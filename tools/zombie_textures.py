"""Original pixel-art painter for Dolfin Zombies skins, prop atlases and spawn eggs.

Every surface is painted per cuboid face with five-tone hue-shifted ramps lit from the
top-left, so adjacent faces share rows and UV seams line up. Only Pillow is required.
"""

from __future__ import annotations

import colorsys
from collections.abc import Callable
from dataclasses import dataclass
from functools import cache

from PIL import Image

Color = tuple[int, int, int, int]
Region = tuple[int, int, int, int]
SIDES = ("right", "front", "left", "back")


@dataclass(frozen=True)
class Box:
    """Minecraft cuboid UV island: texture offset ``(u, v)`` and size ``w x h x d``."""

    u: int
    v: int
    w: int
    h: int
    d: int

    def faces(self) -> dict[str, Region]:
        """Face name to ``(x, y, width, height)`` following vanilla ``ModelPart.Cube`` UVs."""
        u, v, w, h, d = self.u, self.v, self.w, self.h, self.d
        return {
            "top": (u + d, v, w, d),
            "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h),
            "back": (u + 2 * d + w, v + d, w, h),
        }


@dataclass(frozen=True)
class Style:
    """Look of one kind.

    Colors are hex strings. ``face`` is an 8x8 glyph grid for the front of the head (see
    ``FACE_GLYPHS``); ``body`` picks the torso layout (``shirt``, ``overalls`` or ``robe``) and
    ``sleeve`` how many arm rows the shirt covers. ``glow`` colors the emissive ``g``/``v`` glyphs.
    """

    skin: str
    shirt: str
    pants: str
    boots: str
    accent: str
    face: tuple[str, ...]
    hair: str | None = None
    glow: str | None = None
    body: str = "shirt"
    sleeve: int = 4
    trim: str = "4a3526"
    egg: tuple[str, str] | None = None


@dataclass(frozen=True)
class Prop:
    """One accessory cuboid of ``ZombieAttachments`` and the material painted on its faces."""

    box: Box
    material: str


HUMANOID = {
    "head": Box(0, 0, 8, 8, 8),
    "body": Box(16, 16, 8, 12, 4),
    "right_arm": Box(40, 16, 4, 12, 4),
    "left_arm": Box(32, 48, 4, 12, 4),
    "right_leg": Box(0, 16, 4, 12, 4),
    "left_leg": Box(16, 48, 4, 12, 4),
}
# Light from the top-left of the front view: the model's right side faces the viewer's left.
FACE_LIGHT = {"top": 1, "right": 0, "front": 0, "left": -1, "back": -1, "bottom": -1}
# Front-face glyphs: (material, ramp level, emits). "skin"/"hair"/... resolve through the style.
FACE_GLYPHS = {
    "h": ("hair", None, False),
    "d": ("skin", 1, False),
    "D": ("skin", 0, False),
    "l": ("skin", 3, False),
    "e": ("1d211c", 1, False),
    "w": ("d9d5be", 2, False),
    "p": ("2a2622", 0, False),
    "m": ("3a2220", 1, False),
    "t": ("d6cfaa", 2, False),
    "r": ("9a2f2f", 2, False),
    "a": ("accent", 2, False),
    "A": ("accent", 1, False),
    "k": ("1c1b22", 1, False),
    "W": ("ece9e0", 3, False),
    "s": ("shirt", 2, False),
    "g": ("glow", 2, True),
    "v": ("accent", 1, True),
}


@cache
def ramp(hex_color: str) -> tuple[Color, ...]:
    """Five tones darkest first; shadows drift toward blue-violet and lights toward warm yellow."""
    r, g, b = bytes.fromhex(hex_color)
    hue, sat, val = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    tones = []
    for step, mul in zip((-2, -1, 0, 1, 2), (0.5, 0.73, 1.0, 1.16, 1.3), strict=True):
        target = 0.68 if step < 0 else 0.14
        diff = (target - hue + 0.5) % 1 - 0.5
        h = (hue + diff * 0.06 * abs(step) * min(1, sat * 3)) % 1
        s = min(1, max(0, sat + (0.07 * -step if step < 0 else -0.07 * step)))
        v = min(1, val * mul + (0.03 * step if step > 0 else 0))
        tones.append(tuple(round(c * 255) for c in colorsys.hsv_to_rgb(h, s, v)) + (255,))
    return tuple(tones)


def noise(x: int, y: int, seed: int) -> int:
    """Deterministic 0..255 hash used for sparse dithering and scatter placement."""
    h = (x * 374761393 + y * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


class Painter:
    """Paints shaded materials onto named cuboids of one 64x64 atlas plus a glow atlas."""

    def __init__(self, boxes: dict[str, Box], palette: dict[str, str], seed: int):
        self.image = Image.new("RGBA", (64, 64))
        self.glow = Image.new("RGBA", (64, 64))
        self.boxes = boxes
        self.palette = palette
        self.seed = seed

    def color(self, material: str, level: int) -> Color:
        """Tone ``level`` (0..4) of a palette key or hex material."""
        return ramp(self.palette.get(material, material))[max(0, min(4, level))]

    def pos(self, part: str, face: str, fx: int, fy: int) -> tuple[int, int]:
        x, y, w, h = self.boxes[part].faces()[face]
        assert 0 <= fx < w and 0 <= fy < h, (part, face, fx, fy)
        return x + fx, y + fy

    def size(self, part: str, face: str) -> tuple[int, int]:
        return self.boxes[part].faces()[face][2:]

    def level(self, part: str, face: str, fx: int, fy: int, offset: int = 0) -> int:
        """Face light, rim highlight/foot shadow and sparse dither for one texel."""
        _, h = self.size(part, face)
        lvl = 2 + FACE_LIGHT[face] + offset + (face == "bottom" and part.endswith("arm"))
        if face not in ("top", "bottom"):
            if fy == 0:
                lvl += 1 if part in ("head", "right_arm", "left_arm") else -1
            elif fy == h - 1:
                lvl -= 1
        n = noise(*self.pos(part, face, fx, fy), self.seed)
        return lvl - (n < 14) + (n > 244)

    def shade(self, part: str, face: str, fx: int, fy: int, mat: str, offset: int = 0) -> None:  # noqa: PLR0917
        """Paint ``mat`` with full lighting at a face-local texel."""
        lvl = self.level(part, face, fx, fy, offset)
        self.image.putpixel(self.pos(part, face, fx, fy), self.color(mat, lvl))

    def put(self, part: str, face: str, fx: int, fy: int, mat: str, level: int) -> None:  # noqa: PLR0917
        """Paint a fixed tone, ignoring lighting (eyes, emblems, outlines)."""
        self.image.putpixel(self.pos(part, face, fx, fy), self.color(mat, level))

    def emit(self, part: str, face: str, fx: int, fy: int, mat: str, glow: str) -> None:  # noqa: PLR0917
        """Paint a dim base texel and its full-bright twin on the glow atlas."""
        self.put(part, face, fx, fy, mat, 1)
        self.glow.putpixel(self.pos(part, face, fx, fy), ramp(glow)[2])

    def rect(self, part, face, x0, y0, x1, y1, mat, offset=0) -> None:  # noqa: PLR0917
        """Shade an inclusive face-local rectangle."""
        for fy in range(y0, y1 + 1):
            for fx in range(x0, x1 + 1):
                self.shade(part, face, fx, fy, mat, offset)

    def ring(self, part: str, fy: int, mat: str, offset: int = 0) -> None:
        """Shade one row all around a cuboid (belts, cuffs, stripes)."""
        for face in SIDES:
            for fx in range(self.size(part, face)[0]):
                self.shade(part, face, fx, fy, mat, offset)

    def cap(self, part: str, face: str, mat: str, offset: int = 0) -> None:
        """Shade a whole face (hand ends, soles, lids)."""
        w, h = self.size(part, face)
        self.rect(part, face, 0, 0, w - 1, h - 1, mat, offset)

    def fill(self, part: str, material: Callable[[str, int, int], str | None]) -> None:
        """Shade every face of ``part`` with the material chosen per texel."""
        for face in self.boxes[part].faces():
            w, h = self.size(part, face)
            for fy in range(h):
                for fx in range(w):
                    mat = material(face, fx, fy)
                    if mat:
                        self.shade(part, face, fx, fy, mat)

    def scatter(self, parts, faces, chance: int, salt: int):
        """Yield deterministic ``(part, face, fx, fy)`` picks with ``chance``/256 probability."""
        for part in parts:
            for face in faces:
                w, h = self.size(part, face)
                for fy in range(h):
                    for fx in range(w):
                        if noise(*self.pos(part, face, fx, fy), self.seed + salt) < chance:
                            yield part, face, fx, fy


def face(*rows: str) -> tuple[str, ...]:
    assert len(rows) == 8 and all(len(r) == 8 for r in rows), rows
    return rows


# Order follows ZombieKind. Skins stay in the grey-green vanilla zombie family unless the
# theme demands otherwise (charred, drowned, frozen, mime paint, shadow).
STYLES = {
    "minero": Style(
        "7d946a",
        "6e5a45",
        "34506a",
        "2e2620",
        "f3c530",
        glow="ffe888",
        hair="3b3029",
        body="overalls",
        sleeve=5,
        face=face(
            "hhhhhhhh",
            "h.D....h",
            ".DDd.DD.",
            ".gg..gg.",
            ".dD...d.",
            "...DD...",
            "..mmmd..",
            ".d.D....",
        ),
        egg=("34506a", "f3c530"),
    ),
    "ladron": Style(
        "7e8b73",
        "2a2d40",
        "23242e",
        "17171c",
        "bf343d",
        hair="221d1c",
        sleeve=12,
        face=face(
            "hhhhhhhh",
            "h......h",
            "kkkkkkkk",
            "kwpkkpwk",
            ".kk..kk.",
            "...dd...",
            "..mmm...",
            ".d.d.d.d",
        ),
    ),
    "bombero": Style(
        "3d3536",
        "5a4632",
        "4a3a2c",
        "1b1a1c",
        "ff731d",
        glow="ff8527",
        sleeve=12,
        trim="c9a640",
        face=face(
            "DDdDDdDD",
            "d.D...d.",
            ".DD..DD.",
            ".gg..gg.",
            "..D..D..",
            "...DD...",
            ".mtmtm..",
            "..D..D..",
        ),
    ),
    "abismo": Style(
        "4f9e96",
        "c8d3c6",
        "2f4d63",
        "263240",
        "42f6ee",
        glow="50fff3",
        hair="3f6b3a",
        sleeve=5,
        face=face(
            "hhhhhhhh",
            "hh....hh",
            "h.DD.DDh",
            ".gg..gg.",
            "h.d..d..",
            "...DD..w",
            "..mmmm..",
            "...dd...",
        ),
        egg=("2f4d63", "42f6ee"),
    ),
    "escudero": Style(
        "76866b",
        "85878a",
        "4a3b2c",
        "55585c",
        "75ce50",
        hair="3a2e24",
        sleeve=12,
        face=face(
            "hhhhhhhh",
            "........",
            ".DDd.DD.",
            ".ee..ee.",
            "..d..r..",
            "...DDr..",
            "..mmmm..",
            "........",
        ),
    ),
    "hambriento": Style(
        "b5b07a",
        "6a563f",
        "4a3c2e",
        "2f2720",
        "ede1b4",
        sleeve=3,
        face=face(
            "........",
            ".d....d.",
            ".DDddDD.",
            ".ew..we.",
            ".DD..DD.",
            "d..DD..d",
            "dmtmtmtd",
            "dmmmmmmd",
        ),
    ),
    "griton": Style(
        "a9a999",
        "665b65",
        "3d3540",
        "262228",
        "b72f3e",
        hair="4a4440",
        sleeve=4,
        face=face(
            "hhhhhhhh",
            ".D....D.",
            ".ww..ww.",
            ".wp..pw.",
            ".D....D.",
            "..mmmm..",
            ".mrrrrm.",
            ".mrttrm.",
        ),
    ),
    "bruja": Style(
        "8c9c73",
        "5b3778",
        "5b3778",
        "2a1f30",
        "c7dc78",
        hair="2a2131",
        body="robe",
        sleeve=10,
        face=face(
            "hhhhhhhh",
            "h......h",
            "h.DD.DD.",
            ".ea..ae.",
            "h..ld..h",
            "h..DW..h",
            "h.mmmm.h",
            "h......h",
        ),
    ),
    "granjero": Style(
        "9aab78",
        "a8443a",
        "3c6582",
        "4a3424",
        "e5bf64",
        hair="6b4a2c",
        body="overalls",
        sleeve=6,
        face=face(
            "hhhhhhhh",
            "h......h",
            ".DD..DD.",
            ".ee..ee.",
            "........",
            "...DD...",
            ".mmtmm..",
            "d.dddd.d",
        ),
    ),
    "congelado": Style(
        "a9d4e0",
        "6f879a",
        "3f5568",
        "2c3a48",
        "e5fbff",
        sleeve=12,
        face=face(
            "WWlWWlWW",
            "lW....Wl",
            ".DD..DDW",
            ".aa..aa.",
            "..d..d..",
            "...DD...",
            "..AAAA..",
            ".W.ll.W.",
        ),
        egg=("6f879a", "e5fbff"),
    ),
    "jinete": Style(
        "8c9775",
        "c2a878",
        "48506a",
        "5a3a24",
        "d4a979",
        hair="3e2c20",
        sleeve=12,
        face=face(
            "hhhhhhhh",
            "h......h",
            ".DD..DD.",
            ".ee..ee.",
            "........",
            ".hhhhhh.",
            "..mmmm..",
            ".d....d.",
        ),
        egg=("855a3d", "d4a979"),
    ),
    "bilioso": Style(
        "91ae42",
        "59663b",
        "4a4a32",
        "2e2c22",
        "e6e86a",
        sleeve=3,
        face=face(
            "........",
            ".l....l.",
            ".DD..DD.",
            ".ee..ee.",
            ".dd..dd.",
            "...DD...",
            ".mmmmmm.",
            ".maam...",
        ),
    ),
    "enano": Style(
        "999782",
        "6a4a5e",
        "4a4038",
        "2b2522",
        "ffe073",
        glow="ffe976",
        hair="b8b4a8",
        sleeve=12,
        face=face(
            "hhhhhhhh",
            "........",
            ".DD..DD.",
            ".gg..gg.",
            "..d..d..",
            ".hlDDlh.",
            "hhmmmmhh",
            "hhhhhhhh",
        ),
    ),
    "contagiado": Style(
        "80996a",
        "b9b8a0",
        "6e7a86",
        "3a3a40",
        "ba52d5",
        glow="c455f0",
        sleeve=4,
        face=face(
            "........",
            ".v....d.",
            ".DD.vDD.",
            ".gg..gg.",
            ".v...d..",
            "v..DD...",
            "..mmmm..",
            "...v....",
        ),
    ),
    "mimo": Style(
        "dcdcd0",
        "e8e6e0",
        "1e1e26",
        "141418",
        "c23a3a",
        hair="1c1b22",
        sleeve=12,
        face=face(
            "hhhhhhhh",
            ".k....k.",
            "..k..k..",
            ".kk..kk.",
            "..k..k..",
            ".k....k.",
            "..kkkk..",
            "........",
        ),
        egg=("e8e6e0", "1e1e26"),
    ),
    "explorador": Style(
        "2c3247",
        "161d2e",
        "141a28",
        "0d1018",
        "ff2636",
        glow="ff2434",
        hair="10141f",
        sleeve=12,
        face=face(
            "hhhhhhhh",
            "hDDDDDDh",
            "hDDDDDDh",
            "Dgg..ggD",
            "DDD..DDD",
            "D......D",
            "D.kkkk.D",
            "DD....DD",
        ),
    ),
}


def paint_skin(name: str, style: Style, seed: int) -> tuple[Image.Image, Image.Image]:
    """Base 64x64 skin and its glow atlas; the hat overlay island stays transparent."""
    p = Painter(HUMANOID, style_palette(style), seed)
    for part in HUMANOID:
        p.fill(part, lambda f, x, y, part=part: base_material(style, part, f, x, y))
    paint_face(p, style)
    DETAILS.get(name, lambda _p, _s: None)(p, style)
    return p.image, p.glow


def style_palette(style: Style) -> dict[str, str]:
    return {
        "skin": style.skin,
        "shirt": style.shirt,
        "pants": style.pants,
        "boots": style.boots,
        "accent": style.accent,
        "trim": style.trim,
        "hair": style.hair or style.skin,
        "glow": style.glow or "1d211c",
    }


def base_material(style: Style, part: str, face: str, fx: int, fy: int) -> str:
    """Default clothing layout shared by every kind before themed details."""
    if part == "head":
        return head_material(style, face, fx, fy)
    if part == "body":
        return body_material(style, face, fx, fy)
    if part.endswith("arm"):
        if face in ("top", "bottom"):
            return (
                "shirt" if face == "top" or style.sleeve >= 12 and style.body == "robe" else "skin"
            )
        torn = fy == style.sleeve and (fx * 7 + fy) % 3 == 0
        return "shirt" if fy < style.sleeve or torn else "skin"
    if face == "bottom" or fy >= 10:
        return "boots"
    return "shirt" if style.body == "robe" else "pants"


def head_material(style: Style, face: str, fx: int, fy: int) -> str:
    """Hair caps the top, wraps the sides (sideburns toward the back) and covers the nape."""
    if not style.hair or face in ("front", "bottom"):
        return "skin"
    toward_back = fx < 4 if face == "right" else fx >= 4
    covered = (
        face == "top"
        or (face == "back" and fy < 6)
        or (face in ("right", "left") and (fy < 2 or (fy < 5 and toward_back)))
    )
    return "hair" if covered else "skin"


def body_material(style: Style, face: str, fx: int, fy: int) -> str:
    """Torso layout: plain shirt, robe, or overalls with bib and straps over the shirt."""
    if style.body != "overalls":
        return "shirt"
    if fy >= 8 or face == "bottom":
        return "pants"
    if face == "front" and (fy >= 4 and 1 <= fx <= 6 or fx in (1, 6)):
        return "pants"
    return "pants" if face == "back" and fx in (1, 6) else "shirt"


def paint_face(p: Painter, style: Style) -> None:
    """Stamp the 8x8 glyph grid onto the head's front face."""
    for fy, row in enumerate(style.face):
        for fx, glyph in enumerate(row):
            if glyph == ".":
                continue
            mat, level, emits = FACE_GLYPHS[glyph]
            if emits and style.glow:
                p.emit("head", "front", fx, fy, mat, style.glow)
            elif level is None:
                p.shade("head", "front", fx, fy, mat)
            else:
                p.put("head", "front", fx, fy, mat, level)


def limbs(p: Painter) -> tuple[str, ...]:
    return ("right_arm", "left_arm", "right_leg", "left_leg")


def belt(p: Painter, fy: int = 9, buckle: str | None = None) -> None:
    """Leather belt around the waist with an optional metal buckle on the front."""
    p.ring("body", fy, "trim", -1)
    if buckle:
        for fx in (3, 4):
            p.put("body", "front", fx, fy, buckle, 3 if fx == 3 else 2)


def grime(p: Painter, parts, mat: str, chance: int, salt: int, offset: int = -1) -> None:  # noqa: PLR0917
    """Sparse stains (soot, frost, bile, algae) over the given parts' visible faces."""
    for part, face, fx, fy in p.scatter(parts, SIDES, chance, salt):
        p.shade(part, face, fx, fy, mat, offset)


def minero(p: Painter, s: Style) -> None:
    """Overalls with brass buttons, tool belt pouch and coal dust."""
    for fx in (1, 6):
        p.put("body", "front", fx, 4, "f3c530", 2)
    belt(p, 8)
    p.rect("body", "front", 5, 8, 6, 10, "6b4a2c")
    p.put("body", "front", 5, 8, "6b4a2c", 3)
    grime(p, ("body", "right_leg", "left_leg", "right_arm"), "2b2a2c", 16, 1)
    for leg in ("right_leg", "left_leg"):
        p.rect(leg, "front", 1, 5, 2, 6, "4a6680")


def ladron(p: Painter, s: Style) -> None:
    """Striped burglar sweater, red neckerchief and black gloves."""
    for part in ("body", "right_arm", "left_arm"):
        for fy in range(1, 10, 3):
            p.ring(part, fy, "4a4e66")
    p.ring("body", 0, "accent")
    p.rect("body", "front", 3, 1, 4, 2, "accent", -1)
    for arm in ("right_arm", "left_arm"):
        for fy in (10, 11):
            p.ring(arm, fy, "1c1b22")
        p.cap(arm, "bottom", "1c1b22")
    belt(p, 10)


def bombero(p: Painter, s: Style) -> None:
    """Scorched turnout coat with dull reflective bands and glowing ember cracks."""
    for part, fy in (
        ("body", 7),
        ("right_arm", 8),
        ("left_arm", 8),
        ("right_leg", 8),
        ("left_leg", 8),
    ):
        p.ring(part, fy, "trim", -1)
    for fy in range(1, 7):
        p.put("body", "front", 3 + fy % 2, fy, "2a2220", 1)
    embers(p)


def embers(p: Painter) -> None:
    """Short diagonal crack runs that glow orange; capped so the glow layer stays sparse."""
    picks = list(p.scatter(("head", "body", *limbs(p)), SIDES, 6, 7))
    for part, face, fx, fy in picks[:36]:
        w, h = p.size(part, face)
        if part == "head" and face == "front" and fy in (3, 4):
            continue
        for step in range(2):
            x, y = fx + step, fy + step
            if x < w and y < h:
                p.emit(part, face, x, y, "7a331a", "ff8527")


def abismo(p: Painter, s: Style) -> None:
    """Sailor stripes, rotted trousers, algae and barnacles."""
    for part in ("body", "right_arm", "left_arm"):
        for fy in (2, 5):
            p.ring(part, fy, "2f4d63")
    p.rect("body", "front", 2, 0, 5, 1, "2f4d63")
    grime(p, ("head", "body", *limbs(p)), "3f6b3a", 16, 3)
    for part, face, fx, fy in p.scatter(("body", "right_arm", "left_leg"), SIDES, 5, 4):
        p.put(part, face, fx, fy, "c6b990", 3)


def escudero(p: Painter, s: Style) -> None:
    """Chainmail hauberk under a green tabard with a skull badge."""
    for part in ("body", "right_arm", "left_arm"):
        for face in SIDES:
            w, _ = p.size(part, face)
            for fy in range(0, 10 if part != "body" else 12):
                for fx in range(w):
                    if (fx + fy) % 2:
                        p.shade(part, face, fx, fy, "shirt", -1)
    p.rect("body", "front", 2, 1, 5, 11, "4d7a3a")
    p.rect("body", "back", 2, 1, 5, 11, "4d7a3a", -1)
    for fx, fy in ((3, 3), (4, 3), (3, 4), (4, 4), (3, 5), (4, 5)):
        p.put("body", "front", fx, fy, "e8e4d0", 2)
    p.put("body", "front", 3, 4, "1c1b22", 1)
    p.put("body", "front", 4, 4, "1c1b22", 1)
    belt(p, 9, "b7b9b5")


def hambriento(p: Painter, s: Style) -> None:
    """Shredded shirt hanging open over a gaunt ribcage."""
    for fy in range(1, 9):
        for fx in range(2, 6):
            p.shade("body", "front", fx, fy, "skin", -1 if fy % 2 == 0 else 0)
    p.rect("body", "front", 3, 8, 4, 9, "skin", -1)
    for fx in range(8):
        p.shade("body", "front", fx, 10 + fx % 2, "shirt", -1)
    belt(p, 9)


def griton(p: Painter, s: Style) -> None:
    """Torn collar, blood drips from the scream and frayed hems."""
    p.rect("body", "front", 2, 0, 5, 0, "skin", -1)
    for fx, length in ((3, 3), (4, 2), (5, 1)):
        for fy in range(length):
            p.put("body", "front", fx, fy, "7a2228", 2 - fy % 2)
    for fx in range(0, 8, 3):
        p.shade("body", "front", fx, 11, "skin")


def bruja(p: Painter, s: Style) -> None:
    """Robe trimmed in lichen green, rope belt with three potion vials and a pendant."""
    p.ring("body", 8, "8a6a3c")
    for fx, color in ((1, "58d9a4"), (3, "ca68c2"), (5, "6cb6ea")):
        p.put("body", "front", fx, 9, "d8d0b0", 3)
        p.put("body", "front", fx, 10, color, 2)
        p.put("body", "front", fx, 11, color, 1)
    p.put("body", "front", 3, 2, "accent", 3)
    p.put("body", "front", 4, 1, "accent", 2)
    for leg in ("right_leg", "left_leg"):
        p.ring(leg, 9, "accent", -1)
    for part, face, fx, fy in p.scatter(("body",), ("front", "back"), 10, 9):
        if fy < 8:
            p.put(part, face, fx, fy, "9a7ab8", 2)


def granjero(p: Painter, s: Style) -> None:
    """Red plaid shirt under denim overalls, patched knee and mud on the boots."""
    for part in ("body", "right_arm", "left_arm"):
        for face in SIDES:
            w, h = p.size(part, face)
            for fy in range(h):
                for fx in range(w):
                    if (fx % 3 == 1 or fy % 3 == 1) and base_material(
                        s, part, face, fx, fy
                    ) == "shirt":
                        p.shade(part, face, fx, fy, "e2d6b8" if fx % 3 == fy % 3 else "6e2a26")
    for fx in (1, 6):
        p.put("body", "front", fx, 4, "accent", 3)
    p.rect("right_leg", "front", 1, 4, 2, 6, "accent", -1)
    grime(p, ("right_leg", "left_leg"), "5a4a30", 30, 10)


def congelado(p: Painter, s: Style) -> None:
    """Parka with fur collar and cuffs, frost crusts and frozen boots."""
    p.ring("body", 0, "ddd6c8")
    p.ring("body", 1, "ddd6c8", -1)
    for arm in ("right_arm", "left_arm"):
        p.ring(arm, 9, "ddd6c8")
    for fy in range(2, 10):
        p.put("body", "front", 4, fy, "3f5568", 1)
    grime(p, ("head", "body", *limbs(p)), "e5fbff", 18, 11, 1)


def jinete(p: Painter, s: Style) -> None:
    """Leather vest over a linen shirt, red bandana, gold buckle and spurred boots."""
    for face in SIDES:
        w, _ = p.size("body", face)
        for fy in range(1, 10):
            for fx in range(w):
                if face != "front" or fx < 2 or fx > 5:
                    p.shade("body", face, fx, fy, "855a3d")
    p.ring("body", 0, "a8302c")
    p.rect("body", "front", 3, 1, 4, 2, "a8302c", -1)
    belt(p, 10, "e0bc5b")
    for leg in ("right_leg", "left_leg"):
        p.put(leg, "back", 2, 11, "c9ccc8", 3)


def bilioso(p: Painter, s: Style) -> None:
    """Undersized shirt riding up over a swollen belly, splattered with bile."""
    for face in SIDES:
        w, _ = p.size("body", face)
        for fy in range(7, 10):
            for fx in range(w):
                p.shade("body", face, fx, fy, "skin", 1 if face == "front" and fy == 7 else 0)
    p.put("body", "front", 4, 8, "skin", 0)
    belt(p, 10)
    for part, face, fx, fy in p.scatter(("body", "head", "right_arm", "left_leg"), SIDES, 12, 12):
        if not (part == "head" and face == "front"):
            p.put(part, face, fx, fy, "accent", 2)
            if fy + 1 < p.size(part, face)[1]:
                p.put(part, face, fx, fy + 1, "accent", 1)


def enano(p: Painter, s: Style) -> None:
    """Grey beard flowing onto a plum tunic, wide belt with brass buckle, leather gloves."""
    for fy in range(0, 4):
        for fx in range(1 + fy // 2, 7 - fy // 2):
            p.shade("body", "front", fx, fy, "hair")
    belt(p, 8, "ffe073")
    p.ring("body", 9, "trim")
    for arm in ("right_arm", "left_arm"):
        for fy in (10, 11):
            p.ring(arm, fy, "6b4a2c")
        p.cap(arm, "bottom", "6b4a2c")


def contagiado(p: Painter, s: Style) -> None:
    """Hospital gown with stains, bandaged forearm and pulsing purple veins."""
    for fy in range(2, 11, 2):
        p.put("body", "back", 3, fy, "8a8a78", 1)
    for fy in (6, 7, 9):
        p.ring("right_arm", fy, "e8e4d4")
    for part, face, fx, fy in p.scatter(("body",), ("front", "right"), 14, 13):
        p.put(part, face, fx, fy, "8a6a70", 1)
    veins(p, s)


def veins(p: Painter, s: Style) -> None:
    """Branching vein runs on bare skin that feed the pulsing emissive pass."""
    starts = (
        ("right_arm", "front", 1, 5),
        ("head", "back", 3, 1),
        ("head", "right", 5, 3),
        ("head", "left", 2, 4),
        ("right_leg", "front", 1, 2),
        ("right_arm", "right", 2, 6),
    )
    for part, face, fx, fy in starts:
        w, h = p.size(part, face)
        x = fx
        for y in range(fy, min(h, fy + 5)):
            x = max(0, min(w - 1, x + (noise(x, y, p.seed) % 3) - 1))
            p.emit(part, face, x, y, "accent", s.glow)


def mimo(p: Painter, s: Style) -> None:
    """Breton stripes, black suspenders, white gloves and a red neck scarf."""
    for part in ("body", "right_arm", "left_arm"):
        for fy in range(1, 9, 2):
            p.ring(part, fy, "1e1e26")
    for fx in (1, 6):
        p.rect("body", "front", fx, 0, fx, 9, "1e1e26", -1)
        p.rect("body", "back", fx, 0, fx, 9, "1e1e26", -1)
    p.ring("body", 0, "accent")
    p.rect("body", "front", 4, 1, 4, 2, "accent", -1)
    p.ring("body", 10, "1e1e26")
    p.ring("body", 11, "1e1e26")


def explorador(p: Painter, s: Style) -> None:
    """Shadow cloak with faint seams and a hood rim; only the eyes glow."""
    for part in ("body", "right_arm", "left_arm"):
        p.ring(part, 0, "2c3a52")
    for fy in range(1, 12):
        p.put("body", "front", 3, fy, "0b0e16", 1)
    for part, face, fx, fy in p.scatter(("body", *limbs(p)), SIDES, 14, 14):
        p.put(part, face, fx, fy, "263049", 2)


DETAILS = {
    f.__name__: f
    for f in (
        minero,
        ladron,
        bombero,
        abismo,
        escudero,
        hambriento,
        griton,
        bruja,
        granjero,
        congelado,
        jinete,
        bilioso,
        enano,
        contagiado,
        mimo,
        explorador,
    )
}


def hat_props(crown_height: int) -> list[Prop]:
    return [Prop(Box(0, 0, 12, 1, 12), "hat"), Prop(Box(0, 13, 8, crown_height, 8), "hat")]


def pick_props(length: int) -> list[Prop]:
    return [Prop(Box(0, 32, 1, length, 1), "wood"), Prop(Box(32, 32, 7, 2, 2), "iron")]


# Mirrors ZombieAttachments.create: texture offsets and integer UV sizes of every prop cube.
PROPS = {
    "minero": [
        Prop(Box(0, 0, 9, 2, 9), "helmet"),
        Prop(Box(48, 0, 3, 3, 2), "lamp"),
        *pick_props(12),
    ],
    "ladron": [Prop(Box(16, 32, 8, 9, 5), "sack"), Prop(Box(0, 0, 9, 3, 9), "hood")],
    "bombero": [Prop(Box(0, 0, 1, 9, 3), "tank")],
    "abismo": [Prop(Box(0, 48, 1, 7, 2), "kelp"), Prop(Box(16, 48, 2, 3, 2), "barnacle")],
    "escudero": [Prop(Box(0, 0, 9, 3, 9), "helm"), Prop(Box(0, 13, 8, 11, 2), "shield")],
    "hambriento": [Prop(Box(0, 0, 8, 3, 5), "jaw")],
    "griton": [Prop(Box(0, 0, 8, 2, 5), "jaw"), Prop(Box(0, 16, 2, 2, 1), "eye")],
    "bruja": [
        Prop(Box(0, 0, 12, 1, 12), "hat"),
        Prop(Box(0, 13, 8, 4, 8), "hat"),
        Prop(Box(32, 13, 4, 4, 4), "hat"),
        Prop(Box(16, 48, 3, 2, 3), "mushroom"),
        Prop(Box(0, 48, 3, 3, 2), "bottle"),
    ],
    "granjero": [
        *hat_props(3),
        Prop(Box(0, 32, 1, 20, 1), "wood"),
        Prop(Box(32, 32, 5, 1, 1), "iron"),
        Prop(Box(32, 32, 1, 4, 1), "iron"),
    ],
    "congelado": [Prop(Box(0, 0, 1, 5, 1), "icicle"), Prop(Box(16, 48, 10, 3, 6), "fur")],
    "jinete": [*hat_props(3), Prop(Box(16, 32, 8, 3, 3), "bedroll")],
    "bilioso": [Prop(Box(0, 0, 10, 8, 7), "belly")],
    "enano": [
        Prop(Box(0, 0, 9, 2, 9), "helmet"),
        Prop(Box(48, 0, 1, 4, 1), "candle"),
        Prop(Box(0, 13, 8, 5, 3), "beard"),
        *pick_props(7),
    ],
}
# Base color per prop material; hat/helmet/hood/jaw colors come from the kind style.
MATERIALS = {
    "wood": "7a5634",
    "iron": "9a9c98",
    "sack": "a88b5c",
    "tank": "4a3a2c",
    "kelp": "3f7a3a",
    "barnacle": "c6b990",
    "helm": "8f938f",
    "shield": "6a4a2e",
    "eye": "e4dfc8",
    "mushroom": "b8402e",
    "bottle": "58d9a4",
    "icicle": "cdeef8",
    "fur": "e2dccd",
    "bedroll": "8a5a3a",
    "candle": "e8dcb0",
    "lamp": "3a3a3a",
}
PROP_COLORS = {
    "minero": {"helmet": "e8b923"},
    "ladron": {"hood": "26252e"},
    "escudero": {},
    "hambriento": {"jaw": "a8a370"},
    "griton": {"jaw": "9a9a8a"},
    "bruja": {"hat": "4a2c63"},
    "granjero": {"hat": "e5bf64"},
    "jinete": {"hat": "6e4a30"},
    "bilioso": {"belly": "8aa63e"},
    "enano": {"helmet": "8a8c86", "beard": "b8b4a8"},
}


def paint_props(name: str, seed: int) -> tuple[Image.Image, Image.Image]:
    """Accessory atlas and its emissive twin, painted per prop face; unused UVs stay clear."""
    palette = MATERIALS | PROP_COLORS.get(name, {})
    props = PROPS.get(name, [])
    boxes = {f"p{i}": prop.box for i, prop in enumerate(props)}
    p = Painter(boxes, palette, seed)
    for i, prop in enumerate(props):
        part = f"p{i}"
        p.fill(part, lambda f, x, y, mat=prop.material: mat)
        PROP_DETAILS.get(prop.material, lambda *_: None)(p, part)
    return p.image, p.glow


def every_side(p: Painter, part: str):
    for face in SIDES:
        w, h = p.size(part, face)
        for fy in range(h):
            for fx in range(w):
                yield face, fx, fy


def wood(p: Painter, part: str) -> None:
    """Vertical grain streaks."""
    for face, fx, fy in every_side(p, part):
        if (fy + noise(fx, 0, p.seed)) % 4 == 0:
            p.shade(part, face, fx, fy, "wood", -1)


def helmet(p: Painter, part: str) -> None:
    """Dark band on the brim row and scuffs on the dome."""
    _, h = p.size(part, "front")
    p.ring(part, h - 1, "4a3a2a")
    for face, fx, fy in every_side(p, part):
        if noise(fx, fy, p.seed + 31) < 20:
            p.shade(part, face, fx, fy, "helmet", -1)


def lamp(p: Painter, part: str) -> None:
    """Glowing lens framed by a brass rim."""
    w, h = p.size(part, "front")
    for fx in range(w):
        for fy in range(h):
            rim = fx in (0, w - 1) or fy in (0, h - 1)
            if rim:
                p.shade(part, "front", fx, fy, "c9a640")
            else:
                p.emit(part, "front", fx, fy, "fff4c0", "fff0a0")


def hood(p: Painter, part: str) -> None:
    """Fold creases and a lighter hem on the front."""
    w, h = p.size(part, "front")
    for fx in range(w):
        p.shade(part, "front", fx, h - 1, "hood", 1)
    for face, fx, fy in every_side(p, part):
        if fx % 3 == 2:
            p.shade(part, face, fx, fy, "hood", -1)


def sack(p: Painter, part: str) -> None:
    """Burlap weave, rope tie on top and a stitched patch."""
    for face, fx, fy in every_side(p, part):
        if (fx + fy) % 2:
            p.shade(part, face, fx, fy, "sack", -1)
    p.ring(part, 1, "5a4026")
    p.rect(part, "back", 2, 4, 4, 6, "8a6a3a")


def tank(p: Painter, part: str) -> None:
    """Scorched air tank with a reflective band and an ember crack."""
    p.ring(part, 2, "c9a640", -1)
    p.ring(part, 6, "c9a640", -1)
    for fy in range(3, 6):
        p.emit(part, "right", 1, fy, "7a331a", "ff8527")


def kelp(p: Painter, part: str) -> None:
    """Alternating leaf blades."""
    for face, fx, fy in every_side(p, part):
        if (fx + fy) % 3 == 0:
            p.shade(part, face, fx, fy, "5aa04a")


def barnacle(p: Painter, part: str) -> None:
    """Dark openings in the shells."""
    for face in SIDES:
        p.put(part, face, 0, 0, "3a3428", 1)


def helm(p: Painter, part: str) -> None:
    """Riveted iron band and a dent."""
    _, h = p.size(part, "front")
    for face, fx, fy in every_side(p, part):
        if fy == h - 1 and fx % 2 == 0:
            p.put(part, face, fx, fy, "d8dad6", 3)
    p.put(part, "front", 3, 0, "helm", 0)
    p.put(part, "front", 4, 1, "helm", 1)


def shield(p: Painter, part: str) -> None:
    """Planked wooden shield: iron rim and the green skull emblem on the outer face."""
    w, h = p.size(part, "front")
    for fx in range(w):
        for fy in range(h):
            if fx in (0, w - 1) or fy in (0, h - 1):
                p.shade(part, "front", fx, fy, "8f938f")
            elif fx % 3 == 0:
                p.shade(part, "front", fx, fy, "shield", -1)
    for fx, fy in (
        (2, 3),
        (3, 2),
        (4, 2),
        (5, 3),
        (2, 4),
        (3, 4),
        (4, 4),
        (5, 4),
        (3, 6),
        (4, 6),
        (3, 5),
        (4, 5),
        (2, 5),
        (5, 5),
    ):
        p.put(part, "front", fx, fy, "75ce50", 2 if fy < 4 else 1)
    p.put(part, "front", 3, 4, "1c2a18", 1)
    p.put(part, "front", 4, 4, "1c2a18", 1)
    p.ring(part, 0, "8f938f")


def jaw(p: Painter, part: str) -> None:
    """Lower jaw: teeth on the upper lip, dark gums and a bloody chin."""
    w, h = p.size(part, "front")
    for fx in range(w):
        p.put(part, "front", fx, 0, "d6cfaa" if fx % 2 else "3a2220", 2)
        p.put(part, "top", fx, p.size(part, "top")[1] - 1, "d6cfaa", 2)
    top_w, top_h = p.size(part, "top")
    for fx in range(1, top_w - 1):
        for fy in range(top_h - 1):
            p.put(part, "top", fx, fy, "6a2a2a", 1)
    p.put(part, "front", 2, h - 1, "9a2f2f", 1)


def eye(p: Painter, part: str) -> None:
    """Bulging eyeball with a tiny pupil and red veins."""
    p.put(part, "front", 0, 0, "1c1b22", 1)
    p.put(part, "front", 1, 1, "b72f3e", 2)


def hat(p: Painter, part: str) -> None:
    """Woven texture with a darker band at the base of the crown."""
    _, h = p.size(part, "front")
    for face, fx, fy in every_side(p, part):
        if (fx + 2 * fy) % 4 == 0:
            p.shade(part, face, fx, fy, "hat", -1)
    if h > 1:
        p.ring(part, h - 1, "hat", -2)
    tw, th = p.size(part, "top")
    for fx, fy in ((2, 3), (7, 8), (4, 9)):
        p.shade(part, "top", fx % tw, fy % th, "hat", 1)


def mushroom(p: Painter, part: str) -> None:
    """Red cap with white dots."""
    p.put(part, "top", 1, 1, "f0ead8", 3)
    p.put(part, "front", 0, 0, "f0ead8", 3)


def bottle(p: Painter, part: str) -> None:
    """Glass highlight and a cork."""
    p.ring(part, 0, "8a6a3c")
    p.put(part, "front", 0, 1, "e4fff4", 3)


def icicle(p: Painter, part: str) -> None:
    """Translucent-looking core and a sharp bright tip."""
    _, h = p.size(part, "front")
    p.ring(part, h - 1, "ffffff", 1)
    p.ring(part, 1, "9cc8da")


def fur(p: Painter, part: str) -> None:
    """Shaggy tufts."""
    for face, fx, fy in every_side(p, part):
        if (fx * 3 + fy) % 4 == 0:
            p.shade(part, face, fx, fy, "b8ae9a")


def bedroll(p: Painter, part: str) -> None:
    """Rolled blanket stripes and leather straps."""
    for face, fx, fy in every_side(p, part):
        if fx % 4 == 1:
            p.shade(part, face, fx, fy, "c8b48a")
    for face in ("front", "back"):
        for fy in range(p.size(part, face)[1]):
            p.shade(part, face, 1, fy, "3a2618")
            p.shade(part, face, 6, fy, "3a2618")


def belly(p: Painter, part: str) -> None:
    """Swollen skin with stretch marks, navel and glistening bile pustules."""
    w, h = p.size(part, "front")
    p.put(part, "front", w // 2, h // 2, "3e4a1e", 1)
    for face, fx, fy in every_side(p, part):
        n = noise(fx, fy, p.seed + 41)
        if n < 18:
            p.put(part, face, fx, fy, "e6e86a", 3)
        elif n < 34:
            p.shade(part, face, fx, fy, "belly", -1)
    for fy in range(1, 6, 2):
        p.put(part, "front", 1, fy, "belly", 1)
        p.put(part, "front", w - 2, fy + 1, "belly", 1)


def candle(p: Painter, part: str) -> None:
    """Wax drips and a full-bright flame on top."""
    p.emit(part, "top", 0, 0, "ffe976", "ffe976")
    for face in SIDES:
        p.emit(part, face, 0, 0, "ffb347", "ffd060")


def beard(p: Painter, part: str) -> None:
    """Braided strands."""
    for face, fx, fy in every_side(p, part):
        if (fx + fy) % 3 == 0:
            p.shade(part, face, fx, fy, "beard", -1)


def iron(p: Painter, part: str) -> None:
    """Bright honed edge on the bottom row."""
    w, h = p.size(part, "front")
    for fx in range(w):
        p.put(part, "front", fx, h - 1, "iron", 4)


PROP_DETAILS = {
    f.__name__: f
    for f in (
        wood,
        helmet,
        lamp,
        hood,
        sack,
        tank,
        kelp,
        barnacle,
        helm,
        shield,
        jaw,
        eye,
        hat,
        mushroom,
        bottle,
        icicle,
        fur,
        bedroll,
        belly,
        candle,
        beard,
        iron,
    )
}


def paint_egg(style: Style, seed: int) -> Image.Image:
    """32x32 spawn egg: shaded shell in the kind's main color with accent spots and outline."""
    base, spot = style.egg or (style.shirt, style.accent)
    image = Image.new("RGBA", (32, 32))
    inside = {(x, y) for y in range(32) for x in range(32) if in_egg(x, y)}
    spots = [(9 + noise(i, 1, seed) % 14, 7 + noise(i, 2, seed) % 18) for i in range(6)]
    for x, y in inside:
        edge = any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        level = 1 + round(egg_light(x, y) * 3)
        spotted = any((x - sx) ** 2 + (y - sy) ** 2 <= 2.5 for sx, sy in spots)
        color = ramp(spot if spotted else base)[max(1, min(4, level))]
        image.putpixel((x, y), ramp(base)[0] if edge else color)
    for x, y in ((10, 8), (11, 8), (10, 9)):
        image.putpixel((x, y), (255, 255, 255, 255))
    return image


def egg_light(x: int, y: int) -> float:
    """Lambert term of an ellipsoid shell lit from the front top-left, 0..1."""
    nx, ny = (x + 0.5 - 16) / 11, (y + 0.5 - 17) / 14
    nz = max(0.0, 1 - nx * nx - ny * ny) ** 0.5
    return max(0.0, min(1.0, -0.45 * nx - 0.55 * ny + 0.7 * nz))


def in_egg(x: int, y: int) -> bool:
    """Egg silhouette, narrower toward the top."""
    dy = (y + 0.5 - 17) / 14
    rx = 10.5 * (1 + 0.16 * dy)
    return (x + 0.5 - 16) ** 2 / rx**2 + dy**2 <= 1
