"""Pixel-art painter for the box-UV ship textures declared in ShipDesign.java.

Each ship part is a vanilla box-UV cuboid. The renderer flips the model like vanilla living
entities, so in every region the top texture row is the world top of that face and the first
top/bottom strip is the world-up face.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import NamedTuple

from PIL import Image

Color = tuple[int, int, int]
Rect = tuple[int, int, int, int]

PART_PATTERN = re.compile(
    r'p\(\s*"([^"]+)",\s*(-?\d+),\s*(-?\d+),'
    r"\s*(-?[\d.]+),\s*(-?[\d.]+),\s*(-?[\d.]+),\s*([\d.]+),\s*([\d.]+),\s*([\d.]+)\)"
)
TIERS = ("BASICA", "CARGA", "RAPIDA")
SIDES = ("east", "north", "west", "south")

HULL: Color = (222, 228, 236)
METAL: Color = (78, 84, 96)
DARK: Color = (46, 51, 60)
GLASS: Color = (28, 58, 96)
HAZARD: Color = (240, 196, 48)
HOT: Color = (255, 150, 40)


class Texel(NamedTuple):
    """One texel being painted: its part, face, position inside the face and theme colour."""

    part: Part
    face: str
    col: int
    row: int
    w: int
    h: int
    theme: Color


@dataclass(frozen=True)
class Part:
    """One cuboid of a ship: UV origin plus position/size in sixteenths (Y up)."""

    name: str
    u: int
    v: int
    x: int
    y: int
    z: int
    w: int
    h: int
    d: int

    def faces(self) -> dict[str, Rect]:
        """Box-UV rectangles (x, y, width, height) for each face of this part."""
        u, v, w, h, d = self.u, self.v, self.w, self.h, self.d
        return {
            "up": (u + d, v, w, d),
            "down": (u + d + w, v, w, d),
            "east": (u, v + d, d, h),
            "north": (u + d, v + d, w, h),
            "west": (u + d + w, v + d, d, h),
            "south": (u + 2 * d + w, v + d, w, h),
        }


def parse_designs(source: str) -> dict[str, list[Part]]:
    """Parse every tier's parts from the canonical Java ShipDesign source."""
    sections = re.split(r"case (BASICA|CARGA|RAPIDA) ->", source)
    designs: dict[str, list[Part]] = {}
    for tier in TIERS:
        segment = sections[sections.index(tier) + 1]
        designs[tier] = [
            Part(m[0], *(int(float(n)) for n in m[1:])) for m in PART_PATTERN.findall(segment)
        ]
    return designs


def texture_size(parts: list[Part]) -> tuple[int, int]:
    """Mirror of ShipDesign.textureWidth/Height: power-of-two bounds of every UV region."""
    width = max(p.u + 2 * (p.d + p.w) for p in parts)
    height = max(p.v + p.d + p.h for p in parts)
    return _power_of_two(width), _power_of_two(height)


def write_ship_texture(path: Path, parts: list[Part], theme: Color) -> None:
    """Paint and save one ship's texture atlas."""
    path.parent.mkdir(parents=True, exist_ok=True)
    paint_ship(parts, theme).save(path)


def paint_ship(parts: list[Part], theme: Color) -> Image.Image:
    """Paint all parts of a ship into a transparent RGBA atlas."""
    image = Image.new("RGBA", texture_size(parts), (0, 0, 0, 0))
    for part in parts:
        for face, rect in part.faces().items():
            paint_face(image, part, face, rect, theme)
    return image


def paint_face(image: Image.Image, part: Part, face: str, rect: Rect, theme: Color) -> None:
    """Fill one face rectangle texel by texel using the part's material."""
    x0, y0, w, h = rect
    painter = MATERIALS[material(part.name)]
    pixels = image.load()
    for row in range(h):
        for col in range(w):
            color = painter(Texel(part, face, col, row, w, h, theme))
            edge = col in (0, w - 1) or row in (0, h - 1)
            if edge and face in SIDES and w > 2 and h > 2:
                color = _shade(color, 0.82)
            color = _shade(color, 1 + _noise(x0 + col, y0 + row) * 0.035)
            pixels[x0 + col, y0 + row] = (*color, 255)


def material(name: str) -> str:
    """Material key from a part name prefix."""
    prefixes = (
        ("canopy", "glass"),
        ("hull", "hull"),
        ("bridge", "hull"),
        ("bell_neck", "metal"),
        ("neck", "metal"),
        ("bell", "nozzle"),
        ("skirt", "vent"),
        ("leg", "metal"),
        ("foot", "hazard"),
        ("cargo", "cargo"),
        ("dish", "light"),
        ("antenna", "antenna"),
        ("booster", "booster"),
        ("pod_cap", "theme"),
        ("pod", "panel"),
        ("fin", "fin"),
        ("wing", "fin"),
        ("stabilizer", "fin"),
    )
    return next((m for prefix, m in prefixes if name.startswith(prefix)), "theme")


def _hull(t: Texel) -> Color:
    """Light plating with panel seams, rivets, theme bands and side portholes."""
    part, face, col, row, w, h, theme = t
    if face not in SIDES:
        return _shade(HULL, 1.06 if face == "up" else 0.7)
    world_y = part.y + part.h - 1 - row
    base = _shade(HULL, 1.05 - 0.18 * row / max(1, h - 1))
    if world_y in (part.y + 2, part.y + 3, part.y + part.h - 3, part.y + part.h - 4):
        return _shade(theme, 1.0 - 0.1 * (world_y % 2))
    if face in ("east", "west") and w >= 10 and _porthole(col - w // 2, world_y - part.y - h // 2):
        return _porthole(col - w // 2, world_y - part.y - h // 2)
    if world_y % 8 == 0 or col % 10 == 9:
        return _shade(base, 0.8)
    if world_y % 8 == 1 and col % 10 in (1, 7):
        return _shade(base, 0.7)
    return base


def _porthole(dx: int, dy: int) -> Color | None:
    """Round 5-texel window centred on the face; None outside it."""
    r2 = dx * dx + dy * dy
    if r2 > 8:
        return None
    if r2 > 4:
        return METAL
    return (150, 220, 250) if dx - dy < -1 else GLASS


def _glass(t: Texel) -> Color:
    """Tinted canopy with a frame and a diagonal glare streak."""
    part, face, col, row, w, h, theme = t
    if face not in SIDES:
        return METAL
    if col in (0, w - 1) or row in (0, h - 1):
        return _shade(HULL, 0.75)
    if (col + row) % 9 in (2, 3) and row < h * 0.7:
        return (170, 225, 250)
    return _mix(GLASS, (60, 130, 180), 1 - row / max(1, h - 1))


def _theme(t: Texel) -> Color:
    """Painted theme plating, darker toward the bottom lip, bright top caps."""
    part, face, col, row, w, h, theme = t
    if face == "up":
        return _shade(theme, 1.12)
    if face == "down":
        return _shade(theme, 0.6)
    lip = row == h - 1 and h > 3
    return _shade(theme, 0.75 if lip else 1.08 - 0.22 * row / max(1, h - 1))


def _fin(t: Texel) -> Color:
    """Theme fin with a pale chevron band and a dark trailing edge."""
    part, face, col, row, w, h, theme = t
    base = _theme(t)
    if face in SIDES and (part.y + part.h - 1 - row) in (5, 6):
        return _shade(HULL, 0.98)
    if face in SIDES and row == 0:
        return _shade(theme, 1.25)
    return base


def _panel(t: Texel) -> Color:
    """Theme plating with seams and a light stripe, for engine pods."""
    part, face, col, row, w, h, theme = t
    base = _theme(t)
    if face in SIDES and (row % 6 == 5 or col % 8 == 7):
        return _shade(base, 0.8)
    if face in SIDES and row in (2, 3):
        return _shade(HULL, 0.95)
    return base


def _booster(t: Texel) -> Color:
    """Dark theme booster with a hot exhaust rim at the bottom."""
    part, face, col, row, w, h, theme = t
    if face in SIDES and row >= h - 2:
        return _mix(DARK, HOT, 0.4)
    return _shade(_theme(t), 0.7)


def _metal(t: Texel) -> Color:
    """Brushed dark metal with vertical grain."""
    part, face, col, row, w, h, theme = t
    return _shade(METAL, 1.0 + (0.08 if col % 3 == 0 else 0) - 0.15 * row / max(1, h))


def _light(t: Texel) -> Color:
    """Pale metal for dishes and sensors."""
    part, face, col, row, w, h, theme = t
    return _shade(HULL, 0.9 if face in SIDES else 1.0)


def _nozzle(t: Texel) -> Color:
    """Engine bell: heat-tinted skirt, glowing ring and black throat underneath."""
    part, face, col, row, w, h, theme = t
    if face == "down":
        r = max(abs(col - (w - 1) / 2), abs(row - (h - 1) / 2))
        limit = min(w, h) / 2
        if r > limit - 1.5:
            return _shade(METAL, 0.8)
        return _mix(HOT, (255, 230, 150), 0.5) if r > limit - 3 else (20, 16, 18)
    heat = row / max(1, h - 1)
    return _mix(_shade(METAL, 0.85), (150, 70, 40), heat * 0.6)


def _vent(t: Texel) -> Color:
    """Engine housing with cooling slits."""
    part, face, col, row, w, h, theme = t
    if face in SIDES and 0 < row < h - 1 and col % 3 == 1:
        return (24, 26, 30)
    return _shade(DARK, 1.1 if face == "up" else 1.0)


def _hazard(t: Texel) -> Color:
    """Yellow/black diagonal stripes for feet and pads."""
    part, face, col, row, w, h, theme = t
    return HAZARD if (col + row) % 4 < 2 else (34, 34, 38)


def _cargo(t: Texel) -> Color:
    """Cargo bay door: grey plating, hazard frame and a central seam."""
    part, face, col, row, w, h, theme = t
    if face not in SIDES:
        return _shade(METAL, 1.2)
    if row < 2 or row >= h - 2:
        return _hazard(t)
    if col == w // 2 or row % 4 == 0:
        return _shade(METAL, 0.9)
    return _shade(METAL, 1.35)


def _antenna(t: Texel) -> Color:
    """Pale mast with a red beacon on top."""
    part, face, col, row, w, h, theme = t
    return (230, 60, 60) if face == "up" or row == 0 else _shade(HULL, 0.85)


MATERIALS = {
    "hull": _hull,
    "glass": _glass,
    "theme": _theme,
    "panel": _panel,
    "fin": _fin,
    "booster": _booster,
    "metal": _metal,
    "light": _light,
    "nozzle": _nozzle,
    "vent": _vent,
    "hazard": _hazard,
    "cargo": _cargo,
    "antenna": _antenna,
}


def write_flame_texture(path: Path) -> None:
    """Flame atlas (32x32): white-blue core fading into orange and transparent tips."""
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    pixels = image.load()
    for y in range(32):
        heat = y / 31
        for x in range(32):
            flicker = _noise(x, y) * 0.12
            core = _mix((210, 235, 255), (255, 190, 70), min(1, heat * 1.6))
            color = _mix(core, (255, 70, 20), max(0, heat - 0.5) * 2)
            alpha = int(255 * max(0.0, min(1.0, 1.15 - heat * 1.05 + flicker)))
            pixels[x, y] = (*color, alpha)
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def _power_of_two(n: int) -> int:
    size = 1
    while size < n:
        size *= 2
    return size


def _noise(x: int, y: int) -> float:
    """Deterministic texel noise in [-1, 1]."""
    h = (x * 374761393 + y * 668265263) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h >> 8) & 0xFF) / 127.5 - 1


def _shade(color: Color, factor: float) -> Color:
    return tuple(max(0, min(255, int(c * factor))) for c in color)  # type: ignore[return-value]


def _mix(a: Color, b: Color, t: float) -> Color:
    t = max(0.0, min(1.0, t))
    return tuple(int(x + (y - x) * t) for x, y in zip(a, b, strict=True))  # type: ignore[return-value]
