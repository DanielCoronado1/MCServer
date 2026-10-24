"""Renders review sheets of the ShipDesign models with their box-UV textures.

Usage: python tools/space/render_ship_previews.py OUT.png [--before REV] [--icons] [--viewer HTML]

With --before, every ship is drawn twice: the design and textures at GIT_REV, then the working
tree. A 1.8-block player silhouette next to each ship shows the real scale. --icons also renders
the 32x32 inventory icons from the current models; --viewer writes a three.js page
(ship_viewer.html) to orbit every ship in 3D.
"""

from __future__ import annotations

import argparse
import base64
import io
import json
import math
import re
import subprocess
from pathlib import Path

import ship_art
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
DESIGN = "mods/space/src/main/java/com/dolfin/space/ships/ShipDesign.java"
ENTITY = "mods/space/src/main/java/com/dolfin/space/ships/SpaceshipEntity.java"
TEXTURES = "mods/space/src/main/resources/assets/dolfinspace/textures/entity/{}.png"
NAMES = dict(zip(ship_art.TIERS, ("nave_basica", "nave_carga", "nave_rapida"), strict=True))
VIEWS = ((150, 18, "frente 3/4"), (330, 18, "espalda 3/4"), (90, 8, "lado"))
BACKGROUND = (24, 24, 30, 255)
PLAYER = (90, 96, 110)
OUTLINE = (30, 22, 18, 255)
ICONS = "mods/space/src/main/resources/assets/dolfinspace/textures/item/{}.png"
NOZZLE_PATTERN = r"new Nozzle\((-?[\d.]+)f?, (-?[\d.]+)f?, ([\d.]+)f?\)"


def faces(part: ship_art.Part):
    """(uv rect, outward normal, texel -> world point) for each face, columns left to right."""
    x0, y0, z0 = part.x, part.y, part.z
    x1, y1, z1 = x0 + part.w, y0 + part.h, z0 + part.d
    w, h, d = part.w, part.h, part.d
    rects = part.faces()
    return [
        (rects["north"], (0, 0, -1), lambda s, t: (x1 - s * w, y1 - t * h, z0)),
        (rects["south"], (0, 0, 1), lambda s, t: (x0 + s * w, y1 - t * h, z1)),
        (rects["east"], (1, 0, 0), lambda s, t: (x1, y1 - t * h, z1 - s * d)),
        (rects["west"], (-1, 0, 0), lambda s, t: (x0, y1 - t * h, z0 + s * d)),
        (rects["up"], (0, 1, 0), lambda s, t: (x0 + s * w, y1, z0 + t * d)),
        (rects["down"], (0, -1, 0), lambda s, t: (x0 + s * w, y0, z1 - t * d)),
    ]


def rotate(p, yaw: float, pitch: float):
    x, y, z = p
    a, b = math.radians(yaw), math.radians(pitch)
    x, z = x * math.cos(a) + z * math.sin(a), -x * math.sin(a) + z * math.cos(a)
    y, z = y * math.cos(b) - z * math.sin(b), y * math.sin(b) + z * math.cos(b)
    return x, y, z


def render(
    parts, texture: Image.Image, yaw: float, pitch: float, scale: float, player: bool = True
) -> Image.Image:
    """Orthographic render, camera on +Z after rotation; a player silhouette stands beside it."""
    polys = []
    for part in parts:
        for (u, v, w, h), normal, point in faces(part):
            n = rotate(normal, yaw, pitch)
            if n[2] <= 1e-6:
                continue
            light = 0.55 + 0.45 * max(0, n[2] * 0.6 + n[1] * 0.5 + n[0] * 0.25)
            for j in range(h):
                for i in range(w):
                    c = texture.getpixel((u + i, v + j))
                    if c[3] == 0:
                        continue
                    corners = ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1))
                    pts = [rotate(point(a / w, b / h), yaw, pitch) for a, b in corners]
                    shade = tuple(min(255, int(k * light)) for k in c[:3])
                    polys.append((sum(p[2] for p in pts) / 4, pts, shade))
    polys.sort(key=lambda p: p[0])
    size = int(80 * scale)
    image = Image.new("RGBA", (size + int(30 * scale), size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    cx, floor = size / 2, size - 8
    for _, pts, color in polys:
        draw.polygon(
            [(cx + p[0] * scale, floor - p[1] * scale) for p in pts], fill=color, outline=color
        )
    if player:
        px, top = size + 6 * scale, floor - 28.8 * scale
        draw.rectangle((px, top, px + 6 * scale, floor), fill=PLAYER)
        draw.rectangle((px - scale, top, px + 7 * scale, floor - 21 * scale), fill=PLAYER)
    return image


def icon(parts, texture: Image.Image) -> Image.Image:
    """32x32 inventory icon: a front 3/4 render squared, downsampled and hard-edged."""
    big = render(parts, texture, 150, 20, 6, player=False)
    big = big.crop(big.getbbox())
    side = max(big.size)
    square = Image.new("RGBA", (side, side))
    square.alpha_composite(big, ((side - big.size[0]) // 2, side - big.size[1]))
    small = Image.new("RGBA", (32, 32))
    small.alpha_composite(square.resize((30, 30), Image.LANCZOS), (1, 1))
    small.putalpha(small.getchannel("A").point(lambda a: 255 if a > 110 else 0))
    # A dark 1-texel outline keeps the silhouette readable on any inventory slot.
    solid = small.getchannel("A")
    outlined = small.copy()
    for y in range(32):
        for x in range(32):
            if solid.getpixel((x, y)) == 0 and any(
                0 <= x + dx < 32 and 0 <= y + dy < 32 and solid.getpixel((x + dx, y + dy))
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
            ):
                outlined.putpixel((x, y), OUTLINE)
    return outlined


def read(rev: str | None, path: str) -> bytes:
    """A repository file from the working tree, or from a git revision."""
    if rev is None:
        return (ROOT / path).read_bytes()
    return subprocess.run(
        ["git", "show", f"{rev}:{path}"], cwd=ROOT, capture_output=True, check=True
    ).stdout


def load(rev: str | None):
    """Designs and textures from the working tree, or from a git revision."""
    designs = ship_art.parse_designs(read(rev, DESIGN).decode())
    textures = {
        tier: Image.open(io.BytesIO(read(rev, TEXTURES.format(name)))).convert("RGBA")
        for tier, name in NAMES.items()
    }
    return designs, textures


def viewer(out: Path, before: str | None) -> None:
    """Interactive three.js viewer with every ship (and the GIT_REV versions, if given)."""
    data = {}
    for label, rev in ([("antes", before)] if before else []) + [("ahora", None)]:
        source = read(rev, DESIGN).decode()
        sections = re.split(r"case (BASICA|CARGA|RAPIDA) ->", source)
        designs, textures = load(rev)
        # Older entities seated every pilot 0.45 blocks to the side; seat() centres single seats.
        centred = "public Vec3 seat(" in read(rev, ENTITY).decode()
        data[label] = {}
        for tier, name in NAMES.items():
            section = sections[sections.index(tier) + 1]
            png = io.BytesIO()
            textures[tier].save(png, "PNG")
            seats = [-0.45, 0.45] if tier == "CARGA" else [0.0 if centred else -0.45]
            data[label][tier] = {
                "nombre": name.replace("_", " ").capitalize(),
                "asientos": seats,
                "ancho": float(re.search(r"new ShipDesign\(\s*([\d.]+)f", section)[1]),
                "tex": "data:image/png;base64," + base64.b64encode(png.getvalue()).decode(),
                "tw": textures[tier].width,
                "th": textures[tier].height,
                "partes": [[p.x, p.y, p.z, p.w, p.h, p.d, p.u, p.v] for p in designs[tier]],
                "toberas": [[float(n) for n in m] for m in re.findall(NOZZLE_PATTERN, section)],
            }
    template = (Path(__file__).parent / "ship_viewer.html").read_text(encoding="utf-8")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(template.replace("__DATOS__", json.dumps(data)).encode())


def sheet(out: Path, before: str | None) -> None:
    versions = ([("antes", *load(before))] if before else []) + [("ahora", *load(None))]
    scale = 3.2
    title, label = ImageFont.load_default(size=26), ImageFont.load_default(size=16)
    cell = render([], Image.new("RGBA", (1, 1)), 0, 0, scale).size
    rows = len(ship_art.TIERS) * len(versions)
    image = Image.new(
        "RGBA", (40 + len(VIEWS) * (cell[0] + 20), 70 + rows * (cell[1] + 40)), BACKGROUND
    )
    draw = ImageDraw.Draw(image)
    heading = "Naves DolfinSpace - figura gris = jugador (1,8 bloques)"
    draw.text((40, 20), heading, fill=(235, 235, 235), font=title)
    y = 70
    for tier in ship_art.TIERS:
        for name, designs, textures in versions:
            draw.text((40, y), f"{NAMES[tier]} - {name}", fill=(200, 200, 210), font=label)
            for n, (yaw, pitch, view) in enumerate(VIEWS):
                x = 40 + n * (cell[0] + 20)
                view_image = render(designs[tier], textures[tier], yaw, pitch, scale)
                image.alpha_composite(view_image, (x, y + 20))
                draw.text((x, y + 20 + cell[1] - 18), view, fill=(150, 150, 160), font=label)
            y += cell[1] + 40
    out.parent.mkdir(parents=True, exist_ok=True)
    image.save(out)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("out", type=Path)
    parser.add_argument("--before")
    parser.add_argument("--icons", action="store_true", help="also rewrite the 32x32 item icons")
    parser.add_argument("--viewer", type=Path, help="also write an interactive 3D viewer page")
    args = parser.parse_args()
    sheet(args.out, args.before)
    if args.viewer:
        viewer(args.viewer, args.before)
    if args.icons:
        designs, textures = load(None)
        for tier, name in NAMES.items():
            icon(designs[tier], textures[tier]).save(ROOT / ICONS.format(name))
