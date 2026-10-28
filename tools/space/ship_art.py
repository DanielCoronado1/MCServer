"""Steampunk pixel-art painter for the box-UV ship textures declared in ShipDesign.java.

Each ship part is a vanilla box-UV cuboid. The renderer flips the model like vanilla living
entities, so in every region the top texture row is the world top of that face and the first
top/bottom strip is the world-up face.
"""

from __future__ import annotations

import math
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

BRASS: Color = (196, 150, 62)
COPPER: Color = (176, 98, 52)
IRON: Color = (66, 62, 62)
SOOT: Color = (24, 20, 20)
WOOD: Color = (120, 82, 48)
GLASS: Color = (40, 92, 100)
GLARE: Color = (180, 232, 222)
LEATHER: Color = (96, 58, 40)
DIAL: Color = (232, 220, 190)
NEEDLE: Color = (180, 30, 30)
EMBER: Color = (255, 140, 40)
HOT: Color = (255, 214, 110)


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
            if color is None:
                continue
            edge = col in (0, w - 1) or row in (0, h - 1)
            if edge and face in SIDES and w > 2 and h > 2:
                color = _shade(color, 0.82)
            color = _shade(color, 1 + _noise(x0 + col, y0 + row) * 0.035)
            pixels[x0 + col, y0 + row] = (*color, 255)


def material(name: str) -> str:
    """Material key from a part name prefix."""
    prefixes = (
        ("canopy", "porthole"),
        ("hull", "hull"),
        ("pod_cap", "cap"),
        ("pod", "boiler"),
        ("band", "band"),
        ("bridge", "wood"),
        ("cargo", "cargo"),
        ("roof", "iron"),
        ("bell_neck", "neck"),
        ("neck", "neck"),
        ("bell", "nozzle"),
        ("firebox", "firebox"),
        ("gauge", "gauge"),
        ("gear", "gear"),
        ("pipe", "pipe"),
        ("dome", "dome"),
        ("stack_cap", "cap"),
        ("booster_cap", "cap"),
        ("stack", "stack"),
        ("vent", "stack"),
        ("booster", "boiler"),
        ("whistle", "brass"),
        ("spike", "brass"),
        ("leg", "iron"),
        ("foot", "foot"),
        ("fin", "fin"),
        ("wing", "wing"),
    )
    return next((m for prefix, m in prefixes if name.startswith(prefix)), "hull")


def _accent(theme: Color) -> Color:
    """Trim metal that contrasts with the hull: copper on brass ships, brass on the rest."""
    brassy = 0.7 <= theme[1] / max(1, theme[0]) <= 0.9
    return COPPER if brassy else BRASS


def _hull(t: Texel) -> Color:
    """Riveted boiler plating: plate seams, rivet rows along them and side portholes."""
    part, face, col, row, w, h, theme = t
    if face not in SIDES:
        return _shade(theme, 0.95 if face == "up" else 0.6)
    world_y = part.y + part.h - 1 - row
    base = _shade(theme, 1.08 - 0.25 * row / max(1, h - 1))
    if face in ("east", "west") and w >= 10:
        window = _porthole(col - w // 2, world_y - part.y - h // 2, theme)
        if window:
            return window
    if world_y % 8 == 0 or col % 8 == 7:
        return _shade(base, 0.72)
    if world_y % 8 == 1 and col % 4 == 1:
        return _shade(base, 1.3)
    return base


def _porthole(dx: int, dy: int, theme: Color) -> Color | None:
    """Round 7-texel window with a bolted ring; None outside it."""
    r2 = dx * dx + dy * dy
    if r2 > 10:
        return None
    if r2 > 5:
        return _shade(_accent(theme), 0.75 if (dx + dy) % 2 else 1.1)
    return GLARE if dx - dy < -1 else GLASS


def _porthole_window(t: Texel) -> Color:
    """Canopy porthole: thick brass frame with cut corners, cross bars and greenish glass."""
    part, face, col, row, w, h, theme = t
    if face not in SIDES:
        return _shade(BRASS, 0.8)
    corner = min(col, w - 1 - col) + min(row, h - 1 - row) < 2
    if corner:
        return _shade(BRASS, 0.55)
    if col in (0, w - 1) or row in (0, h - 1):
        return _shade(BRASS, 1.05 if (col + row) % 3 else 1.3)
    if face in ("north", "south") and col == w // 2:
        return _shade(BRASS, 0.8)
    if (col + row) % 7 in (2, 3) and row < h * 0.6:
        return GLARE
    return _mix(GLASS, (70, 120, 100), 1 - row / max(1, h - 1))


def _band(t: Texel) -> Color:
    """Riveted reinforcement ring around the hull."""
    part, face, col, row, w, h, theme = t
    trim = _accent(theme)
    if face not in SIDES:
        return _shade(trim, 0.9 if face == "up" else 0.55)
    if col % 3 == 1:
        return _shade(trim, 1.35)
    return _shade(trim, 1.0 - 0.15 * row)


def _dome(t: Texel) -> Color:
    """Stepped trim-metal nose dome with a dark rivet lip on every tier."""
    part, face, col, row, w, h, theme = t
    trim = _accent(theme)
    if face == "down":
        return _shade(trim, 0.55)
    if face == "up":
        return _shade(trim, 1.15)
    if row == h - 1:
        return _shade(IRON, 1.4 if col % 3 == 1 else 0.9)
    return _shade(trim, 1.15 - 0.25 * row / max(1, h - 1))


def _brass(t: Texel) -> Color:
    """Polished brass fittings: whistles and spikes."""
    part, face, col, row, w, h, theme = t
    if face == "up":
        return _shade(BRASS, 1.3)
    return _shade(BRASS, 1.2 - 0.3 * row / max(1, h - 1) + (0.15 if col == 0 else 0))


def _cap(t: Texel) -> Color:
    """Brass caps; stack caps show a sooty chimney mouth on top."""
    part, face, col, row, w, h, theme = t
    if face == "up" and part.name.startswith("stack") and 0 < col < w - 1 and 0 < row < h - 1:
        return SOOT
    if face in SIDES and col % 2 == 1 and row == 0:
        return _shade(BRASS, 1.35)
    return _shade(BRASS, 1.05 if face != "down" else 0.6)


def _stack(t: Texel) -> Color:
    """Dark iron smokestack, sooty toward the mouth, with a brass collar."""
    part, face, col, row, w, h, theme = t
    if face == "up":
        return SOOT
    if face in SIDES and row == h - 2:
        return _shade(BRASS, 1.0)
    soot = 1 - row / max(1, h - 1)
    return _mix(_shade(IRON, 1.1 if col == 0 else 1.0), SOOT, soot * 0.6)


def _pipe(t: Texel) -> Color:
    """Copper steam pipe with brass couplings and a bright highlight edge."""
    part, face, col, row, w, h, theme = t
    if face in SIDES and row % 6 in (0, 1):
        return _shade(BRASS, 1.1 if row % 6 == 0 else 0.85)
    return _shade(COPPER, 1.3 if col == 0 else 1.0)


def _gauge(t: Texel) -> Color:
    """Pressure gauge: brass rim, cream dial and a red needle."""
    part, face, col, row, w, h, theme = t
    if face not in ("north", "south") or w < 3:
        return _shade(BRASS, 0.9)
    if col in (0, w - 1) or row in (0, h - 1):
        return _shade(BRASS, 1.2 if (col + row) % 2 else 0.85)
    return NEEDLE if col == row else DIAL


def _gear(t: Texel) -> Color | None:
    """Cog on the large faces: brass teeth around an iron web and hub; cut out between teeth."""
    part, face, col, row, w, h, theme = t
    if w < 6 or h < 6:
        return _shade(BRASS, 1.1 if (col + row) % 2 else 0.8)
    cx, cy = (w - 1) / 2, (h - 1) / 2
    dx, dy = col - cx, row - cy
    r = (dx * dx + dy * dy) ** 0.5
    outer = min(w, h) / 2
    if r <= 1.2:
        return SOOT
    if r <= 2.2:
        return _shade(BRASS, 1.25)
    if r <= outer - 1.4:
        return _shade(IRON, 1.6 if (col + row) % 3 == 0 else 1.4)
    angle = (math.atan2(dy, dx) / (2 * math.pi) * 8) % 1
    if r <= outer - 0.6 or (r <= outer + 0.3 and angle < 0.5):
        return _shade(BRASS, 1.0 if angle < 0.5 else 0.8)
    return None


def _fin(t: Texel) -> Color:
    """Theme fin plate framed in rivetted trim."""
    part, face, col, row, w, h, theme = t
    if face not in SIDES:
        return _shade(_accent(theme), 0.9)
    if col in (0, w - 1) or row in (0, h - 1):
        return _shade(_accent(theme), 1.25 if (col + row) % 2 else 0.95)
    return _shade(theme, 0.95 - 0.2 * row / max(1, h - 1))


def _wing(t: Texel) -> Color:
    """Ornithopter wing: leather stretched over a brass spar and sparse ribs."""
    part, face, col, row, w, h, theme = t
    if face not in SIDES:
        return _shade(BRASS, 0.9)
    if row == 0 or col == 0 or (row % 6 == 0 and w > 2):
        return _shade(BRASS, 1.15)
    return _shade(LEATHER, 1.1 - 0.25 * row / max(1, h - 1) + (0.08 if col % 2 else 0))


def _boiler(t: Texel) -> Color:
    """Copper boiler drum: riveted rings and an ember glow at the burner end."""
    part, face, col, row, w, h, theme = t
    if face not in SIDES:
        return _shade(COPPER, 0.95 if face == "up" else 0.55)
    if part.name.startswith("booster") and row >= h - 2:
        return _mix(SOOT, EMBER, 0.6)
    if row % 5 == 2:
        return _shade(BRASS, 1.25 if col % 3 == 1 else 0.95)
    return _shade(COPPER, 1.15 - 0.25 * row / max(1, h - 1) + (0.1 if col % 7 == 0 else 0))


def _firebox(t: Texel) -> Color:
    """Iron firebox under the hull with glowing grate slits."""
    part, face, col, row, w, h, theme = t
    if face in SIDES and 0 < row < h - 1 and col % 3 == 1:
        return _mix(EMBER, HOT, row / max(1, h - 1))
    return _shade(IRON, 1.1 if face == "up" else 0.85)


def _wood(t: Texel) -> Color:
    """Wheelhouse planking with iron straps and brass corner posts."""
    part, face, col, row, w, h, theme = t
    if face not in SIDES:
        return _shade(IRON, 0.9)
    if col in (0, w - 1):
        return _shade(BRASS, 1.1)
    if row in (0, h - 1):
        return _shade(IRON, 0.8)
    grain = 0.92 if row % 2 else 1.04
    return _shade(WOOD, grain - (0.12 if col % 7 == 3 else 0))


def _cargo(t: Texel) -> Color:
    """Cargo hatch: wooden planks, iron straps and a brass lock plate."""
    part, face, col, row, w, h, theme = t
    if face not in SIDES:
        return _shade(IRON, 0.9)
    if row in (2, h - 3) or col in (0, w - 1):
        return _shade(IRON, 1.05 if col % 4 == 1 else 0.8)
    if abs(col - w // 2) <= 1 and abs(row - h // 2) <= 1:
        return _shade(BRASS, 1.2)
    return _shade(WOOD, 0.88 if col % 4 == 0 else 1.0)


def _iron(t: Texel) -> Color:
    """Wrought iron with bolt heads along the rim, for legs and roofs."""
    part, face, col, row, w, h, theme = t
    if (col in (1, w - 2) or row in (1, h - 2)) and (col + row) % 4 == 0 and w > 3:
        return _shade(BRASS, 0.9)
    return _shade(IRON, 1.1 - 0.2 * row / max(1, h))


def _foot(t: Texel) -> Color:
    """Iron claw foot with a brass toe cap."""
    part, face, col, row, w, h, theme = t
    if face == "up":
        return _shade(BRASS, 1.0)
    return _shade(IRON, 0.75 if col % 2 else 0.95)


def _neck(t: Texel) -> Color:
    """Copper throat between boiler and nozzle, rings of brass."""
    part, face, col, row, w, h, theme = t
    if face in SIDES and row == 0:
        return _shade(BRASS, 1.1)
    return _shade(COPPER, 0.9 - 0.1 * row / max(1, h))


def _nozzle(t: Texel) -> Color:
    """Flared copper bell: heat-blued skirt, glowing ring and a black throat underneath."""
    part, face, col, row, w, h, theme = t
    if face == "down":
        r = max(abs(col - (w - 1) / 2), abs(row - (h - 1) / 2))
        limit = min(w, h) / 2
        if r > limit - 1.5:
            return _shade(COPPER, 0.7)
        return _mix(EMBER, HOT, 0.5) if r > limit - 3 else SOOT
    heat = row / max(1, h - 1)
    return _mix(_shade(COPPER, 1.1), (70, 60, 110), heat * 0.55)


MATERIALS = {
    "hull": _hull,
    "porthole": _porthole_window,
    "band": _band,
    "dome": _dome,
    "brass": _brass,
    "cap": _cap,
    "stack": _stack,
    "pipe": _pipe,
    "gauge": _gauge,
    "gear": _gear,
    "fin": _fin,
    "wing": _wing,
    "boiler": _boiler,
    "firebox": _firebox,
    "wood": _wood,
    "cargo": _cargo,
    "iron": _iron,
    "foot": _foot,
    "neck": _neck,
    "nozzle": _nozzle,
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
