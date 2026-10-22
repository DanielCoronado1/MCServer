#!/usr/bin/env python3
"""Shared helper module for DolfinSpace asset, data, and translation generation."""

from __future__ import annotations

import json
import struct
import zlib
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
MOD_DIR = ROOT / "mods/space"
RESOURCES_DIR = MOD_DIR / "src/main/resources"
ASSETS_DIR = RESOURCES_DIR / "assets/dolfinspace"
DATA_DIR = RESOURCES_DIR / "data/dolfinspace"

LOCALES = ("en_us", "es_es", "es_mx", "es_ar")

KEPLER_COLORS = {
    "roca_lunar": (64, 68, 85),
    "bloque_cristal_energia": (55, 225, 220),
    "lodo_acido": (107, 145, 39),
    "hongo_bioluminiscente": (125, 230, 180),
    "mena_titanio": (125, 133, 168),
    "mena_hierro_meteorico": (150, 85, 66),
    "mena_cristal_energia": (57, 148, 150),
}

BIOMES = [
    "kepler_plains",
    "kepler_craters",
    "kepler_crystal_forest",
    "kepler_acid_marshes",
    "kepler_abyssal_caverns",
]

MOBS = [
    "parasito",
    "acechador",
    "gusano",
    "centinela",
    "zombi_espacial",
    "esqueleto_espacial",
    "creeper_espacial",
]

SHIP_THEMES = {
    "nave_basica": (73, 202, 202),
    "nave_carga": (239, 165, 69),
    "nave_rapida": (186, 139, 255),
}

UI_TRANSLATIONS = {
    "launch": ("LANZAR", "LAUNCH"),
    "passengers": ("Pasajeros", "Passengers"),
    "passengers_on": ("Pasajeros: sí", "Passengers: yes"),
    "passengers_off": ("Pasajeros: no", "Passengers: no"),
    "shake": ("Vibración: %s%%", "Camera shake: %s%%"),
    "destination": ("Destino: %s", "Destination: %s"),
    "kepler": ("Kepler", "Kepler"),
    "overworld": ("Overworld", "Overworld"),
    "fuel": ("Combustible: %s%% / mínimo %s%%", "Fuel: %s%% / minimum %s%%"),
    "oxygen": ("%s s (%s%%)", "%s s (%s%%)"),
    "no_tank": ("sin tanque equipado", "no equipped tank"),
    "no_passenger": ("sin pasajero", "no passenger"),
    "pilot": ("Oxígeno piloto: %s", "Pilot oxygen: %s"),
    "passenger": ("Pasajero: %s", "Passenger: %s"),
    "platform": ("%s Plataforma y ascenso libres", "%s Platform and ascent clear"),
    "ground": ("%s Suelo y ascenso libres", "%s Ground and ascent clear"),
    "fuel_check": ("%s Combustible de viaje", "%s Flight fuel"),
    "controls": ("R: cabina · Shift: bajar/cancelar", "R: cockpit · Shift: exit/cancel"),
    "countdown": ("Despegue: %s s", "Liftoff: %s s"),
    "tank_ok": ("✓ Tanque equipado", "✓ Tank equipped"),
    "tank_warning": ("! Equipa un tanque de oxígeno", "! Equip an oxygen tank"),
    "cockpit": ("Panel de mando", "Cockpit"),
    "transit": ("Tránsito estelar", "Star transit"),
    "assemble": ("Ensamblar", "Assemble"),
    "empty_table": ("Vacía la mesa para cambiar", "Empty the table to change tier"),
    "cargo": ("Carga de nave", "Ship cargo"),
    "leave_first": (
        "Baja a los pasajeros antes de recoger la nave.",
        "Disembark passengers before collecting the ship.",
    ),
    "ship_help": (
        "Carga: clic con cofre. Recoger: agachado + clic.",
        "Cargo: click with chest. Collect: sneak + click.",
    ),
    "fuel_tip": ("Combustible: %s%%", "Fuel: %s%%"),
    "guide0": (
        "2 lingotes → placa (acero/cobre/oro/titanio). Carbón al costado.",
        "2 ingots → plate (steel/copper/gold/titanium). Coal at the side.",
    ),
    "guide1": (
        "Redstone + cuarzo + placa de oro → circuito básico; básico + diamante + cristal → avanzado.",
        "Redstone + quartz + gold plate → basic circuit; basic + diamond + crystal → advanced.",
    ),
    "hud_parked": ("En plataforma", "Parked"),
    "hud_ready": ("Lista para despegar", "Ready for liftoff"),
    "hud_not_ready": ("Despegue bloqueado", "Liftoff blocked"),
    "hud_checking": ("Comprobando sistemas…", "Checking systems…"),
    "hud_countdown": ("Despegue en %s", "Liftoff in %s"),
    "hud_ignition": ("Ignición", "Ignition"),
    "hud_ascent": ("Ascenso", "Ascent"),
    "hud_transit": ("Tránsito estelar", "Star transit"),
    "hud_descent": ("Descenso", "Descent"),
    "hud_destination": ("Destino: %s", "Destination: %s"),
    "hud_altitude": ("Altitud: %s m", "Altitude: %s m"),
    "hud_speed": ("Vel. vertical: %s m/s", "Vertical speed: %s m/s"),
    "hud_fuel": ("Combustible %s%%", "Fuel %s%%"),
    "hud_fuel_required": ("mín. %s%%", "min %s%%"),
    "hud_warn_fuel": ("Combustible insuficiente", "Not enough fuel"),
    "hud_warn_surface": ("Plataforma o ascenso bloqueado", "Platform or ascent blocked"),
    "hud_warn_tank": ("Sin tanque de oxígeno", "No oxygen tank"),
    "hud_warn_inactive": ("Viajes espaciales desactivados", "Space travel disabled"),
    "hud_hint": ("R: cabina · Shift: bajar", "R: cockpit · Shift: exit"),
    "guide2": (
        "Lava + blaze + bidón vacío → combustible + cubeta. Carbón al costado.",
        "Lava + blaze powder + empty canister → fuel + bucket. Coal at the side.",
    ),
}

ENGLISH_NAMES = {
    "regolito": "Regolith",
    "roca_lunar": "Lunar rock",
    "bloque_acero": "Steel block",
    "mezcla_acero": "Steel mixture",
    "lingote_acero": "Steel ingot",
    "compresor": "Compressor",
    "refineria": "Refinery",
    "fabricador_circuitos": "Circuit fabricator",
    "compresor_oxigeno": "Oxygen compressor",
    "mesa_ensamblaje": "Assembly table",
    "cargador_combustible": "Fuel loader",
    "bloque_plataforma": "Launch pad",
    "punta_conica": "Nose cone",
    "punta_titanio": "Titanium nose cone",
    "placa_casco": "Hull plate",
    "placa_casco_titanio": "Titanium hull plate",
    "aleta_nave": "Ship fin",
    "motor_nave": "Ship engine",
    "motor_avanzado": "Advanced engine",
    "tanque_combustible_nave": "Ship fuel tank",
    "asiento_piloto": "Pilot seat",
    "nave_basica": "Scout ship",
    "nave_carga": "Cargo ship",
    "nave_rapida": "Interceptor ship",
    "bidon_vacio": "Empty fuel canister",
    "bidon_combustible": "Fuel canister",
    "circuito_basico": "Basic circuit",
    "circuito_avanzado": "Advanced circuit",
    "cristal_energia": "Energy crystal",
    "nucleo_energia": "Energy core",
    "quitina_alienigena": "Alien chitin",
    "diente_gusano": "Worm tooth",
    "parasito": "Parasite",
    "acechador": "Stalker",
    "gusano": "Worm",
    "centinela": "Sentinel",
    "zombi_espacial": "Space zombie",
    "esqueleto_espacial": "Space skeleton",
    "creeper_espacial": "Space creeper",
    "proyectil_energia": "Energy projectile",
    "flecha_cristal": "Crystal arrow",
    "nube_acida": "Acid cloud",
    "nave": "Spaceship",
    "lodo_acido": "Acid mud",
    "hongo_bioluminiscente": "Bioluminescent fungus",
    "bloque_cristal_energia": "Energy crystal block",
}

for material, en in [
    ("acero", "Steel"),
    ("cobre", "Copper"),
    ("oro", "Gold"),
    ("titanio", "Titanium"),
]:
    ENGLISH_NAMES[f"placa_{material}"] = f"{en} plate"

for material, en in [
    ("titanio", "Titanium"),
    ("hierro_meteorico", "Meteoric iron"),
    ("cristal_energia", "Energy crystal"),
]:
    ENGLISH_NAMES[f"mena_{material}"] = f"{en} ore"
    ENGLISH_NAMES[f"lingote_{material}"] = f"{en} ingot"
    ENGLISH_NAMES[f"{material}_crudo"] = f"Raw {en.lower()}"

for size, en in [("chico", "Small"), ("mediano", "Medium"), ("grande", "Large")]:
    ENGLISH_NAMES[f"tanque_oxigeno_{size}"] = f"{en} oxygen tank"


def write_json(path: Path | str, data: Any) -> Path:
    """Serialize data into indented UTF-8 JSON within the Space resources directory."""
    target = path if isinstance(path, Path) and path.is_absolute() else RESOURCES_DIR / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    return target


def read_json(path: Path | str) -> Any:
    """Load JSON from a path relative to the Space resources directory or absolute path."""
    target = path if isinstance(path, Path) and path.is_absolute() else RESOURCES_DIR / path
    return json.loads(target.read_text())


def build_png_bytes(width: int, height: int, raw_scanlines: bytes) -> bytes:
    """Pack raw uncompressed RGBA scanlines (filter byte 0) into valid PNG file bytes."""

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw_scanlines))
        + chunk(b"IEND", b"")
    )


def write_placeholder_png_if_missing(
    rel_path: str, color: tuple[int, int, int], tank: bool = False
) -> None:
    """Write procedural 16x16 placeholder PNG only if no asset is currently present on disk."""
    target = RESOURCES_DIR / rel_path
    if target.exists():
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    raw = b""
    for y in range(16):
        raw += b"\0"
        for x in range(16):
            visible = not tank or (4 <= x <= 11 and 3 <= y <= 14) or (6 <= x <= 9 and 1 <= y <= 3)
            noise = ((x * 19 + y * 7) % 23) - 11
            pixel_rgb = tuple(max(0, min(255, val + noise)) for val in color)
            if tank and (x in {4, 11} or y == 14):
                pixel_rgb = (65, 75, 85)
            raw += bytes((*pixel_rgb, 255 if visible else 0))
    target.write_bytes(build_png_bytes(16, 16, raw))


def write_entity_texture_if_missing(
    name: str, color: tuple[int, int, int], width: int = 64, height: int = 32
) -> None:
    """Write procedural entity placeholder texture if not already present."""
    target = ASSETS_DIR / f"textures/entity/{name}.png"
    if target.exists():
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    raw = b""
    for y in range(height):
        raw += b"\0"
        for x in range(width):
            bright = x % 16 in (4, 5, 10, 11) and y % 16 in (5, 6)
            noise = ((x * 31 + y * 13) % 29) - 14
            pixel = (
                (70, 245, 190) if bright else tuple(max(0, min(255, val + noise)) for val in color)
            )
            raw += bytes((*pixel, 255))
    target.write_bytes(build_png_bytes(width, height, raw))


def write_item_model_and_definition(
    name: str,
    color: tuple[int, int, int] | None = None,
    tank: bool = False,
    model_parent: str = "minecraft:item/generated",
    layer0: str | None = None,
) -> None:
    """Create item model, texture placeholder if missing, and 1.21 item definition."""
    if color is not None:
        write_placeholder_png_if_missing(
            f"assets/dolfinspace/textures/item/{name}.png", color, tank=tank
        )
    texture_ref = layer0 if layer0 else f"dolfinspace:item/{name}"
    write_json(
        f"assets/dolfinspace/models/item/{name}.json",
        {"parent": model_parent, "textures": {"layer0": texture_ref}},
    )
    write_json(
        f"assets/dolfinspace/items/{name}.json",
        {"model": {"type": "minecraft:model", "model": f"dolfinspace:item/{name}"}},
    )


def write_block_assets(
    name: str,
    color: tuple[int, int, int] | None = None,
    cross: bool = False,
    model_texture: str | None = None,
    write_item_model: bool = True,
) -> None:
    """Create blockstate, models, item definition, and placeholder PNG."""
    if color is not None:
        write_placeholder_png_if_missing(f"assets/dolfinspace/textures/block/{name}.png", color)
    tex_ref = model_texture if model_texture else f"dolfinspace:block/{name}"
    write_json(
        f"assets/dolfinspace/blockstates/{name}.json",
        {"variants": {"": {"model": f"dolfinspace:block/{name}"}}},
    )
    parent = "minecraft:block/cross" if cross else "minecraft:block/cube_all"
    tex_key = "cross" if cross else "all"
    write_json(
        f"assets/dolfinspace/models/block/{name}.json",
        {"parent": parent, "textures": {tex_key: tex_ref}},
    )
    if write_item_model:
        write_json(
            f"assets/dolfinspace/models/item/{name}.json", {"parent": f"dolfinspace:block/{name}"}
        )
    write_json(
        f"assets/dolfinspace/items/{name}.json",
        {"model": {"type": "minecraft:model", "model": f"dolfinspace:block/{name}"}},
    )


def write_block_loot(name: str, drop: str | None = None, legacy_condition: bool = True) -> None:
    """Generate default block loot table with explosion resistance."""
    drop_item = drop if drop else name
    entry = {"type": "minecraft:item", "name": f"dolfinspace:{drop_item}"}
    pool: dict[str, Any] = {"rolls": 1, "entries": [entry]}
    if legacy_condition:
        pool["condition"] = {"type": "minecraft:survives_explosion"}
    else:
        pool["conditions"] = [{"condition": "minecraft:survives_explosion"}]
    write_json(
        f"data/dolfinspace/loot_table/blocks/{name}.json",
        {"type": "minecraft:block", "pools": [pool]},
    )


def write_ore_loot(ore: str, drop: str) -> None:
    """Generate ore drop table supporting fortune bonus and silk touch alternative."""
    write_json(
        f"data/dolfinspace/loot_table/blocks/{ore}.json",
        {
            "type": "minecraft:block",
            "pools": [
                {
                    "rolls": 1,
                    "entries": [
                        {
                            "type": "minecraft:alternatives",
                            "children": [
                                {
                                    "type": "minecraft:item",
                                    "condition": "minecraft:tool/can_silk_touch",
                                    "name": f"dolfinspace:{ore}",
                                },
                                {
                                    "type": "minecraft:item",
                                    "name": f"dolfinspace:{drop}",
                                    "modifier": [
                                        {
                                            "type": "minecraft:apply_bonus",
                                            "enchantment": "minecraft:fortune",
                                            "formula": "minecraft:ore_drops",
                                        },
                                        {"type": "minecraft:explosion_decay"},
                                    ],
                                },
                            ],
                        }
                    ],
                }
            ],
        },
    )


def write_entity_loot(name: str, drop: str) -> None:
    """Generate entity death loot table."""
    write_json(
        f"data/dolfinspace/loot_table/entities/{name}.json",
        {
            "type": "minecraft:entity",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": drop}]}],
        },
    )


def write_recipe_advancement(name: str) -> None:
    """Create instant recipe unlock advancement."""
    write_json(
        f"data/dolfinspace/advancement/recipes/{name}.json",
        {
            "criteria": {"tick": {"trigger": "minecraft:tick"}},
            "requirements": [["tick"]],
            "rewards": {"recipes": [f"dolfinspace:{name}"]},
        },
    )


def write_shapeless_recipe(
    name: str,
    ingredients: list[str],
    result: str,
    *,
    count: int = 1,
    category: str | None = None,
    with_advancement: bool = True,
) -> None:
    """Generate shapeless crafting recipe and optional unlock advancement."""
    target_id = result if ":" in result else f"dolfinspace:{result}"
    data: dict[str, Any] = {"type": "minecraft:crafting_shapeless"}
    if category:
        data["category"] = category
    data["ingredients"] = ingredients
    data["result"] = {"id": target_id, "count": count}
    write_json(f"data/dolfinspace/recipe/{name}.json", data)
    if with_advancement:
        write_recipe_advancement(name)


def write_shaped_recipe(
    name: str,
    pattern: list[str],
    key: dict[str, Any],
    *,
    result: str | None = None,
    category: str | None = None,
    count: int | None = None,
    components: dict[str, Any] | None = None,
) -> None:
    """Generate shaped crafting recipe with optional data components."""
    target = result or name
    target_id = target if ":" in target else f"dolfinspace:{target}"
    out: dict[str, Any] = {"id": target_id}
    if count is not None:
        out["count"] = count
    if components:
        out["components"] = components
    data: dict[str, Any] = {"type": "minecraft:crafting_shaped"}
    if category:
        data["category"] = category
    data["pattern"] = pattern
    data["key"] = key
    data["result"] = out
    write_json(f"data/dolfinspace/recipe/{name}.json", data)


def write_cooking_recipe(
    name: str,
    method: str,
    ingredient: str,
    result: str,
    *,
    experience: float = 0.7,
    cookingtime: int = 200,
    category: str | None = None,
    with_advancement: bool = False,
) -> None:
    """Generate smelting or blasting furnace recipe."""
    data: dict[str, Any] = {"type": f"minecraft:{method}"}
    if category:
        data["category"] = category
    target_ing = ingredient if ":" in ingredient else f"dolfinspace:{ingredient}"
    data["ingredient"] = target_ing
    if category:
        data["result"] = {"id": f"dolfinspace:{result}"}
        data["experience"] = experience
        data["cookingtime"] = cookingtime
    else:
        data["cookingtime"] = cookingtime
        data["experience"] = experience
        data["result"] = {"id": f"dolfinspace:{result}"}
    write_json(f"data/dolfinspace/recipe/{name}.json", data)
    if with_advancement:
        write_recipe_advancement(name)


def write_feature(name: str, feature_data: dict[str, Any], placement: list[dict[str, Any]]) -> None:
    """Generate worldgen feature and placed_feature JSON pair."""
    write_json(f"data/dolfinspace/worldgen/feature/{name}.json", feature_data)
    write_json(
        f"data/dolfinspace/worldgen/placed_feature/{name}.json",
        {"feature": f"dolfinspace:{name}", "placement": placement},
    )


def surface_placement(count: int) -> list[dict[str, Any]]:
    """Standard surface heightmap placement modifiers."""
    return [
        {"type": "minecraft:count", "count": count},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
        {"type": "minecraft:biome"},
    ]


def update_translations(keys: dict[str, str], locales: tuple[str, ...] = LOCALES) -> None:
    """Merge translation keys into all active locales."""
    for locale in locales:
        path = f"assets/dolfinspace/lang/{locale}.json"
        data = read_json(path)
        data.update(keys)
        write_json(path, data)
