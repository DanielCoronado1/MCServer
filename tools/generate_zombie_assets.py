#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow==12.1.0", "soundfile==0.13.1"]
# ///
"""Deterministic, original 64x64 zombie art (see zombie_textures.py) and mono Vorbis cues.
Run: uv run tools/generate_zombie_assets.py (Pillow and libsndfile are installed by uv).
No downloaded textures, audio, or Minecraft assets. Preview is documentation only.
"""

import json
import math
import random
import struct
from pathlib import Path

import soundfile as sf
from PIL import Image, ImageDraw
from zombie_textures import STYLES, paint_egg, paint_props, paint_skin

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "mods/zombies/src/main/resources/assets/dolfinzombies"
SOUNDS = {
    "griton_scream": (1.25, "Gritón screams", "El Gritón grita"),
    "bilioso_burst": (0.65, "Bilious zombie bursts", "El Bilioso revienta"),
    "abismo_grab": (0.8, "Abyss drowned grabs", "El Ahogado agarra"),
    "ladron_steal": (0.45, "Thief snatches", "El Ladrón roba"),
}


def audio(name, seconds, seed, output):
    rng = random.Random(seed)
    rate = 22050
    phase = 0.0
    samples = []
    last = 0.0
    for i in range(int(seconds * rate)):
        t = i / rate
        u = t / seconds
        noise = rng.uniform(-1, 1)
        env = min(1, u * 30) * max(0, 1 - u) ** 1.6
        if name == "griton_scream":
            freq = 390 + 130 * math.sin(u * math.pi) + 24 * math.sin(2 * math.pi * 13 * t)
            phase += 2 * math.pi * freq / rate
            value = (
                (
                    math.sin(phase)
                    + 0.4 * math.sin(phase * 2)
                    + 0.2 * math.sin(phase * 3)
                    + 0.3 * noise
                )
                * env
                * 0.42
            )
        elif name == "bilioso_burst":
            last = 0.74 * last + 0.26 * noise
            value = (last * 2 + math.sin(2 * math.pi * (100 * t - 45 * t * t)) * 0.45) * env
        elif name == "abismo_grab":
            last = 0.85 * last + 0.15 * noise
            value = (
                last * 2
                + math.sin(2 * math.pi * 125 * t + 4 * math.sin(2 * math.pi * 7 * t)) * 0.35
            ) * env
        else:
            value = (
                (noise * 0.3 + math.sin(2 * math.pi * (900 * t - 600 * t * t)) * 0.35)
                * env
                * (0.5 + 0.5 * math.sin(2 * math.pi * 17 * t) ** 2)
            )
        samples.append(int(max(-0.95, min(0.95, value)) * 32767))
    sf.write(str(output), [v / 32768 for v in samples], rate, format="OGG", subtype="VORBIS")
    # libogg chooses a random stream serial. Canonicalize each page and recalculate
    # the Ogg CRC so rebuilding unchanged synthesis produces identical bytes.
    data = bytearray(output.read_bytes())
    offset = 0
    while offset < len(data):
        assert data[offset : offset + 4] == b"OggS"
        segments = data[offset + 26]
        size = 27 + segments + sum(data[offset + 27 : offset + 27 + segments])
        data[offset + 14 : offset + 18] = struct.pack("<I", seed)
        data[offset + 22 : offset + 26] = b"\0" * 4
        crc = 0
        for byte in data[offset : offset + size]:
            crc ^= byte << 24
            for _ in range(8):
                crc = ((crc << 1) ^ 0x04C11DB7 if crc & 0x80000000 else crc << 1) & 0xFFFFFFFF
        data[offset + 22 : offset + 26] = struct.pack("<I", crc)
        offset += size
    output.write_bytes(data)


def main():
    textures = ASSETS / "textures/entity"
    textures.mkdir(parents=True, exist_ok=True)
    for i, (name, style) in enumerate(STYLES.items()):
        write_kind_textures(textures, name, style, 600 + i)
        write_spawn_egg(name, style, 600 + i)
    write_disguise(textures)
    write_preview()
    write_sounds()
    print(
        "Generated 16 skins, 16 accessory atlases, 6 glow atlases, 16 eggs, 4 mono cues, "
        "4 locales and preview."
    )


def write_kind_textures(textures, name, style, seed):
    """Skin, prop atlas and the emissive layers the renderer requests for this kind."""
    skin, glow = paint_skin(name, style, seed)
    props, props_glow = paint_props(name, seed)
    skin.save(textures / f"{name}_zombie.png")
    props.save(textures / f"{name}_zombie_accessories.png")
    if style.glow:
        glow.save(textures / f"{name}_zombie_eyes.png")
    if name in ("minero", "enano"):
        props_glow.save(textures / f"{name}_zombie_accessories_emissive.png")


def write_spawn_egg(name, style, seed):
    """26.3 removed the template_spawn_egg tint model: ship an original colored sprite."""
    eggid = f"{name}_zombie_spawn_egg"
    for folder in ("models/item", "textures/item", "items"):
        (ASSETS / folder).mkdir(parents=True, exist_ok=True)
    paint_egg(style, seed).save(ASSETS / f"textures/item/{eggid}.png")
    model = {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"dolfinzombies:item/{eggid}"},
    }
    (ASSETS / f"models/item/{eggid}.json").write_text(json.dumps(model, indent=2) + "\n")
    item = {"model": {"type": "minecraft:model", "model": f"dolfinzombies:item/{eggid}"}}
    (ASSETS / f"items/{eggid}.json").write_text(json.dumps(item, indent=2) + "\n")


def write_disguise(textures):
    """Mimo overlay drawn over copied player skins: hollow eyes and a thin smile."""
    disguise = Image.new("RGBA", (64, 64))
    dd = ImageDraw.Draw(disguise)
    for x in (9, 13):
        dd.rectangle((x, 11, x + 1, 12), fill=(12, 12, 19, 255))
    dd.line((10, 14, 13, 14, 14, 13), fill=(24, 16, 25, 255))
    disguise.save(textures / "mimo_zombie_disguise.png")


def write_preview():
    """Documentation sheet: front and back of every skin, 4x nearest-neighbour."""
    textures = ASSETS / "textures/entity"
    preview = Image.new("RGBA", (8 * 160, 2 * 330), (25, 29, 37, 255))
    pd = ImageDraw.Draw(preview)
    for i, name in enumerate(STYLES):
        skin = Image.open(textures / f"{name}_zombie.png")
        x, y = (i % 8) * 160, (i // 8) * 330
        for j, side in enumerate(("front", "back")):
            view = flat_view(skin, side).resize((64, 128), Image.Resampling.NEAREST)
            preview.alpha_composite(view, (x + 12 + j * 72, y + 40))
        pd.text((x + 8, y + 12), name, fill="white")
        pd.text((x + 8, y + 294), "UV frente / espalda", fill="#a5b4c9")
    (ROOT / "docs/zombies").mkdir(parents=True, exist_ok=True)
    preview.save(ROOT / "docs/zombies/issue-08-skins.png")


def flat_view(skin, side):
    """Unfolded 16x32 front or back view assembled from humanoid UV faces."""
    view = Image.new("RGBA", (16, 32))
    parts = {
        "front": [
            ((8, 8), (4, 0), 8, 8),
            ((20, 20), (4, 8), 8, 12),
            ((44, 20), (0, 8), 4, 12),
            ((36, 52), (12, 8), 4, 12),
            ((4, 20), (4, 20), 4, 12),
            ((20, 52), (8, 20), 4, 12),
        ],
        "back": [
            ((24, 8), (4, 0), 8, 8),
            ((32, 20), (4, 8), 8, 12),
            ((44, 52), (0, 8), 4, 12),
            ((52, 20), (12, 8), 4, 12),
            ((28, 52), (4, 20), 4, 12),
            ((12, 20), (8, 20), 4, 12),
        ],
    }
    for (u, v), dest, w, h in parts[side]:
        view.paste(skin.crop((u, v, u + w, v + h)), dest)
    return view


def write_sounds():
    sounddir = ASSETS / "sounds"
    sounddir.mkdir(exist_ok=True)
    defs = {}
    for i, (name, (duration, _, _)) in enumerate(SOUNDS.items()):
        audio(name, duration, 800 + i, sounddir / f"{name}.ogg")
        defs[name] = {
            "subtitle": f"subtitles.dolfinzombies.{name}",
            "sounds": [
                {
                    "name": f"dolfinzombies:{name}",
                    "attenuation_distance": 32 if name == "griton_scream" else 16,
                }
            ],
        }
    (ASSETS / "sounds.json").write_text(json.dumps(defs, indent=2) + "\n")
    for locale in ("en_us", "es_mx", "es_es", "es_ar"):
        path = ASSETS / f"lang/{locale}.json"
        lang = json.loads(path.read_text())
        for name, (_, en, es) in SOUNDS.items():
            lang[f"subtitles.dolfinzombies.{name}"] = en if locale == "en_us" else es
        path.write_text(json.dumps(lang, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
