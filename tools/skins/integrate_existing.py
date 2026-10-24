"""Export the approved mob designs to the mods: their own model, texture, exterior layer and glowing eyes.

For each design this writes, next to the mod's entity textures:
- ``<mob>.png``: the design texture as packed by the skins engine (no resampling).
- ``<mob>_exterior.png``: the exterior (relief) layer, same UV, drawn on the inflated copy of the model.
- ``<mob>_ojos.png``: only the eye texels (the engine's OJO ramp), drawn full-bright like spider eyes.
and ``assets/<ns>/dolfin/modelos/<mob>.json``: the model in Minecraft coordinates, read by ``ModeloDiseno.java``.

The model JSON groups the pieces into animation bones: the head (turns to look), legs (swing when walking, in
alternating phases), arms (small swing) and dangling parts (sway). A piece follows a bone when its pivot sits
inside a piece of that bone, so helmets ride on heads and shins on thighs.
"""

from __future__ import annotations

import importlib.util
import json
import math
import sys
from dataclasses import dataclass
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SKILL_SCRIPTS = ROOT / ".claude" / "skills" / "skins-mobs" / "scripts"
DESIGNS = ROOT / "tools" / "skins" / "mobs"
sys.path.insert(0, str(SKILL_SCRIPTS))
sys.path.insert(0, str(DESIGNS))

import motor  # noqa: E402


@dataclass(frozen=True)
class Mod:
    folder: str
    source_set: str
    namespace: str

    def assets(self) -> Path:
        return ROOT / "mods" / self.folder / "src" / self.source_set / "resources" / "assets" / self.namespace

    def textures(self) -> Path:
        return self.assets() / "textures" / "entity"

    def models(self) -> Path:
        return self.assets() / "dolfin" / "modelos"


CREEPERS = Mod("creepers", "client", "dolfincreepers")
ZOMBIES = Mod("zombies", "main", "dolfinzombies")
SPACE = Mod("space", "main", "dolfinspace")


@dataclass(frozen=True)
class Target:
    design: str
    texture: str
    mod: Mod


CREEPER_DESIGNS = {
    "creeper_electrico": "electrico_creeper",
    "creeper_fuego": "fuego_creeper",
    "creeper_gigante": "gigante_creeper",
    "creeper_hielo": "hielo_creeper",
    "creeper_mini": "mini_creeper",
    "creeper_multiplicador": "multiplicador_creeper",
    "creeper_rey": "rey_creeper",
    "creeper_saltarin": "saltarin_creeper",
    "creeper_toxico": "toxico_creeper",
}
ZOMBIE_DESIGNS = (
    "abismo_zombie",
    "bilioso_zombie",
    "bombero_zombie",
    "bruja_zombie",
    "congelado_zombie",
    "contagiado_zombie",
    "enano_zombie",
    "escudero_zombie",
    "explorador_zombie",
    "granjero_zombie",
    "griton_zombie",
    "hambriento_zombie",
    "jinete_zombie",
    "ladron_zombie",
    "mimo_zombie",
    "minero_zombie",
)
KEPLER_DESIGNS = {
    "parasito_trepador": "parasito",
    "acechador": "acechador",
    "gusano_regolito": "gusano",
    "centinela_flotante": "centinela",
    "zombi_xenomorfo": "zombi_espacial",
    "esqueleto_cristalino": "esqueleto_espacial",
    "creeper_alienigena": "creeper_espacial",
}

# Bone roots by piece role. Every other piece rides on the bone whose piece holds its pivot, or stays on the body.
HEADS = ("cabeza", "ojo")
LEGS = ("pierna", "pierna_i", "hueso_pierna", "pata", "muslo", "pata_del", "anca", "dedo")
ARMS = ("brazo", "brazo_d", "brazo_i", "brazo_largo", "brazo_x", "hueso_brazo")
DANGLING = ("tentaculo", "tentaculo_corto", "cola", "alga", "alga_corta", "capa")
TRUNK = ("cuerpo", "panza", "pecho", "torax", "abdomen", "anillo1", "anillo2", "anillo3", "anillo4", "anillo5")
BODY = "__cuerpo__"


def targets() -> list[Target]:
    """Every design that a mod renders, with the texture name it takes there."""
    result = [Target(key, name, CREEPERS) for key, name in CREEPER_DESIGNS.items()]
    result.append(Target("rey_zombie", "rey_zombie", CREEPERS))
    result += [Target(key, key, ZOMBIES) for key in ZOMBIE_DESIGNS]
    result += [Target(key, name, SPACE) for key, name in KEPLER_DESIGNS.items()]
    return result


def _design(key: str):
    path = DESIGNS / f"{key}.py"
    spec = importlib.util.spec_from_file_location(f"skin_design_{key}", path)
    if spec is None or spec.loader is None:
        raise ImportError(f"No se pudo cargar el diseño de mob: {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


# ---------------------------------------------------------------- geometry
def _matrix(rot: tuple[float, float, float]) -> list[list[float]]:
    """Rotation matrix of a motor rotation (columns are the rotated basis vectors)."""
    columns = [motor.rotar(axis, rot) for axis in ((1, 0, 0), (0, 1, 0), (0, 0, 1))]
    return [[columns[c][r] for c in range(3)] for r in range(3)]


def minecraft_rotation(rot: tuple[float, float, float]) -> tuple[float, float, float]:
    """Motor rotation (degrees, applied x, z, y; y up, +z front) as Minecraft xRot, yRot, zRot in radians.

    Minecraft flips y and z (a half turn about x) and rotates parts as Rz * Ry * Rx, so the matrix is conjugated
    by the flip and decomposed again; this stays exact when a design combines y with x or z rotations.
    """
    m = _matrix(rot)
    flip = (1, -1, -1)
    r = [[m[i][j] * flip[i] * flip[j] for j in range(3)] for i in range(3)]
    y = math.asin(max(-1.0, min(1.0, -r[2][0])))
    if abs(math.cos(y)) > 1e-6:
        x = math.atan2(r[2][1], r[2][2])
        z = math.atan2(r[1][0], r[0][0])
    else:  # gimbal lock: fold z into x
        x, z = math.atan2(-r[1][2], r[1][1]), 0.0
    return x, y, z


def _local(part: dict, point: tuple[float, float, float]) -> tuple[float, float, float]:
    """Point in model coordinates expressed in the part's own (unrotated) coordinates."""
    d = tuple(a - b for a, b in zip(point, part["piv"]))
    m = _matrix(part["rot"])
    return tuple(sum(m[r][c] * d[r] for r in range(3)) for c in range(3))


def _depth(part: dict, point: tuple[float, float, float]) -> float:
    """How far inside the part's box the point is (negative: how far outside)."""
    x, y, z = _local(part, point)
    x0, y0, z0, x1, y1, z1 = part["caja"]
    return min(x - x0, x1 - x, y - y0, y1 - y, z - z0, z1 - z)


def _holds(part: dict, point: tuple[float, float, float], margin: float = 0.6) -> bool:
    return _depth(part, point) >= -margin


def _anchor(part: dict) -> tuple[float, float, float]:
    """Where a piece hangs from: its pivot, or the center of its box when it was placed without one."""
    if any(part["piv"]) or any(part["rot"]):
        return part["piv"]
    x0, y0, z0, x1, y1, z1 = part["caja"]
    return ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)


def _role_kind(role: str) -> str | None:
    if role in HEADS:
        return "cabeza"
    if role in LEGS:
        return "pierna"
    if role in ARMS:
        return "brazo"
    if role in DANGLING:
        return "cuelga"
    return None


def bones(model: list[dict]) -> tuple[list[dict], list[str | None]]:
    """Animation bones and, per piece, the bone it belongs to (None = body, does not move by itself)."""
    found, owner = [], [None] * len(model)
    kinds = {}
    for i, part in enumerate(model):
        if part["r"] in TRUNK:
            owner[i] = BODY
            continue
        kind = _role_kind(part["r"])
        # a chained piece (second tail segment) joins its parent bone of the same kind instead of starting one
        if kind is None or any(kinds.get(owner[j]) == kind and _holds(model[j], part["piv"], 0.3) for j in range(i)):
            continue
        name = f"{kind}_{len(found)}"
        found.append(dict(nombre=name, tipo=kind, piv=part["piv"], raiz=i))
        owner[i], kinds[name] = name, kind
    head = next((b for b in found if b["tipo"] == "cabeza"), None)
    changed = True
    while changed:
        changed = False
        for i, part in enumerate(model):
            if owner[i]:
                continue
            if head and part["piv"] == model[head["raiz"]]["piv"] and part["rot"] == model[head["raiz"]]["rot"]:
                owner[i], changed = head["nombre"], True
                continue
            anchor = _anchor(part)
            best = max(((_depth(other, anchor), j) for j, other in enumerate(model) if owner[j] and j != i),
                       default=None)
            if best and best[0] >= -0.6:
                owner[i], changed = owner[best[1]], True
    owner = [None if o == BODY else o for o in owner]
    for bone in found:
        x, _, z = bone["piv"]
        bone["fase"] = _phase(bone, found)
        bone["pieza"] = bone.pop("raiz")  # the piece that starts the bone (an arm holds items at its end)
        bone["lado"] = 1 if x > 0 else -1 if x < 0 else 0
        bone["frente"] = 1 if z > 0.5 else -1 if z < -0.5 else 0
    return found, owner


def _phase(bone: dict, found: list[dict]) -> float:
    """Walking phase: the two sides alternate, and on four or more legs front and back alternate too."""
    x, _, z = bone["piv"]
    if bone["tipo"] not in ("pierna", "brazo"):
        return 0.0
    same = sorted((b for b in found if b["tipo"] == bone["tipo"] and (b["piv"][0] > 0) == (x > 0)),
                  key=lambda b: b["piv"][2])
    rank = next(k for k, b in enumerate(same) if b is bone)
    left = x > 0
    return math.pi if left ^ (rank % 2 == 1) else 0.0


def export_model(design, model: list[dict], texture: Image.Image) -> dict:
    """Model JSON in Minecraft coordinates (y down, feet at 24, front at -z), bones first, then pieces."""
    found, owner = bones(model)
    pivots = {b["nombre"]: b["piv"] for b in found}

    def mc(point):
        x, y, z = point
        return [round(x, 4), round(24 - y, 4), round(-z, 4)]

    pieces = []
    for part, bone in zip(model, owner):
        x0, y0, z0, x1, y1, z1 = part["caja"]
        piv = part["piv"]
        if bone:
            gx, gy, gz = pivots[bone]
            piv = (piv[0] - gx, piv[1] - gy, piv[2] - gz)
        px, py, pz = piv
        pieces.append(dict(
            nombre=part["r"],
            hueso=bone,
            uv=list(part["uv"]),
            origen=[round(x0, 4), round(-y1, 4), round(-z1, 4)],
            tam=[round(x1 - x0, 4), round(y1 - y0, 4), round(z1 - z0, 4)],
            # inside a bone the pivot is relative to it; the bone itself sits at its pivot with no rotation
            piv=[round(px, 4), round(-py, 4) if bone else round(24 - py, 4), round(-pz, 4)],
            rot=[round(a, 5) for a in minecraft_rotation(part["rot"])],
            inflado=part["inflado"],
        ))
    for bone in found:
        bone["piv"] = mc(bone["piv"])
    alto, ancho = extents(model)
    escala = design.MOB.get("escala", 1.0)
    return dict(
        clave=design.MOB["clave"],
        textura=list(texture.size),
        escala=escala,
        alto=round(alto * escala / 16, 3),
        ancho=round(ancho * escala / 16, 3),
        huesos=found,
        piezas=pieces,
    )


def extents(model: list[dict]) -> tuple[float, float]:
    """Height (from the ground) and widest horizontal extent of the design, in model pixels."""
    xs, ys, zs = [], [], []
    for part in model:
        x0, y0, z0, x1, y1, z1 = part["caja"]
        for corner in ((a, b, c) for a in (x0, x1) for b in (y0, y1) for c in (z0, z1)):
            x, y, z = (u + v for u, v in zip(motor.rotar(corner, part["rot"]), part["piv"]))
            xs.append(x), ys.append(y), zs.append(z)
    return max(ys) - min(0, min(ys)), max(max(xs) - min(xs), max(zs) - min(zs))


def eyes(texture: Image.Image) -> Image.Image:
    """Only the eye texels (OJO ramp), to be drawn full-bright over the model."""
    palette = {color + (255,) for color in motor.OJO}
    out = Image.new("RGBA", texture.size, (0, 0, 0, 0))
    for y in range(texture.size[1]):
        for x in range(texture.size[0]):
            if texture.getpixel((x, y)) in palette:
                out.putpixel((x, y), texture.getpixel((x, y)))
    return out


def render_target(target: Target) -> tuple[Image.Image, Image.Image, Image.Image, dict]:
    """Texture, exterior, eyes and model JSON of one target."""
    design = _design(target.design)
    texture, model, exterior = motor.construir(design)
    return texture, exterior, eyes(texture), export_model(design, model, texture)


def main() -> None:
    print(f"{'mob':24} {'alto':>6} {'ancho':>6}  (bloques, para la hitbox)")
    for target in targets():
        texture, exterior, glow, model = render_target(target)
        textures, models = target.mod.textures(), target.mod.models()
        models.mkdir(parents=True, exist_ok=True)
        texture.save(textures / f"{target.texture}.png")
        exterior.save(textures / f"{target.texture}_exterior.png")
        glow.save(textures / f"{target.texture}_ojos.png")
        with open(models / f"{target.texture}.json", "w", encoding="utf-8", newline="\n") as out:
            json.dump(model, out, ensure_ascii=False, indent=1)
            out.write("\n")
        print(f"{target.texture:24} {model['alto']:6.2f} {model['ancho']:6.2f}")
    print("Listo: 9 creepers, el Rey Zombie, 16 zombies y 7 mobs de Kepler con modelo propio.")


if __name__ == "__main__":
    main()
