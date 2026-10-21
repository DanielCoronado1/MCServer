#!/usr/bin/env python3
"""Regenera las estructuras abandonadas de Dolfin Creepers (issue #21), solo datos.

Uso:  python3 tools/estructuras/generate_estructuras.py
Salida en mods/creepers/src/main/resources/data/dolfincreepers/:
  structure/*.nbt                         plantillas de bloques (DataVersion de Minecraft 26.3)
  worldgen/structure/*.json               estructura jigsaw de una sola pieza
  worldgen/template_pool/*.json           pool con la plantilla
  worldgen/structure_set/*.json           frecuencia (spacing/separation/salt) de cada una
  tags/worldgen/biome/has_structure/*.json biomas donde puede aparecer
  loot_table/chests/*.json                botín ligero del cofre

La frecuencia se ajusta editando `spacing` y `separation` (en chunks) del structure_set; el mundo
ya generado no cambia porque las estructuras solo se crean en chunks nuevos.
"""

from __future__ import annotations

import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent))

from disenos import DISENOS, NAMESPACE, tabla_botin  # noqa: E402
from plantilla import escribir  # noqa: E402

RAIZ = Path(__file__).resolve().parents[2]
DATOS = RAIZ / "mods/creepers/src/main/resources/data" / NAMESPACE


@dataclass(frozen=True)
class Estructura:
    """Parámetros de worldgen de una estructura de una sola pieza.

    `altura` es el start_height del jigsaw; `proyeccion` el heightmap sobre el que se apoya
    (None para estructuras subterráneas a altura absoluta). `spacing` y `separation` son los de
    random_spread, en chunks: una por cuadrícula de `spacing`, al menos `separation` desde el borde.
    """

    nombre: str
    biomas: tuple[str, ...]
    paso: str
    adaptacion: str
    altura: dict[str, Any]
    spacing: int
    separation: int
    salt: int
    proyeccion: str | None = None


ESTRUCTURAS = (
    Estructura(
        nombre="casa_abandonada",
        biomas=(
            "minecraft:plains",
            "minecraft:sunflower_plains",
            "minecraft:meadow",
            "minecraft:forest",
            "minecraft:flower_forest",
            "minecraft:birch_forest",
            "minecraft:old_growth_birch_forest",
            "minecraft:dappled_forest",
            "minecraft:dark_forest",
        ),
        paso="surface_structures",
        adaptacion="beard_thin",
        altura={"absolute": 0},
        proyeccion="WORLD_SURFACE_WG",
        spacing=24,
        separation=8,
        salt=21040101,
    ),
    Estructura(
        nombre="torre_caida",
        biomas=(
            "#minecraft:is_forest",
            "#minecraft:is_taiga",
            "#minecraft:is_savanna",
            "#minecraft:is_hill",
            "#minecraft:is_badlands",
            "minecraft:plains",
            "minecraft:sunflower_plains",
            "minecraft:meadow",
            "minecraft:desert",
            "minecraft:snowy_plains",
        ),
        paso="surface_structures",
        adaptacion="beard_thin",
        altura={"absolute": 0},
        proyeccion="WORLD_SURFACE_WG",
        spacing=32,
        separation=12,
        salt=21040102,
    ),
    Estructura(
        nombre="campamento_minero",
        biomas=("#minecraft:is_overworld",),
        paso="underground_structures",
        adaptacion="none",
        altura={
            "type": "minecraft:uniform",
            "min_inclusive": {"absolute": 1},
            "max_inclusive": {"absolute": 32},
        },
        spacing=20,
        separation=6,
        salt=21040103,
    ),
    Estructura(
        nombre="puesto_hundido",
        biomas=(
            "minecraft:ocean",
            "minecraft:cold_ocean",
            "minecraft:lukewarm_ocean",
            "minecraft:warm_ocean",
            "minecraft:frozen_ocean",
        ),
        paso="surface_structures",
        adaptacion="beard_thin",
        altura={"absolute": 0},
        proyeccion="OCEAN_FLOOR_WG",
        spacing=28,
        separation=10,
        salt=21040104,
    ),
)


def main() -> None:
    """Escribe plantillas, JSON de worldgen y tablas de botín de las cuatro estructuras."""
    for nombre, construir in DISENOS.items():
        escribir(construir(), DATOS / "structure" / f"{nombre}.nbt")
    for estructura in ESTRUCTURAS:
        generar_datos(estructura)
    for nombre, tabla in tablas_de_botin().items():
        escribir_json(DATOS / "loot_table/chests" / f"{nombre}.json", tabla)


def generar_datos(e: Estructura) -> None:
    """Escribe estructura, pool, structure_set y tag de biomas de una estructura."""
    escribir_json(DATOS / "worldgen/structure" / f"{e.nombre}.json", json_estructura(e))
    escribir_json(DATOS / "worldgen/template_pool" / f"{e.nombre}.json", json_pool(e))
    escribir_json(DATOS / "worldgen/structure_set" / f"{e.nombre}.json", json_set(e))
    escribir_json(
        DATOS / "tags/worldgen/biome/has_structure" / f"{e.nombre}.json",
        {"values": list(e.biomas)},
    )


def json_estructura(e: Estructura) -> dict[str, Any]:
    """Estructura jigsaw de una sola pieza (size 1, sin expansión)."""
    datos: dict[str, Any] = {
        "type": "minecraft:jigsaw",
        "biomes": f"#{NAMESPACE}:has_structure/{e.nombre}",
        "max_distance_from_center": 80,
        "size": 1,
        "spawn_overrides": {},
        "start_height": e.altura,
        "start_pool": f"{NAMESPACE}:{e.nombre}",
        "step": e.paso,
        "terrain_adaptation": e.adaptacion,
        "use_expansion_hack": False,
    }
    if e.proyeccion:
        datos["project_start_to_heightmap"] = e.proyeccion
    return datos


def json_pool(e: Estructura) -> dict[str, Any]:
    """Pool con la única plantilla, sin procesadores."""
    return {
        "elements": [
            {
                "element": {
                    "element_type": "minecraft:single_pool_element",
                    "location": f"{NAMESPACE}:{e.nombre}",
                    "processors": "minecraft:empty",
                    "projection": "rigid",
                },
                "weight": 1,
            }
        ],
        "fallback": "minecraft:empty",
    }


def json_set(e: Estructura) -> dict[str, Any]:
    """Structure set propio para poder ajustar la frecuencia de cada estructura por separado."""
    return {
        "placement": {
            "type": "minecraft:random_spread",
            "salt": e.salt,
            "spacing": e.spacing,
            "separation": e.separation,
        },
        "structures": [{"structure": f"{NAMESPACE}:{e.nombre}", "weight": 1}],
    }


def tablas_de_botin() -> dict[str, dict[str, Any]]:
    """Tablas de cofre ligeras: 1-3 tiradas comunes y una tirada extra poco probable."""
    return {
        "casa_abandonada": _tabla(
            "casa_abandonada",
            comunes=[
                ("bread", 12, 1, 3),
                ("apple", 8, 1, 2),
                ("carrot", 8, 1, 3),
                ("potato", 8, 1, 3),
                ("wheat_seeds", 10, 2, 5),
                ("stick", 10, 2, 6),
                ("torch", 8, 2, 5),
                ("paper", 6, 1, 3),
                ("string", 6, 1, 3),
                ("rotten_flesh", 8, 1, 3),
                ("coal", 6, 1, 4),
            ],
            raros=[("iron_ingot", 10, 1, 2), ("emerald", 4, 1, 1), ("name_tag", 3, 1, 1)],
        ),
        "torre_caida": _tabla(
            "torre_caida",
            comunes=[
                ("arrow", 12, 2, 6),
                ("bone", 12, 1, 4),
                ("cobweb", 6, 1, 2),
                ("flint", 8, 1, 3),
                ("coal", 8, 1, 4),
                ("iron_nugget", 8, 2, 6),
                ("gold_nugget", 6, 1, 4),
                ("rotten_flesh", 6, 1, 3),
                ("torch", 6, 2, 4),
            ],
            raros=[("iron_ingot", 10, 1, 3), ("bow", 4, 1, 1), ("iron_sword", 2, 1, 1)],
        ),
        "campamento_minero": _tabla(
            "campamento_minero",
            comunes=[
                ("coal", 14, 2, 6),
                ("raw_iron", 8, 1, 3),
                ("raw_copper", 8, 1, 4),
                ("torch", 10, 3, 8),
                ("rail", 8, 3, 8),
                ("bread", 8, 1, 3),
                ("stick", 6, 2, 5),
                ("redstone", 5, 2, 5),
                ("lapis_lazuli", 4, 2, 5),
            ],
            raros=[("stone_pickaxe", 8, 1, 1), ("iron_pickaxe", 3, 1, 1), ("lantern", 6, 1, 1)],
        ),
        "puesto_hundido": _tabla(
            "puesto_hundido",
            comunes=[
                ("cod", 10, 1, 3),
                ("salmon", 8, 1, 2),
                ("kelp", 8, 2, 6),
                ("dried_kelp", 8, 2, 5),
                ("string", 6, 1, 3),
                ("prismarine_shard", 8, 1, 3),
                ("iron_nugget", 6, 2, 5),
                ("gold_nugget", 5, 1, 3),
                ("sea_pickle", 5, 1, 2),
            ],
            raros=[
                ("nautilus_shell", 4, 1, 1),
                ("prismarine_crystals", 8, 1, 3),
                ("compass", 3, 1, 1),
            ],
        ),
    }


def _tabla(
    nombre: str,
    comunes: list[tuple[str, int, int, int]],
    raros: list[tuple[str, int, int, int]],
) -> dict[str, Any]:
    return {
        "type": "minecraft:chest",
        "pools": [
            {
                "rolls": {"type": "minecraft:uniform", "min": 1, "max": 3},
                "entries": [_entrada(*datos) for datos in comunes],
            },
            {
                "condition": {"type": "minecraft:random_chance", "chance": 0.3},
                "rolls": 1,
                "entries": [_entrada(*datos) for datos in raros],
            },
        ],
        "random_sequence": tabla_botin(nombre),
    }


def _entrada(item: str, peso: int, minimo: int, maximo: int) -> dict[str, Any]:
    cantidad: Any = (
        minimo if minimo == maximo else {"type": "minecraft:uniform", "min": minimo, "max": maximo}
    )
    return {
        "type": "minecraft:item",
        "name": f"minecraft:{item}",
        "weight": peso,
        "modifier": {"type": "minecraft:set_count", "count": cantidad},
    }


def escribir_json(ruta: Path, datos: dict[str, Any]) -> None:
    """Escribe JSON con sangría de dos espacios y salto final, creando las carpetas."""
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(datos, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
