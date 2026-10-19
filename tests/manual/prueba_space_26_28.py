#!/usr/bin/env python3
"""One isolated, headless integration run. Never launches a client or touches the live server.

JAVA_HOME=<JDK25> python3 tests/manual/prueba_space_26_28.py
Writes evidence into ignored mods/space/run/space-smoke.log. Flight is injected after
liftoff: real player boarding, countdown UI and reconnect remain manual acceptance.
"""

import argparse
import json
import os
import subprocess
import threading
import time
from pathlib import Path

from space_nbt import inspect_space_world

ROOT = Path(__file__).resolve().parents[2]
MOD = ROOT / "mods/space"
RUN = MOD / "run"
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument(
    "--inspect", action="store_true", help="read saved NBT only; do not launch a server"
)
parser.add_argument("--world", type=Path, help="world to inspect")
options = parser.parse_args()
if options.inspect:
    world = options.world or max(RUN.glob("space-smoke-world*"), key=lambda p: p.stat().st_mtime)
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
lines = []
ready = threading.Event()
process = subprocess.Popen(
    [
        "./gradlew",
        "runServer",
        "--no-daemon",
        "--no-configuration-cache",
        "--console=plain",
        "--init-script",
        str(init),
        "--args=nogui",
    ],
    cwd=MOD,
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
    text=True,
    start_new_session=True,
)


def reader():
    for line in process.stdout:
        lines.append(line)
        if "Done (" in line:
            ready.set()


threading.Thread(target=reader, daemon=True).start()


def command(text):
    process.stdin.write(text + "\n")
    process.stdin.flush()
    time.sleep(0.35)


try:
    if not ready.wait(180):
        raise RuntimeError("Server startup did not complete")
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
        command(cmd)
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
        command(f"setblock {x} 80 0 dolfinspace:{block}")
        command(f"item replace block {x} 80 0 container.3 with minecraft:coal")
        for slot, item in enumerate(inputs):
            command(f"item replace block {x} 80 0 container.{slot} with {item}")
    time.sleep(2)
    for x in [4, 6, 8]:
        command(f"data get block {x} 80 0 Items")
    command(
        'summon dolfinspace:nave 0.5 81 0.5 {Tags:["space_smoke_ship"],Ship:{owner:"00000000-0000-0000-0000-000000000001",tier:1,fuel:50,home:80L,hasHome:true,passengersAllowed:false},Items:[{Slot:0b,id:"minecraft:diamond",count:3}]}'
    )
    command(
        'data merge entity @e[tag=space_smoke_ship,limit=1] {Flight:{phase:"TRANSITION",ticks:1,startY:81.0d,target:90L,destination:"dolfinspace:kepler",riders:[]}}'
    )
    time.sleep(12)
    command(
        "execute in dolfinspace:kepler run data get entity @e[tag=space_smoke_ship,limit=1] Ship"
    )
    command(
        "execute in dolfinspace:kepler run data get entity @e[tag=space_smoke_ship,limit=1] Items"
    )
    command(
        'execute in dolfinspace:kepler run data merge entity @e[tag=space_smoke_ship,limit=1] {Flight:{phase:"TRANSITION",ticks:1,startY:90.0d,target:81L,destination:"minecraft:overworld",riders:[]}}'
    )
    time.sleep(8)
    command("data get entity @e[tag=space_smoke_ship,limit=1] Items")
    command("setblock 3 80 0 dolfinspace:cargador_combustible")
    command("item replace block 3 80 0 container.0 with dolfinspace:bidon_combustible 2")
    time.sleep(3)
    command("data get block 3 80 0 Items")
    command("data get entity @e[tag=space_smoke_ship,limit=1] Ship")
    command("save-all flush")
finally:
    if process.poll() is None:
        try:
            command("stop")
            process.wait(30)
        except (BrokenPipeError, subprocess.TimeoutExpired):
            import signal

            os.killpg(process.pid, signal.SIGTERM)
            process.wait(20)
    (RUN / "space-smoke.log").write_text("".join(lines))

log = "".join(lines)
saved = inspect_space_world(RUN / world_name)
checks = {
    **saved,
    "startup": "Done (" in log,
    "kepler_arrival": any(
        "event=ship_landed" in line and "dimension=dolfinspace:kepler" in line for line in lines
    ),
    "overworld_descent": any(
        "event=ship_landed" in line and "dimension=minecraft:overworld" in line for line in lines
    ),
    "round_trip": all(
        any("event=ship_landed" in line and f"dimension={dimension}" in line for line in lines)
        for dimension in ["dolfinspace:kepler", "minecraft:overworld"]
    ),
    "cargo": 'id: "minecraft:diamond"' in log,
    "refueling": 'id: "dolfinspace:bidon_vacio"' in log and "fuel: 100" in log,
    "clean_exit": process.returncode == 0,
    "no_errors": not any("/ERROR]" in line or "event=ship_flight_wait" in line for line in lines),
}
(RUN / "space-smoke-report.json").write_text(
    json.dumps({"world": str(RUN / world_name), "checks": checks}, indent=2) + "\n"
)
print(json.dumps(checks, indent=2))
print(f"Evidence: {RUN / 'space-smoke.log'}")
if not all(checks.values()):
    raise SystemExit(1)
