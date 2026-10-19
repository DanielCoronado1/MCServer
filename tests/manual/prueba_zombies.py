#!/usr/bin/env python3
"""Isolated, single-process Fabric regression smoke test. Never points at production by default.

Provision runtime/zombies-smoke with Fabric 26.3/0.19.5 launcher, server.jar, Fabric API,
and built creepers + zombies JARs. Accept Minecraft's EULA in that instance first.
Artifacts: console-smoke.log, smoke-result.json, logs/latest.log, config diagnostics.
"""

import argparse
import json
import re
import traceback
from pathlib import Path

from tests.manual.servidor import ServidorFabric, buscar_java, verificar_instancia


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-dir", type=Path, default=Path("runtime/zombies-smoke"))
    parser.add_argument("--java", default=buscar_java())
    return parser.parse_args()


def ejecutar_smoke(servidor: ServidorFabric, root: Path) -> None:
    text = servidor.salida
    assert (
        "dolfincreepers" in text and "event=startup registeredEntities=16 registeredEggs=16" in text
    )
    assert not re.search(r"(?i)(mixin.*(?:error|warn|fail)|failed to parse|error loading)", text), (
        text
    )

    servidor.comando_y_esperar(
        "gamerule minecraft:advance_time false", r"advance_time|Incorrect argument"
    )
    servidor.comando_y_esperar("time set midnight", r"time marker|Set the time to")
    servidor.comando_y_esperar(
        "forceload add 0 0", r"force|Force|marked for force loading|already marked"
    )

    config = root / "config/dolfinzombies"
    kinds = sorted(p.stem for p in (config / "zombies").glob("*.json"))
    assert len(kinds) == 16

    for kind in kinds:
        servidor.comando_y_esperar(f"kill @e[type=dolfinzombies:{kind}]", r"Killed|No entit")
    for kind in kinds:
        servidor.comando_y_esperar(
            f"execute positioned 0 70 0 run zombies invocar {kind}", f"Invocados 1/1 {kind}"
        )
        servidor.comando_y_esperar(
            f"loot spawn 0 70 0 loot dolfinzombies:entities/{kind}", r"Dropped|dropped"
        )

    servidor.comando_y_esperar(
        "tag @e[type=dolfinzombies:minero_zombie] add foundation_old", r"Added tag"
    )

    path = config / "zombies/minero_zombie.json"
    raw = json.loads(path.read_text())
    raw["vida"] = 77
    path.write_text(json.dumps(raw))
    servidor.comando_y_esperar("zb recargar", r"Config recargada:.*errors=0")
    servidor.comando_y_esperar(
        "execute positioned 0 70 0 run zombies invocar minero_zombie", r"Invocados 1/1"
    )
    servidor.comando_y_esperar(
        "data get entity @e[type=dolfinzombies:minero_zombie,tag=!foundation_old,limit=1] Health",
        r"77\.0f",
    )
    servidor.comando_y_esperar(
        "data get entity @e[type=dolfinzombies:minero_zombie,tag=foundation_old,limit=1] Health",
        r"24\.0f",
    )

    raw["vida"] = -1
    raw["danio"] = 7
    path.write_text(json.dumps(raw))
    servidor.comando_y_esperar("zombies recargar", r"Config recargada:.*errors=1")
    report = json.loads((config / "diagnostics/last-reload.json").read_text())
    assert report["zombies"]["minero_zombie"]["vida"] == 24
    assert report["zombies"]["minero_zombie"]["danio"] == 7
    assert report["problems"][0]["field"] == "vida"

    path.write_text("{broken")
    servidor.comando_y_esperar("zombies recargar", r"Config recargada:.*errors=1")
    assert (
        json.loads((config / "diagnostics/last-reload.json").read_text())["problems"][0]["field"]
        == "$"
    )

    path.write_text(json.dumps(raw | {"vida": 24}))
    servidor.comando_y_esperar("zombies desactivar minero_zombie", r"activo=false")
    servidor.comando_y_esperar("zombies invocar minero_zombie", r"está desactivado")
    assert json.loads(path.read_text())["activo"] is False
    servidor.comando_y_esperar("zombies activar minero_zombie", r"activo=true")
    servidor.comando_y_esperar("zb info minero_zombie", r"Minero.*efectivoActivo=true")
    servidor.comando_y_esperar("zombies invocar unknown_zombie", r"Zombie desconocido")
    servidor.comando_y_esperar(
        "zombies invocar minero_zombie 65",
        r"not be more than|must not be|Incorrect argument|Invalid integer",
    )

    # Restaura una configuración limpia conservando logs como evidencia.
    path.write_text(json.dumps(raw | {"vida": 24, "danio": 3, "activo": True}))
    servidor.comando_y_esperar("save-all flush", r"Saved the game|Saved the world")


def main():
    args = parse_args()
    root = verificar_instancia(args.server_dir)
    checks = []
    result = {"passed": False, "checks": checks}

    servidor = ServidorFabric(
        root,
        java=args.java,
        memoria="2G",
        memoria_inicial="512M",
        log="console-smoke.log",
        jvm_args=["-Dfabric.log.disableAnsi=true"],
        comprobaciones=checks,
        prefijo_marcador="ZOMBIES_SMOKE",
        timeout_arranque=180.0,
    )

    try:
        servidor.iniciar()
        ejecutar_smoke(servidor, root)
        result["passed"] = True
    except BaseException as error:
        result["error"] = repr(error)
        result["traceback"] = traceback.format_exc()
        result["last_completed_check"] = checks[-1] if checks else None
        result["console_tail"] = "".join(servidor.lineas[-80:])
        raise
    finally:
        servidor.detener(timeout=40.0)
        result["exit_code"] = servidor.codigo_salida
        (root / "smoke-result.json").write_text(json.dumps(result, indent=2))
        print(
            json.dumps(
                {
                    k: v
                    for k, v in result.items()
                    if k not in {"checks", "console_tail", "last_completed_check"}
                },
                indent=2,
            )
        )


if __name__ == "__main__":
    main()
