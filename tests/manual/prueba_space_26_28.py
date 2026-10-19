#!/usr/bin/env python3
"""One isolated, headless integration run. Never launches a client or touches the live server.

JAVA_HOME=<JDK25> python3 tests/manual/prueba_space_26_28.py
Writes evidence into ignored mods/space/run/space-smoke.log. Flight is injected after
liftoff: real player boarding, countdown UI and reconnect remain manual acceptance.
"""

import argparse
import json
import time
from pathlib import Path

from space_nbt import inspect_space_world

from tests.manual.servidor import ServidorFabric

ROOT = Path(__file__).resolve().parents[2]
MOD = ROOT / "mods/space"
RUN = MOD / "run"


def ejecutar_comandos_espacio(servidor: ServidorFabric, world_name: str) -> None:
    config = RUN / "config/dolfin-space.json"
    data = json.loads(config.read_text())
    for name in ["ticksCompresor", "ticksFabricadorCircuitos", "ticksRefineria"]:
        data["maquinas"][name] = 20
    config.write_text(json.dumps(data))

    for cmd in [
        "dolfinspace recargar",
        "forceload add 0 0 15 15",
        "fill -2 80 -2 2 80 2 dolfinspace:bloque_plataforma",
    ]:
        servidor.comando(cmd, espera=0.35)
    time.sleep(2)

    for x, block, inputs in [
        (4, "compresor", ["dolfinspace:lingote_acero 2"]),
        (
            6,
            "fabricador_circuitos",
            ["minecraft:redstone", "minecraft:quartz", "dolfinspace:placa_oro"],
        ),
        (
            8,
            "refineria",
            ["minecraft:lava_bucket", "minecraft:blaze_powder", "dolfinspace:bidon_vacio"],
        ),
    ]:
        servidor.comando(f"setblock {x} 80 0 dolfinspace:{block}", espera=0.35)
        servidor.comando(
            f"item replace block {x} 80 0 container.3 with minecraft:coal", espera=0.35
        )
        for slot, item in enumerate(inputs):
            servidor.comando(
                f"item replace block {x} 80 0 container.{slot} with {item}", espera=0.35
            )
    time.sleep(2)

    for x in [4, 6, 8]:
        servidor.comando(f"data get block {x} 80 0 Items", espera=0.35)

    servidor.comando(
        'summon dolfinspace:nave 0.5 81 0.5 {Tags:["space_smoke_ship"],Ship:{owner:"00000000-0000-0000-0000-000000000001",tier:1,fuel:50,home:80L,hasHome:true,passengersAllowed:false},Items:[{Slot:0b,id:"minecraft:diamond",count:3}]}',
        espera=0.35,
    )
    servidor.comando(
        'data merge entity @e[tag=space_smoke_ship,limit=1] {Flight:{phase:"TRANSITION",ticks:1,startY:81.0d,target:90L,destination:"dolfinspace:kepler",riders:[]}}',
        espera=0.35,
    )
    time.sleep(12)

    servidor.comando(
        "execute in dolfinspace:kepler run data get entity @e[tag=space_smoke_ship,limit=1] Ship",
        espera=0.35,
    )
    servidor.comando(
        "execute in dolfinspace:kepler run data get entity @e[tag=space_smoke_ship,limit=1] Items",
        espera=0.35,
    )
    servidor.comando(
        'execute in dolfinspace:kepler run data merge entity @e[tag=space_smoke_ship,limit=1] {Flight:{phase:"TRANSITION",ticks:1,startY:90.0d,target:81L,destination:"minecraft:overworld",riders:[]}}',
        espera=0.35,
    )
    time.sleep(8)

    servidor.comando("data get entity @e[tag=space_smoke_ship,limit=1] Items", espera=0.35)
    servidor.comando("setblock 3 80 0 dolfinspace:cargador_combustible", espera=0.35)
    servidor.comando(
        "item replace block 3 80 0 container.0 with dolfinspace:bidon_combustible 2",
        espera=0.35,
    )
    time.sleep(3)
    servidor.comando("data get block 3 80 0 Items", espera=0.35)
    servidor.comando("data get entity @e[tag=space_smoke_ship,limit=1] Ship", espera=0.35)
    servidor.comando("save-all flush", espera=0.35)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--inspect", action="store_true", help="read saved NBT only; do not launch a server"
    )
    parser.add_argument("--world", type=Path, help="world to inspect")
    options = parser.parse_args()

    if options.inspect:
        world = options.world or max(
            RUN.glob("space-smoke-world*"), key=lambda p: p.stat().st_mtime
        )
        checks = inspect_space_world(world)
        print(json.dumps(checks, indent=2))
        raise SystemExit(0 if all(checks.values()) else 1)
    if options.world:
        parser.error("--world is only supported with --inspect")

    RUN.mkdir(exist_ok=True)
    world_name = f"space-smoke-world-{time.time_ns()}"
    (RUN / "eula.txt").write_text("eula=true\n")
    (RUN / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\ngenerate-structures=false\nlevel-name={world_name}\nmax-tick-time=120000\n"
    )
    init = RUN / "space-smoke.gradle"
    init.write_text(
        'allprojects { tasks.withType(JavaExec).configureEach { standardInput = System.in; maxHeapSize = "1G" } }\n'
    )

    cmd = [
        "./gradlew",
        "runServer",
        "--no-daemon",
        "--no-configuration-cache",
        "--console=plain",
        "--init-script",
        str(init),
        "--args=nogui",
    ]

    with ServidorFabric(
        RUN,
        comando_arranque=cmd,
        cwd=MOD,
        log=RUN / "space-smoke.log",
        timeout_arranque=180.0,
        crear_grupo_proceso=True,
    ) as servidor:
        ejecutar_comandos_espacio(servidor, world_name)

    log = servidor.salida
    saved = inspect_space_world(RUN / world_name)
    checks = {
        **saved,
        "startup": "Done (" in log,
        "kepler_arrival": any(
            "event=ship_landed" in line and "dimension=dolfinspace:kepler" in line
            for line in servidor.lineas
        ),
        "overworld_descent": any(
            "event=ship_landed" in line and "dimension=minecraft:overworld" in line
            for line in servidor.lineas
        ),
        "round_trip": all(
            any(
                "event=ship_landed" in line and f"dimension={dimension}" in line
                for line in servidor.lineas
            )
            for dimension in ["dolfinspace:kepler", "minecraft:overworld"]
        ),
        "cargo": 'id: "minecraft:diamond"' in log,
        "refueling": 'id: "dolfinspace:bidon_vacio"' in log and "fuel: 100" in log,
        "clean_exit": servidor.codigo_salida == 0,
        "no_errors": not any(
            "/ERROR]" in line or "event=ship_flight_wait" in line for line in servidor.lineas
        ),
    }

    (RUN / "space-smoke-report.json").write_text(
        json.dumps({"world": str(RUN / world_name), "checks": checks}, indent=2) + "\n"
    )
    print(json.dumps(checks, indent=2))
    print(f"Evidence: {RUN / 'space-smoke.log'}")
    if not all(checks.values()):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
