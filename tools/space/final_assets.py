#!/usr/bin/env python3
"""Original deterministic pixel art, model preview and synthesized CC0 audio for #32.
Requires Pillow and ffmpeg; run with runtime/space-art-venv/bin/python.
"""

import json
import math
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
R = ROOT / "mods/space/src/main/resources"
A = R / "assets/dolfinspace"


def write(p, obj):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n")


def rgb(hex):
    return tuple(int(hex[i : i + 2], 16) for i in (0, 2, 4))


THEMES = {
    "nave_basica": (73, 202, 202),
    "nave_carga": (239, 165, 69),
    "nave_rapida": (186, 139, 255),
}
for state in (A / "blockstates").glob("*.json"):
    name = state.stem
    if name == "regolito":
        continue
    color = (
        rgb("56636e")
        if name
        not in [
            "compresor",
            "fabricador_circuitos",
            "refineria",
            "compresor_oxigeno",
            "cargador_combustible",
            "mesa_ensamblaje",
        ]
        else rgb("344c61")
    )
    # Keep the already original planet tiles; replace all vanilla industrial placeholders.
    model = A / f"models/block/{name}.json"
    data = json.loads(model.read_text()) if model.exists() else {}
    if any(str(v).startswith("minecraft:block/") for v in data.get("textures", {}).values()):
        im = Image.new("RGB", (16, 16), color)
        d = ImageDraw.Draw(im)
        d.rectangle((0, 0, 15, 15), outline=(125, 153, 170))
        d.rectangle((2, 2, 13, 13), outline=(24, 35, 45))
        if name != "bloque_acero":
            d.rectangle((4, 5, 11, 11), fill=(14, 30, 40))
            d.line((5, 10, 10, 6), fill=THEMES["nave_basica"], width=2)
        im.save(A / f"textures/block/{name}.png")
        write(
            model,
            {
                "parent": "minecraft:block/cube_all",
                "textures": {"all": f"dolfinspace:block/{name}"},
            },
        )
for item in (A / "items").glob("*.json"):
    name = item.stem
    model = A / f"models/item/{name}.json"
    if not model.exists():
        continue
    data = json.loads(model.read_text())
    if "layer0" not in data.get("textures", {}) or not data["textures"]["layer0"].startswith(
        "minecraft:"
    ):
        continue
    color = THEMES.get(name, (185, 205, 215))
    im = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(im)
    if name.startswith("nave_"):
        if name == "nave_carga":
            d.rectangle((3, 13, 28, 24), fill=color)
            d.polygon([(6, 13), (12, 4), (20, 4), (25, 13)], fill=color)
            d.rectangle((4, 22, 9, 28), fill=(255, 100, 30))
            d.rectangle((23, 22, 28, 28), fill=(255, 100, 30))
        else:
            d.polygon(
                [
                    (16, 2),
                    (23, 12),
                    (22, 23),
                    (29, 29),
                    (20, 27),
                    (16, 31),
                    (12, 27),
                    (3, 29),
                    (10, 23),
                    (9, 12),
                ],
                fill=color,
            )
        d.rectangle((12, 12, 19, 17), fill=(19, 45, 68))
    elif name.startswith("placa") or name.startswith("lingote"):
        d.polygon([(3, 12), (22, 5), (29, 16), (11, 26)], fill=color, outline=(60, 90, 110))
        d.line((5, 13, 21, 8), fill=(240, 250, 255), width=2)
    elif name.startswith("circuito"):
        d.rectangle((5, 5, 26, 26), fill=(28, 120, 90))
        d.rectangle((10, 10, 21, 21), fill=(45, 45, 70))
        d.line((3, 16, 29, 16), fill=(244, 200, 80), width=2)
    else:
        d.rounded_rectangle((6, 4, 25, 28), radius=4, fill=color, outline=(50, 80, 100))
        d.rectangle((10, 10, 21, 22), fill=(30, 60, 85))
    im.save(A / f"textures/item/{name}.png")
    write(
        model,
        {"parent": "minecraft:item/generated", "textures": {"layer0": f"dolfinspace:item/{name}"}},
    )
for name, color in THEMES.items():
    im = Image.new("RGB", (128, 128), color)
    d = ImageDraw.Draw(im)
    for y in range(0, 96, 8):
        d.line((0, y, 127, y), fill=tuple(int(c * 0.65) for c in color))
    for x in range(0, 128, 16):
        d.line((x, 0, x, 95), fill=tuple(min(255, int(c * 1.1)) for c in color))
    d.rectangle((0, 96, 127, 127), fill=(20, 40, 68))
    d.rectangle((0, 97, 127, 99), fill=(140, 230, 255))
    im.save(A / f"textures/entity/{name}.png")
im = Image.new("RGB", (32, 32))
p = im.load()
for y in range(32):
    for x in range(32):
        p[x, y] = (255, max(40, 230 - y * 5), max(0, 130 - y * 6))
im.save(A / "textures/entity/ship_flame.png")
sounds = {}
for name, freq, seconds in [
    ("cockpit_hum", 85, 2),
    ("check_beep", 1100, 0.12),
    ("fuel_alarm", 250, 0.6),
    ("engine", 55, 1),
    ("parasite", 420, 0.35),
]:
    out = A / f"sounds/{name}.ogg"
    out.parent.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(dir=ROOT / "runtime") as td:
        wav = Path(td) / "sound.wav"
        rate = 22050
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(rate)
            samples = []
            for i in range(int(seconds * rate)):
                t = i / rate
                fade = min(1, t * 30, (seconds - t) * 30)
                mod = 1 if name == "cockpit_hum" else 0.7 + 0.3 * math.sin(t * 25)
                samples.append(
                    struct.pack(
                        "<h",
                        int(
                            6000
                            * fade
                            * mod
                            * (
                                math.sin(2 * math.pi * freq * t)
                                + 0.2 * math.sin(2 * math.pi * freq * 2 * t)
                            )
                        ),
                    )
                )
            w.writeframes(b"".join(samples))
        subprocess.run(
            [
                "ffmpeg",
                "-v",
                "error",
                "-y",
                "-i",
                str(wav),
                "-c:a",
                "vorbis",
                "-strict",
                "experimental",
                "-ac",
                "2",
                "-q:a",
                "3",
                str(out),
            ],
            check=True,
        )
    sounds[name] = {
        "subtitle": f"space.sound.{name}",
        "sounds": [{"name": f"dolfinspace:{name}", "stream": name == "cockpit_hum"}],
    }
write(A / "sounds.json", sounds)
# Preview uses the actual model cuboids (parsed from the shared Java design specification).
import re

source = (ROOT / "mods/space/src/main/java/com/dolfin/space/ships/ShipDesign.java").read_text()
sections = re.split(r"case (BASICA|CARGA|RAPIDA) ->", source)

# The GUI icon is flat pixel art; held items use the same original hull geometry in 3D.
for tier, name in zip(["BASICA", "CARGA", "RAPIDA"], THEMES):
    seg = sections[sections.index(tier) + 1]
    parts = re.findall(
        r'p\("([^\"]+)",(-?[\d.]+),(-?[\d.]+),(-?[\d.]+),([\d.]+),([\d.]+),([\d.]+)\)', seg
    )
    elements = []
    for part, *coords in parts:
        x, y, z, w, h, depth = map(float, coords)
        elements.append(
            {
                "from": [8 + x * 0.32, y * 0.32, 8 + z * 0.32],
                "to": [8 + (x + w) * 0.32, (y + h) * 0.32, 8 + (z + depth) * 0.32],
                "faces": {
                    f: {"texture": "#hull"}
                    for f in ["north", "south", "east", "west", "up", "down"]
                },
            }
        )
    write(
        A / f"models/item/{name}_hand.json",
        {
            "textures": {"hull": f"dolfinspace:entity/{name}"},
            "elements": elements,
            "display": {
                "firstperson_righthand": {
                    "rotation": [0, 45, 0],
                    "translation": [0, 1, 0],
                    "scale": [0.5, 0.5, 0.5],
                },
                "thirdperson_righthand": {
                    "rotation": [0, 45, 0],
                    "translation": [0, 1, 0],
                    "scale": [0.35, 0.35, 0.35],
                },
            },
        },
    )
    write(
        A / f"items/{name}.json",
        {
            "model": {
                "type": "minecraft:select",
                "property": "minecraft:display_context",
                "cases": [
                    {
                        "when": "gui",
                        "model": {"type": "minecraft:model", "model": f"dolfinspace:item/{name}"},
                    }
                ],
                "fallback": {"type": "minecraft:model", "model": f"dolfinspace:item/{name}_hand"},
            }
        },
    )

W, H = 1500, 1120
sheet = Image.new("RGB", (W, H), (14, 23, 35))
d = ImageDraw.Draw(sheet)
font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf", 20)
big = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 30)
d.text((35, 22), "DOLFIN SPACE / ORIGINAL ASSET CATALOG", font=big, fill=(220, 235, 248))
for n, (tier, name) in enumerate(zip(["BASICA", "CARGA", "RAPIDA"], THEMES)):
    seg = sections[sections.index(tier) + 1]
    parts = re.findall(
        r'p\("([^\"]+)",(-?[\d.]+),(-?[\d.]+),(-?[\d.]+),([\d.]+),([\d.]+),([\d.]+)\)', seg
    )
    ox = 250 + n * 500
    oy = 420
    s = 3.4

    def proj(x, y, z):
        return (ox + (x - z * 0.55) * s, oy - (y - z * 0.25) * s)

    for part, *coords in sorted(parts, key=lambda p: float(p[3]), reverse=True):
        x, y, z, w, h, depth = map(float, coords)
        color = THEMES[name] if "window" not in part else (40, 70, 100)
        for face, shade in [
            ([(x, y, z), (x + w, y, z), (x + w, y + h, z), (x, y + h, z)], 1),
            (
                [
                    (x + w, y, z),
                    (x + w, y, z + depth),
                    (x + w, y + h, z + depth),
                    (x + w, y + h, z),
                ],
                0.65,
            ),
            (
                [
                    (x, y + h, z),
                    (x + w, y + h, z),
                    (x + w, y + h, z + depth),
                    (x, y + h, z + depth),
                ],
                1.15,
            ),
        ]:
            d.polygon(
                [proj(*v) for v in face],
                fill=tuple(min(255, int(c * shade)) for c in color),
                outline=(16, 30, 40),
            )
    d.text((ox - 150, 80), tier, font=big, fill=THEMES[name])
    d.text(
        (ox - 150, 455),
        ["2.5 × 3.75 · 1 pilot", "4.5 × 3 · 2 seats / 27 cargo", "2.5 × 4.5 · 5 s launch"][n],
        font=font,
        fill=(190, 209, 225),
    )
for heading, y, paths, size in [
    ("BLOCKS / MACHINES", 510, list((A / "textures/block").glob("*.png")), 54),
    ("ITEMS / SHIP PARTS", 680, list((A / "textures/item").glob("*.png")), 42),
    ("PLANETARY FAUNA", 970, list((A / "textures/item").glob("*_spawn_egg.png")), 54),
]:
    d.text((30, y), heading, font=big, fill=(170, 205, 220))
    x = 30
    row = y + 42
    for path in paths:
        if heading == "PLANETARY FAUNA" and (
            path.stem.startswith("nave_") or path.stem == "ship_flame"
        ):
            continue
        if x > W - 75:
            x = 30
            row += size + 36
        tile = Image.open(path).convert("RGBA")
        tile = tile.resize((size, size), Image.Resampling.NEAREST)
        sheet.paste(tile, (x, row), tile)
        d.text(
            (x, row + size + 2),
            path.stem[:12],
            font=ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf", 10),
            fill=(180, 200, 210),
        )
        x += 90 if size == 54 else 70
(ROOT / "docs/space").mkdir(parents=True, exist_ok=True)
sheet.save(ROOT / "docs/space/issue-32-assets.png")
