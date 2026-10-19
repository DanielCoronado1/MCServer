#!/usr/bin/env python3
"""Headless Fabric #3 integration: fixture probes, live reload, exact nights, restart persistence.

Uses ONLY the explicitly provisioned runtime/zombies-smoke instance. Preserves configs;
world fixtures occupy x=160..180,z=160..180,y=30..90. Logs/results survive any failure.
Visual sky/fog, real player sleep and multiplayer cap acceptance remain manual.
"""

import argparse
import json
import time
import traceback
from pathlib import Path

from tests.manual.servidor import ServidorFabric, buscar_java, verificar_instancia


def parse_args():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--server-dir", type=Path, default=Path("runtime/zombies-smoke"))
    p.add_argument("--java", default=buscar_java())
    return p.parse_args()


def configurar_entorno(config: Path, seventh: int) -> dict:
    general = json.loads((config / "general.json").read_text())
    general["activo"] = True
    general["lunaDeSangre"] = {
        "activo": True,
        "cadaNNoches": 7,
        "probabilidadPorNoche": 1,
        "multiplicadorSpawns": 2.5,
        "soloEspeciales": True,
        "aviso": True,
        "cieloRojo": True,
        "dormirBloqueado": True,
        "recompensaXpExtra": 1.5,
    }
    general["maxZombiesEspecialesPorJugador"] = 12
    (config / "general.json").write_text(json.dumps(general))
    return general


def ejecutar_primera_sesion(
    root: Path, java: str, config: Path, checks: list[dict], seventh: int
) -> None:
    def write(name, value):
        (config / name).write_text(json.dumps(value))

    with ServidorFabric(
        root,
        java=java,
        memoria="2G",
        memoria_inicial="512M",
        log=root / "console-bloodmoon-first.log",
        jvm_args=["-Dfabric.log.disableAnsi=true"],
        comprobaciones=checks,
        prefijo_marcador="BLOODMOON",
    ) as s:
        s.comando_y_esperar(
            "gamerule minecraft:advance_time false", r"advance_time|Incorrect argument"
        )
        s.comando_y_esperar("zombies lunadesangre detener", r"active=false")
        s.comando_y_esperar("forceload add 160 160 180 180", r"force|already marked")
        s.comando_y_esperar("fill 160 30 160 180 90 180 minecraft:air", r"filled|No blocks")
        s.comando_y_esperar("fill 160 49 160 180 49 180 minecraft:stone", r"filled|No blocks")
        s.comando_y_esperar("fillbiome 160 30 160 180 90 180 minecraft:plains", r"biomes|biome")
        s.comando_y_esperar(f"time set {seventh * 24000 + 13000}", r"time|Time")
        time.sleep(0.2)
        s.comando_y_esperar("zombies diagnostico", r"active=true.*lastNight=" + str(seventh + 1))

        # Modificación de peso y parámetros del minero durante luna de sangre.
        minero = json.loads((config / "zombies/minero_zombie.json").read_text())
        minero.update(
            peso=8,
            grupo=[2, 3],
            biomas=["minecraft:plains"],
            biomasExcluidos=[],
            alturaY=[30, 80],
            luzMax=0,
        )
        minero["lunaDeSangre"]["pesoExtra"] = 1.5
        write("zombies/minero_zombie.json", minero)
        s.comando_y_esperar("zombies recargar", r"errors=0")
        time.sleep(0.2)
        s.comando_y_esperar(
            "execute positioned 170 50 170 run zombies inspeccionar minero_zombie",
            r"reason=eligible weight=12 group=2,3 placement=true",
        )
        s.comando_y_esperar(
            "execute positioned 170 81 170 run zombies inspeccionar minero_zombie",
            r"reason=height",
        )
        s.comando_y_esperar(
            "execute positioned 170 50 170 run zombies inspeccionar mimo_zombie",
            r"reason=mimo weight=0",
        )
        s.comando_y_esperar(
            "execute positioned 170 50 170 run zombies inspeccionar congelado_zombie",
            r"reason=biome",
        )
        s.comando_y_esperar(
            "execute positioned 170 50 170 run zombies inspeccionar enano_zombie",
            r"reason=height|reason=dwarf",
        )

        # Exclusión de bioma y peso cero en vivo.
        minero["biomasExcluidos"] = ["minecraft:plains"]
        write("zombies/minero_zombie.json", minero)
        s.comando_y_esperar("zombies recargar", r"errors=0")
        s.comando_y_esperar(
            "execute positioned 170 50 170 run zombies inspeccionar minero_zombie",
            r"reason=biome",
        )
        minero["biomasExcluidos"] = []
        minero["peso"] = 0
        write("zombies/minero_zombie.json", minero)
        s.comando_y_esperar("zombies recargar", r"errors=0")
        s.comando_y_esperar(
            "execute positioned 170 50 170 run zombies inspeccionar minero_zombie",
            r"weight=0 group=2,3",
        )

        # Comprobación de profundidad de agua para el zombi del abismo.
        abyss = json.loads((config / "zombies/abismo_zombie.json").read_text())
        abyss["biomas"] = ["minecraft:plains"]
        abyss["biomasExcluidos"] = []
        write("zombies/abismo_zombie.json", abyss)
        s.comando_y_esperar("zombies recargar", r"errors=0")
        s.comando_y_esperar(
            "execute positioned 170 50 170 run zombies inspeccionar abismo_zombie",
            r"reason=water_depth",
        )
        s.comando_y_esperar("fill 168 50 168 172 53 172 minecraft:water", r"filled")
        s.comando_y_esperar(
            "execute positioned 170 51 170 run zombies inspeccionar abismo_zombie",
            r"reason=water_depth",
        )
        s.comando_y_esperar("fill 168 54 168 172 54 172 minecraft:water", r"filled")
        s.comando_y_esperar(
            "execute positioned 170 51 170 run zombies inspeccionar abismo_zombie",
            r"reason=eligible",
        )

        # Regla de nieve para congelado.
        snow = json.loads((config / "zombies/congelado_zombie.json").read_text())
        snow["biomas"] = ["minecraft:plains", "minecraft:snowy_plains"]
        snow["biomasExcluidos"] = []
        write("zombies/congelado_zombie.json", snow)
        s.comando_y_esperar("zombies recargar", r"errors=0")
        s.comando_y_esperar(
            "execute positioned 175 50 175 run zombies inspeccionar congelado_zombie",
            r"reason=snow",
        )
        s.comando_y_esperar("fillbiome 160 30 160 180 90 180 minecraft:snowy_plains", r"biome")
        s.comando_y_esperar(
            "execute positioned 175 50 175 run zombies inspeccionar congelado_zombie",
            r"reason=eligible",
        )

        # Invocación y comprobación de persistencia con etiquetas.
        s.comando_y_esperar(
            "execute positioned 175 50 175 run zombies invocar bombero_zombie",
            r"Invocados 1/1",
        )
        s.comando_y_esperar(
            "execute positioned 175 50 175 run zombies invocar hambriento_zombie",
            r"Invocados 1/1",
        )
        s.comando_y_esperar(
            "tag @e[type=dolfinzombies:hambriento_zombie] add moon_persist",
            r"Added tag|already",
        )
        s.comando_y_esperar(
            "data get entity @e[type=dolfinzombies:hambriento_zombie,tag=moon_persist,limit=1] Health",
            r"20.0f",
        )
        s.comando_y_esperar("save-all flush", r"Saved the game")


def ejecutar_segunda_sesion(
    root: Path,
    java: str,
    config: Path,
    checks: list[dict],
    seventh: int,
    *,
    general: dict,
) -> None:
    def write(name, value):
        (config / name).write_text(json.dumps(value))

    with ServidorFabric(
        root,
        java=java,
        memoria="2G",
        memoria_inicial="512M",
        log=root / "console-bloodmoon-restart.log",
        jvm_args=["-Dfabric.log.disableAnsi=true"],
        comprobaciones=checks,
        prefijo_marcador="BLOODMOON",
    ) as s:
        s.comando_y_esperar("zombies diagnostico", r"active=true.*lastNight=" + str(seventh + 1))
        s.comando_y_esperar(
            "data get entity @e[type=dolfinzombies:hambriento_zombie,tag=moon_persist,limit=1] Health",
            r"20.0f",
        )
        s.comando_y_esperar(f"time set {(seventh + 1) * 24000 + 1000}", r"time|Time")
        time.sleep(0.2)
        s.comando_y_esperar("zombies diagnostico", r"active=false")
        s.comando_y_esperar(
            "data get entity @e[type=dolfinzombies:hambriento_zombie,tag=moon_persist,limit=1] Fire",
            r"[1-9][0-9]*s",
        )
        s.comando_y_esperar(
            "data get entity @e[type=dolfinzombies:bombero_zombie,limit=1,sort=nearest] Fire",
            r"0s|-[0-9]+s",
        )
        s.comando_y_esperar(f"time set {(seventh + 1) * 24000 + 13000}", r"time|Time")
        time.sleep(0.2)
        s.comando_y_esperar("zombies diagnostico", r"active=false")
        s.comando_y_esperar(f"time set {(seventh + 7) * 24000 + 13000}", r"time|Time")
        time.sleep(0.2)
        s.comando_y_esperar("zombies diagnostico", r"active=true")
        s.comando_y_esperar("zombies lunadesangre detener", r"active=false")
        time.sleep(0.2)
        s.comando_y_esperar("zombies diagnostico", r"active=false")
        s.comando_y_esperar("zombies lunadesangre iniciar", r"active=true")
        general["activo"] = False
        write("general.json", general)
        s.comando_y_esperar("zombies recargar", r"errors=0")
        time.sleep(0.2)
        s.comando_y_esperar("zombies diagnostico", r"active=false")

        report = json.loads((config / "diagnostics/runtime.json").read_text())
        assert "spawnCounters" in report and len(report["recentSpawns"]) <= 32


def main() -> None:
    args = parse_args()
    root = verificar_instancia(args.server_dir)
    config = root / "config/dolfinzombies"
    originals = {f: f.read_bytes() for f in config.rglob("*.json") if "diagnostics" not in f.parts}
    checks: list[dict] = []
    result = {"passed": False, "checks": checks}

    day = int(time.time()) % 100000 + 100000
    seventh = day + (6 - day % 7) % 7

    try:
        general = configurar_entorno(config, seventh)
        ejecutar_primera_sesion(root, args.java, config, checks, seventh)
        ejecutar_segunda_sesion(root, args.java, config, checks, seventh, general=general)
        result["passed"] = True
    except Exception:
        result["error"] = traceback.format_exc()
        raise
    finally:
        for path, raw in originals.items():
            path.write_bytes(raw)
        (root / "bloodmoon-smoke-result.json").write_text(json.dumps(result, indent=2) + "\n")

    print(
        json.dumps(
            {
                "passed": True,
                "checks": len(checks),
                "artifact": str(root / "bloodmoon-smoke-result.json"),
            }
        )
    )


if __name__ == "__main__":
    main()
