#!/usr/bin/env python3
"""Deterministic generator entry point for all DolfinSpace assets, data, and translations."""

from __future__ import annotations

import math
import struct
import subprocess
import tempfile
import wave
from pathlib import Path
from typing import Any

import ship_art
from PIL import Image, ImageDraw, ImageFont
from resources import (
    ASSETS_DIR,
    BIOMES,
    DATA_DIR,
    ENGLISH_NAMES,
    KEPLER_COLORS,
    LOCALES,
    MOBS,
    RESOURCES_DIR,
    ROOT,
    SHIP_THEMES,
    UI_TRANSLATIONS,
    read_json,
    surface_placement,
    update_translations,
    write_block_assets,
    write_block_loot,
    write_cooking_recipe,
    write_entity_loot,
    write_entity_texture_if_missing,
    write_feature,
    write_item_model_and_definition,
    write_json,
    write_ore_loot,
    write_placeholder_png_if_missing,
    write_shaped_recipe,
    write_shapeless_recipe,
)

INDUSTRIAL_ITEMS = {
    "mezcla_acero": "iron_ingot",
    "lingote_acero": "netherite_ingot",
    "placa_acero": "iron_ingot",
    "placa_cobre": "copper_ingot",
    "placa_oro": "gold_ingot",
    "placa_titanio": "netherite_ingot",
    "circuito_basico": "repeater",
    "circuito_avanzado": "comparator",
    "bidon_vacio": "bucket",
    "bidon_combustible": "lava_bucket",
}

INDUSTRIAL_BLOCKS = {
    "bloque_acero": "iron_block",
    "compresor": "piston_top",
    "fabricador_circuitos": "crafter_top",
    "refineria": "blast_furnace_top",
}

SHIP_DESIGN_SOURCE = ROOT / "mods/space/src/main/java/com/dolfin/space/ships/ShipDesign.java"

SOUND_SPECS = (
    ("cockpit_hum", 85, 2.0),
    ("check_beep", 1100, 0.12),
    ("fuel_alarm", 250, 0.6),
    ("engine", 55, 1.0),
    ("parasite", 420, 0.35),
)


def main() -> None:
    """Regenerate all DolfinSpace resources in a unified, deterministic pass."""
    generate_industrial_assets()
    generate_oxygen_assets()
    generate_planet_assets()
    generate_fauna_assets()
    generate_ship_and_sound_assets()
    generate_translations()
    print("DolfinSpace assets regenerated successfully.")


def generate_industrial_assets() -> None:
    """Register machines, metal plates, circuit items, and factory crafting recipes."""
    for name in INDUSTRIAL_ITEMS:
        write_item_model_and_definition(name)
    for name in INDUSTRIAL_BLOCKS:
        write_block_assets(name, write_item_model=False)
        write_block_loot(name, legacy_condition=False)

    write_shapeless_recipe(
        "mezcla_acero", ["minecraft:iron_ingot", "minecraft:coal"], "mezcla_acero", category="misc"
    )
    write_cooking_recipe(
        "acero_alto_horno",
        "blasting",
        "mezcla_acero",
        "lingote_acero",
        experience=0.5,
        cookingtime=100,
        category="misc",
        with_advancement=True,
    )
    write_shapeless_recipe(
        "bloque_acero", ["dolfinspace:lingote_acero"] * 9, "bloque_acero", category="misc"
    )
    write_shapeless_recipe(
        "lingotes_acero", ["dolfinspace:bloque_acero"], "lingote_acero", count=9, category="misc"
    )
    write_shapeless_recipe(
        "bidon_vacio", ["dolfinspace:placa_acero"] * 3, "bidon_vacio", category="misc"
    )
    write_shapeless_recipe(
        "compresor",
        ["dolfinspace:lingote_acero"] * 4 + ["minecraft:piston"] * 2 + ["minecraft:furnace"],
        "compresor",
        category="misc",
    )
    write_shapeless_recipe(
        "fabricador_circuitos",
        ["dolfinspace:placa_acero"] * 4 + ["minecraft:repeater", "minecraft:diamond"],
        "fabricador_circuitos",
        category="misc",
    )
    write_shapeless_recipe(
        "refineria",
        ["dolfinspace:placa_acero"] * 4
        + ["minecraft:cauldron", "dolfinspace:circuito_basico", "minecraft:blast_furnace"],
        "refineria",
        category="misc",
    )


def generate_oxygen_assets() -> None:
    """Generate oxygen compressor, life-support canisters, damage types, and plant tags."""
    tank_specs = [
        ("chico", (95, 180, 200), 300, "minecraft:glass"),
        ("mediano", (70, 130, 220), 600, "minecraft:copper_ingot"),
        ("grande", (170, 100, 230), 1200, "minecraft:diamond"),
    ]
    for tier, color, capacity, core in tank_specs:
        write_item_model_and_definition(f"tanque_oxigeno_{tier}", color, tank=True)
        write_shaped_recipe(
            f"tanque_oxigeno_{tier}",
            ["PGP", "PGP", "PPP"],
            {"P": "#dolfinspace:placas_tanque", "G": core},
            category="misc",
            count=1,
            components={"minecraft:damage": capacity},
        )

    write_placeholder_png_if_missing(
        "assets/dolfinspace/textures/gui/sprites/container/slot/tank.png",
        (100, 110, 120),
        tank=True,
    )
    write_block_assets("compresor_oxigeno", (80, 110, 120))
    write_block_loot("compresor_oxigeno", legacy_condition=True)

    write_json(
        "data/dolfinspace/damage_type/oxygen.json",
        {
            "message_id": "dolfinspace.oxygen",
            "scaling": "never",
            "exhaustion": 0.0,
            "effects": "drowning",
        },
    )
    write_json(
        "data/minecraft/tags/damage_type/bypasses_armor.json",
        {"replace": False, "values": ["dolfinspace:oxygen"]},
    )
    write_json(
        "data/dolfinspace/tags/item/plantas_compresor.json",
        {
            "replace": False,
            "values": [
                "minecraft:short_grass",
                "minecraft:tall_grass",
                "minecraft:fern",
                "minecraft:large_fern",
                "minecraft:moss_block",
                "minecraft:vine",
                "minecraft:azalea",
                "minecraft:flowering_azalea",
                "minecraft:dandelion",
                "minecraft:poppy",
                "#minecraft:flowers",
                "minecraft:bamboo",
                "minecraft:cactus",
                "minecraft:sugar_cane",
                "minecraft:kelp",
                "minecraft:seagrass",
                "minecraft:crimson_roots",
                "minecraft:warped_roots",
                "minecraft:nether_sprouts",
                "dolfinspace:hongo_bioluminiscente",
            ],
        },
    )
    write_json(
        "data/dolfinspace/tags/item/placas_tanque.json",
        {
            "values": [
                "minecraft:iron_ingot",
                {"id": "dolfinspace:placas_acero", "required": False},
                {"id": "dolfinspace:placa_acero", "required": False},
            ]
        },
    )
    write_json(
        "data/dolfinspace/tags/item/circuitos_compresor.json",
        {
            "values": [
                "minecraft:redstone",
                {"id": "dolfinspace:circuito_basico", "required": False},
            ]
        },
    )
    write_shaped_recipe(
        "compresor_oxigeno",
        ["PPP", "LCL", "PPP"],
        {
            "P": "#dolfinspace:placas_tanque",
            "L": "#minecraft:leaves",
            "C": "#dolfinspace:circuitos_compresor",
        },
        category="misc",
        count=1,
    )


def generate_planet_assets() -> None:
    """Generate Kepler planet blocks, raw ores, worldgen biomes, dimension and noise rules."""
    for name, color in KEPLER_COLORS.items():
        write_block_assets(name, color, cross=(name == "hongo_bioluminiscente"))
        if not name.startswith("mena_"):
            write_block_loot(name, legacy_condition=True)

    items = [
        ("titanio_crudo", (160, 165, 195)),
        ("lingote_titanio", (185, 190, 215)),
        ("hierro_meteorico_crudo", (170, 100, 80)),
        ("lingote_hierro_meteorico", (195, 120, 95)),
        ("cristal_energia", (65, 225, 220)),
    ]
    for item_name, color in items:
        write_item_model_and_definition(item_name, color)

    ore_specs = [
        ("mena_titanio", "titanio_crudo", 9, 12, 65),
        ("mena_hierro_meteorico", "hierro_meteorico_crudo", 12, 10, 75),
        ("mena_cristal_energia", "cristal_energia", 8, 14, 40),
    ]
    for ore, drop, size, count, top in ore_specs:
        write_ore_loot(ore, drop)
        write_feature(
            ore,
            {
                "type": "minecraft:ore",
                "size": size,
                "discard_chance_on_air_exposure": 0.0,
                "targets": [
                    {
                        "target": {
                            "predicate_type": "minecraft:tag_match",
                            "tag": "dolfinspace:terreno_kepler",
                        },
                        "state": f"dolfinspace:{ore}",
                    }
                ],
            },
            [
                {"type": "minecraft:count", "count": count},
                {"type": "minecraft:in_square"},
                {
                    "type": "minecraft:height_range",
                    "height": {
                        "type": "minecraft:uniform",
                        "min_inclusive": {"absolute": -55},
                        "max_inclusive": {"absolute": top},
                    },
                },
                {"type": "minecraft:biome"},
            ],
        )

    for ore in ["titanio", "hierro_meteorico"]:
        for method, ticks in [("smelting", 200), ("blasting", 100)]:
            write_cooking_recipe(
                f"lingote_{ore}_{method}",
                method,
                f"{ore}_crudo",
                f"lingote_{ore}",
                experience=0.7,
                cookingtime=ticks,
            )

    write_shaped_recipe(
        "bloque_cristal_energia",
        ["CC", "CC"],
        {"C": "dolfinspace:cristal_energia"},
        category="misc",
        count=1,
    )
    write_json(
        "data/dolfinspace/tags/item/cristales_energia.json",
        {"replace": False, "values": ["dolfinspace:cristal_energia"]},
    )
    write_json(
        "data/dolfinspace/tags/item/lingotes_titanio.json",
        {"replace": False, "values": ["dolfinspace:lingote_titanio"]},
    )
    write_json(
        "data/dolfinspace/tags/block/terreno_kepler.json",
        {"values": ["dolfinspace:regolito", "dolfinspace:roca_lunar"]},
    )

    tag_specs = [
        (
            "mineable/pickaxe",
            [v for v in KEPLER_COLORS if v not in ["hongo_bioluminiscente", "lodo_acido"]],
        ),
        ("needs_iron_tool", ["mena_titanio", "mena_cristal_energia", "bloque_cristal_energia"]),
        ("needs_stone_tool", ["mena_hierro_meteorico"]),
        ("mineable/shovel", ["lodo_acido"]),
    ]
    for tag, values in tag_specs:
        path = f"data/minecraft/tags/block/{tag}.json"
        target_path = RESOURCES_DIR / path
        prev = read_json(path) if target_path.exists() else {"replace": False, "values": []}
        prev["values"] = list(dict.fromkeys(prev["values"] + [f"dolfinspace:{v}" for v in values]))
        write_json(path, prev)

    write_feature(
        "crystal_spires",
        {
            "type": "minecraft:block_column",
            "direction": "up",
            "allowed_placement": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
            "prioritize_tip": False,
            "layers": [
                {
                    "height": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 5},
                    "provider": {"id": "dolfinspace:bloque_cristal_energia"},
                }
            ],
        },
        surface_placement(7),
    )
    write_feature(
        "glowing_fungi",
        {"type": "minecraft:simple_block", "to_place": {"id": "dolfinspace:hongo_bioluminiscente"}},
        surface_placement(24),
    )
    write_feature(
        "cave_fungi",
        {"type": "minecraft:simple_block", "to_place": {"id": "dolfinspace:hongo_bioluminiscente"}},
        [{"type": "minecraft:count_on_every_layer", "count": 12}, {"type": "minecraft:biome"}],
    )

    palettes = ["regolito", "roca_lunar", "roca_lunar", "lodo_acido", "bloque_cristal_energia"]
    sky = ["#100e22", "#27121b", "#072a38", "#243014", "#080b24"]
    fog = ["#403949", "#503444", "#135563", "#506529", "#171938"]
    particles = ["white_ash", "ash", "end_rod", "spore_blossom_air", "warped_spore"]
    ores = [
        ["mena_titanio"],
        ["mena_hierro_meteorico"],
        ["mena_cristal_energia"],
        ["mena_titanio"],
        ["mena_cristal_energia"],
    ]

    for i, name in enumerate(BIOMES):
        features = [[] for _ in range(11)]
        features[6] = [f"dolfinspace:{v}" for v in ores[i]]
        if i == 2:
            features[9] = ["dolfinspace:crystal_spires", "dolfinspace:glowing_fungi"]
        if i == 4:
            features[9] = ["dolfinspace:cave_fungi"]
        write_json(
            f"data/dolfinspace/worldgen/biome/{name}.json",
            {
                "has_precipitation": False,
                "temperature": 0.2,
                "downfall": 0.0,
                "attributes": {
                    "minecraft:visual/sky_color": sky[i],
                    "minecraft:visual/fog_color": fog[i],
                    "minecraft:visual/water_fog_color": fog[i],
                    "minecraft:visual/ambient_particles": {
                        "modifier": "append",
                        "argument": [
                            {
                                "particle": {"type": f"minecraft:{particles[i]}"},
                                "probability": 0.015 if i != 3 else 0.04,
                            }
                        ],
                    },
                    "minecraft:gameplay/natural_mob_spawns": {
                        "modifier": "overlay",
                        "argument": {"spawn_costs": {}, "spawns_by_category": {"monster": []}},
                    },
                },
                "effects": {"water_color": "#40605a"},
                "carvers": [],
                "features": features,
            },
        )

    climates = []
    for i, name in enumerate(BIOMES):
        climates.append(
            {
                "biome": f"dolfinspace:{name}",
                "parameters": {
                    "temperature": (
                        [-1, 1]
                        if i == 4
                        else [-1, -0.35]
                        if i == 0
                        else [-0.35, 0.1]
                        if i == 1
                        else [0.1, 0.5]
                        if i == 2
                        else [0.5, 1]
                    ),
                    "humidity": [-1, 1],
                    "continentalness": [-1, 1],
                    "erosion": [-1, 1],
                    "weirdness": [-1, 1],
                    "depth": [0.4, 1.5] if i == 4 else [-1, 0.25],
                    "offset": 0,
                },
            }
        )

    write_json(
        "data/dolfinspace/dimension/kepler.json",
        {
            "type": "dolfinspace:kepler",
            "generator": {
                "type": "minecraft:noise",
                "settings": "dolfinspace:kepler",
                "biome_source": {"type": "minecraft:multi_noise", "biomes": climates},
            },
        },
    )

    noise_path = DATA_DIR / "worldgen/noise_settings/kepler.json"
    noise = read_json(noise_path)
    router = noise["noise_router"]
    router["temperature"] = "minecraft:overworld/temperature"
    router["vegetation"] = "minecraft:overworld/vegetation"
    router["depth"] = {
        "type": "minecraft:gradient",
        "axis": "y",
        "from_coordinate": -64,
        "to_coordinate": 64,
        "from_value": 1.0,
        "to_value": 0.0,
    }
    base = router["final_density"]
    if base.get("type") != "minecraft:min":
        router["final_density"] = {
            "type": "minecraft:min",
            "left": base,
            "right": {
                "type": "minecraft:add",
                "left": 0.32,
                "right": {
                    "type": "minecraft:noise",
                    "noise": "minecraft:cave_layer",
                    "xz_scale": 1.5,
                    "y_scale": 2.0,
                },
            },
        }
    write_json(noise_path, noise)

    rule_path = DATA_DIR / "worldgen/material_rule/kepler.json"
    rule = read_json(rule_path)
    bedrock = rule["sequence"][0]
    seq = [bedrock]
    for biome, palette in zip(BIOMES, palettes, strict=True):
        seq.append(
            {
                "type": "minecraft:condition",
                "if_true": {"type": "minecraft:biome", "biome_is": f"dolfinspace:{biome}"},
                "then_run": {
                    "type": "minecraft:condition",
                    "if_true": "minecraft:on_floor",
                    "then_run": {
                        "type": "minecraft:block",
                        "result_state": f"dolfinspace:{palette}",
                    },
                },
            }
        )
    write_json(rule_path, {"type": "minecraft:sequence", "sequence": seq})


def generate_fauna_assets() -> None:
    """Generate Kepler alien mob skins, drops, equipment crafting, and biome spawns."""
    mob_colors = [
        (85, 130, 65),
        (65, 55, 100),
        (135, 100, 75),
        (45, 125, 150),
        (95, 140, 120),
        (110, 185, 190),
        (85, 120, 155),
    ]
    for name, color in zip(MOBS, mob_colors, strict=True):
        write_entity_texture_if_missing(name, color, 64, 64 if name == "zombi_espacial" else 32)
        write_item_model_and_definition(f"{name}_spawn_egg", color)

    write_entity_texture_if_missing("flecha_cristal", (60, 200, 220))

    drops = [
        ("quitina_alienigena", (115, 90, 145)),
        ("diente_gusano", (205, 190, 165)),
        ("nucleo_energia", (70, 210, 235)),
    ]
    for name, color in drops:
        write_item_model_and_definition(name, color)

    mob_drops = [
        "dolfinspace:quitina_alienigena",
        "dolfinspace:quitina_alienigena",
        "dolfinspace:diente_gusano",
        "dolfinspace:nucleo_energia",
        "minecraft:rotten_flesh",
        "minecraft:bone",
        "minecraft:gunpowder",
    ]
    for name, drop in zip(MOBS, mob_drops, strict=True):
        write_entity_loot(name, drop)

    write_shaped_recipe(
        "escudo_quitina",
        ["QCQ", "QQQ", " Q "],
        {"Q": "dolfinspace:quitina_alienigena", "C": "dolfinspace:cristal_energia"},
        result="minecraft:shield",
    )
    write_shaped_recipe(
        "pico_diente",
        ["DDD", " S ", " S "],
        {"D": "dolfinspace:diente_gusano", "S": "minecraft:stick"},
        result="minecraft:iron_pickaxe",
    )
    write_shapeless_recipe(
        "cristales_del_nucleo",
        ["dolfinspace:nucleo_energia"],
        "cristal_energia",
        count=8,
        with_advancement=False,
    )
    write_json(
        "data/dolfinspace/damage_type/parasito.json",
        {"message_id": "dolfinspace.parasito", "scaling": "never", "exhaustion": 0.1},
    )

    for biome in BIOMES:
        p = DATA_DIR / f"worldgen/biome/{biome}.json"
        data = read_json(p)
        spawns = []
        mob_weights = [100 if biome == "kepler_abyssal_caverns" else 35, 20, 12, 6, 18, 15, 8]
        for name, weight in zip(MOBS, mob_weights, strict=True):
            spawns.append(
                {
                    "type": f"dolfinspace:{name}",
                    "weight": weight,
                    "count": {
                        "type": "minecraft:uniform",
                        "min_inclusive": 2 if name == "acechador" else 1,
                        "max_inclusive": 3 if name == "acechador" else 2,
                    },
                }
            )
        data["attributes"]["minecraft:gameplay/natural_mob_spawns"]["argument"][
            "spawns_by_category"
        ]["monster"] = spawns
        write_json(p, data)


def generate_ship_and_sound_assets() -> None:
    """Generate 3D ship models from Java design spec, sounds.json, and catalog preview sheet."""
    designs = ship_art.parse_designs(SHIP_DESIGN_SOURCE.read_text())
    for tier, name in zip(ship_art.TIERS, SHIP_THEMES, strict=True):
        ship_art.write_ship_texture(
            ASSETS_DIR / f"textures/entity/{name}.png", designs[tier], SHIP_THEMES[name]
        )
    ship_art.write_flame_texture(ASSETS_DIR / "textures/entity/ship_flame.png")

    sound_dir = ASSETS_DIR / "sounds"
    sound_dir.mkdir(parents=True, exist_ok=True)
    sounds: dict[str, dict[str, Any]] = {}
    for name, freq, seconds in SOUND_SPECS:
        out = sound_dir / f"{name}.ogg"
        if not out.exists():
            runtime_dir = ROOT / "runtime"
            runtime_dir.mkdir(parents=True, exist_ok=True)
            with tempfile.TemporaryDirectory(dir=runtime_dir) as td:
                wav = Path(td) / "sound.wav"
                rate = 22050
                with wave.open(str(wav), "wb") as w:
                    w.setnchannels(1)
                    w.setsampwidth(2)
                    w.setframerate(rate)
                    samples = []
                    for i in range(int(seconds * rate)):
                        t = i / rate
                        fade = min(1.0, t * 30.0, (seconds - t) * 30.0)
                        mod = 1.0 if name == "cockpit_hum" else 0.7 + 0.3 * math.sin(t * 25)
                        sample = int(
                            6000
                            * fade
                            * mod
                            * (
                                math.sin(2 * math.pi * freq * t)
                                + 0.2 * math.sin(2 * math.pi * freq * 2 * t)
                            )
                        )
                        samples.append(struct.pack("<h", sample))
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
    write_json("assets/dolfinspace/sounds.json", sounds)

    ship_designs = parse_ship_designs()
    for name, elements in ship_designs.items():
        write_json(
            f"assets/dolfinspace/models/item/{name}_hand.json",
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
        write_json(
            f"assets/dolfinspace/items/{name}.json",
            {
                "model": {
                    "type": "minecraft:select",
                    "property": "minecraft:display_context",
                    "cases": [
                        {
                            "when": "gui",
                            "model": {
                                "type": "minecraft:model",
                                "model": f"dolfinspace:item/{name}",
                            },
                        }
                    ],
                    "fallback": {
                        "type": "minecraft:model",
                        "model": f"dolfinspace:item/{name}_hand",
                    },
                }
            },
        )

    generate_catalog_sheet_if_missing()


def parse_ship_designs() -> dict[str, list[dict[str, Any]]]:
    """Convert ShipDesign cuboids into item-model elements with box-UV face mapping."""
    designs = ship_art.parse_designs(SHIP_DESIGN_SOURCE.read_text())
    return {
        name: ship_item_elements(designs[tier])
        for tier, name in zip(ship_art.TIERS, SHIP_THEMES, strict=True)
    }


def ship_item_elements(parts: list[ship_art.Part]) -> list[dict[str, Any]]:
    """Scale parts into block-model space; UVs are in sixteenths of the texture atlas."""
    width, height = ship_art.texture_size(parts)
    elements = []
    for part in parts:
        faces = {}
        for face, (u, v, w, h) in part.faces().items():
            uv = [u * 16 / width, v * 16 / height, (u + w) * 16 / width, (v + h) * 16 / height]
            faces[face] = {"texture": "#hull", "uv": [round(c, 4) for c in uv]}
        elements.append(
            {
                "from": [8 + part.x * 0.32, part.y * 0.32, 8 + part.z * 0.32],
                "to": [
                    8 + (part.x + part.w) * 0.32,
                    (part.y + part.h) * 0.32,
                    8 + (part.z + part.d) * 0.32,
                ],
                "faces": faces,
            }
        )
    return elements


def generate_catalog_sheet_if_missing() -> None:
    """Generate high-level documentation visual catalog sheet if absent."""
    sheet_path = ROOT / "docs/space/issue-32-assets.png"
    if sheet_path.exists():
        return
    designs = ship_art.parse_designs(SHIP_DESIGN_SOURCE.read_text())
    width, height = 1500, 1120
    sheet = Image.new("RGB", (width, height), (14, 23, 35))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    big = font
    draw.text((35, 22), "DOLFIN SPACE / ORIGINAL ASSET CATALOG", font=big, fill=(220, 235, 248))

    for n, (tier, name) in enumerate(zip(["BASICA", "CARGA", "RAPIDA"], SHIP_THEMES, strict=True)):
        parts = [(p.name, p.x, p.y, p.z, p.w, p.h, p.d) for p in designs[tier]]
        ox = 250 + n * 500
        oy = 420
        scale = 3.4
        for part, *coords in sorted(parts, key=lambda p: float(p[3]), reverse=True):
            x, y, z, w, h, depth = (float(c) for c in coords)
            color = SHIP_THEMES[name] if "canopy" not in part else (40, 70, 100)
            faces = [
                ([(x, y, z), (x + w, y, z), (x + w, y + h, z), (x, y + h, z)], 1.0),
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
            ]
            for face, shade in faces:
                poly = [
                    (ox + (vx - vz * 0.55) * scale, oy - (vy - vz * 0.25) * scale)
                    for vx, vy, vz in face
                ]
                draw.polygon(
                    poly, fill=tuple(min(255, int(c * shade)) for c in color), outline=(16, 30, 40)
                )
        draw.text((ox - 150, 80), tier, font=big, fill=SHIP_THEMES[name])

    sheet_path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(sheet_path)


def generate_translations() -> None:
    """Synchronize localization strings across Spanish and English locales with full parity."""
    base_strings = {
        f"item.dolfinspace.{x}": x.replace("_", " ").capitalize() for x in INDUSTRIAL_ITEMS
    }
    base_strings.update(
        {f"block.dolfinspace.{x}": x.replace("_", " ").capitalize() for x in INDUSTRIAL_BLOCKS}
    )
    base_strings.update(
        {
            "block.dolfinspace.compresor_oxigeno": "Compresor de oxígeno",
            "item.dolfinspace.tanque_oxigeno_chico": "Tanque de oxígeno chico",
            "item.dolfinspace.tanque_oxigeno_mediano": "Tanque de oxígeno mediano",
            "item.dolfinspace.tanque_oxigeno_grande": "Tanque de oxígeno grande",
            "death.attack.dolfinspace.oxygen": "%1$s se quedó sin oxígeno",
        }
    )
    base_strings.update(
        {f"block.dolfinspace.{v}": v.replace("_", " ").capitalize() for v in KEPLER_COLORS}
    )
    base_strings.update(
        {
            f"item.dolfinspace.{v}": v.replace("_", " ").capitalize()
            for v in [
                "titanio_crudo",
                "lingote_titanio",
                "hierro_meteorico_crudo",
                "lingote_hierro_meteorico",
                "cristal_energia",
            ]
        }
    )
    biome_spanish = [
        "Llanuras de regolito",
        "Cráteres meteóricos",
        "Bosque de cristal",
        "Pantanos ácidos",
        "Cavernas abisales",
    ]
    base_strings.update(
        {f"biome.dolfinspace.{n}": t for n, t in zip(BIOMES, biome_spanish, strict=True)}
    )
    base_strings.update(
        {
            f"entity.dolfinspace.{name}": name.replace("_", " ").capitalize()
            for name in MOBS + ["proyectil_energia", "flecha_cristal", "nube_acida"]
        }
    )
    base_strings.update(
        {
            f"item.dolfinspace.{name}_spawn_egg": f"Huevo de {name.replace('_', ' ')}"
            for name in MOBS
        }
    )
    base_strings.update(
        {
            "item.dolfinspace.quitina_alienigena": "Quitina alienígena",
            "item.dolfinspace.diente_gusano": "Diente de gusano",
            "item.dolfinspace.nucleo_energia": "Núcleo de energía",
            "death.attack.dolfinspace.parasito": "%1$s fue devorado por un parásito",
            "death.attack.dolfinspace.parasito.player": "%1$s fue devorado por un parásito mientras huía de %2$s",
        }
    )
    update_translations(base_strings)

    locales_data = {
        locale: read_json(f"assets/dolfinspace/lang/{locale}.json") for locale in LOCALES
    }
    for locale, data in locales_data.items():
        for key, (es, en) in UI_TRANSLATIONS.items():
            data[f"space.ui.{key}"] = en if locale == "en_us" else es
        for tier, es, en in [
            ("BASICA", "Básica", "Scout"),
            ("CARGA", "Carga", "Cargo"),
            ("RAPIDA", "Rápida", "Fast"),
        ]:
            data[f"space.tier.{tier}"] = en if locale == "en_us" else es
        data["space.part"] = "%s × %s"
        sound_titles = {
            "cockpit_hum": ("Zumbido de cabina", "Cockpit hum"),
            "check_beep": ("Pitido de control", "Control beep"),
            "fuel_alarm": ("Alarma de combustible", "Low fuel alarm"),
            "engine": ("Motor de nave", "Ship engine"),
            "parasite": ("Parásito", "Parasite"),
        }
        for event, (es, en) in sound_titles.items():
            data[f"space.sound.{event}"] = en if locale == "en_us" else es
        if locale == "en_us":
            for key in list(data.keys()):
                suffix = key.split(".")[-1]
                if suffix in ENGLISH_NAMES and key.startswith(("item.", "block.", "entity.")):
                    data[key] = ENGLISH_NAMES[suffix]
                elif suffix.endswith("_spawn_egg"):
                    base = suffix.removesuffix("_spawn_egg")
                    if base in ENGLISH_NAMES:
                        data[key] = f"{ENGLISH_NAMES[base]} spawn egg"
            data.update(
                {
                    "biome.dolfinspace.kepler_plains": "Regolith plains",
                    "biome.dolfinspace.kepler_craters": "Meteor craters",
                    "biome.dolfinspace.kepler_crystal_forest": "Crystal forest",
                    "biome.dolfinspace.kepler_acid_marshes": "Acid marshes",
                    "biome.dolfinspace.kepler_abyssal_caverns": "Abyssal caverns",
                    "tag.item.dolfinspace.cristales_energia": "Energy crystals",
                    "tag.item.dolfinspace.lingotes_titanio": "Titanium ingots",
                    "tag.item.dolfinspace.placas_tanque": "Tank plates",
                    "tag.item.dolfinspace.circuitos_compresor": "Compressor circuits",
                    "tag.item.dolfinspace.plantas_compresor": "Oxygen compressor plants",
                    "death.attack.dolfinspace.parasito": "%1$s was devoured by a parasite",
                    "death.attack.dolfinspace.parasito.player": "%1$s was devoured by a parasite while escaping %2$s",
                    "death.attack.dolfinspace.oxygen": "%1$s ran out of oxygen",
                    "death.attack.dolfinspace.oxygen.player": "%1$s ran out of oxygen while escaping %2$s",
                    "itemGroup.dolfinspace.space": "Space",
                    "dolfinspace.command.reloaded": "DolfinSpace configuration reloaded (generation %s, warnings: %s).",
                    "dolfinspace.command.info": "DolfinSpace active — dimension: %s, gravity: %s%%, generation: %s.",
                    "dolfinspace.travel.blocked": "You can only travel to Kepler aboard a spaceship.",
                }
            )

    union_keys = {k: v for d in locales_data.values() for k, v in d.items()}
    for locale, data in locales_data.items():
        for k, v in union_keys.items():
            data.setdefault(k, v)
        write_json(f"assets/dolfinspace/lang/{locale}.json", data)


if __name__ == "__main__":
    main()
