#!/usr/bin/env python3
"""Isolated, single-process Fabric regression smoke test. Never points at production by default.
Provision runtime/zombies-smoke with Fabric 26.3/0.19.5 launcher, server.jar, Fabric API,
and built creepers + zombies JARs. Accept Minecraft's EULA in that instance first.
Artifacts: console-smoke.log, smoke-result.json, logs/latest.log, config diagnostics.
"""

import argparse
import json
import os
import queue
import re
import subprocess
import threading
import time
import traceback
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-dir", type=Path, default=Path("runtime/zombies-smoke"))
    parser.add_argument(
        "--java",
        default=str(Path(os.environ["JAVA_HOME"]) / "bin/java")
        if "JAVA_HOME" in os.environ
        else "java",
    )
    args = parser.parse_args()
    root = args.server_dir.resolve()
    if not (root / "eula.txt").exists() or "eula=true" not in (root / "eula.txt").read_text():
        raise SystemExit(
            "Provision the isolated instance and accept its Minecraft EULA before running."
        )
    if not (root / "fabric-server-launch.jar").exists():
        raise SystemExit("Missing isolated Fabric launcher: " + str(root))
    transcript = []
    lines = queue.Queue()
    checks = []
    log = (root / "console-smoke.log").open("w")
    process = subprocess.Popen(
        [
            args.java,
            "-Xms512M",
            "-Xmx2G",
            "-Dfabric.log.disableAnsi=true",
            "-jar",
            "fabric-server-launch.jar",
            "nogui",
        ],
        cwd=root,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )

    def reader():
        for line in process.stdout:
            transcript.append(line)
            log.write(line)
            log.flush()
            lines.put(line)

    reader_thread = threading.Thread(target=reader, daemon=True)
    reader_thread.start()

    def wait_for(pattern, timeout=90):
        deadline = time.monotonic() + timeout
        seen = []
        while time.monotonic() < deadline:
            if process.poll() is not None and lines.empty():
                raise AssertionError(
                    f"Server exited {process.returncode}; expected {pattern!r}; see {root}/console-smoke.log"
                )
            try:
                line = lines.get(timeout=0.2)
                seen.append(line)
                if re.search(pattern, line):
                    return "".join(seen)
            except queue.Empty:
                pass
        raise AssertionError(f"Timeout waiting for {pattern!r}; see {root}/console-smoke.log")

    def command(value, expected):
        marker = "__ZOMBIES_SMOKE_" + str(len(checks)) + "__"
        process.stdin.write(value + "\nsay " + marker + "\n")
        process.stdin.flush()
        output = wait_for(re.escape(marker))
        checks.append({"command": value, "expected": expected, "output": output})
        assert re.search(expected, output), (
            f"Command {value!r}: expected {expected!r}; actual {output!r}"
        )
        return output

    result = {"passed": False, "checks": checks}
    try:
        wait_for(r"Done \(", 180)
        text = "".join(transcript)
        assert (
            "dolfincreepers" in text
            and "event=startup registeredEntities=16 registeredEggs=16" in text
        )
        assert not re.search(
            r"(?i)(mixin.*(?:error|warn|fail)|failed to parse|error loading)", text
        ), text
        command("gamerule minecraft:advance_time false", r"advance_time|Incorrect argument")
        command("time set midnight", r"time marker|Set the time to")
        command("forceload add 0 0", r"force|Force|marked for force loading|already marked")
        config = root / "config/dolfinzombies"
        assert len(list((config / "zombies").glob("*.json"))) == 16
        kinds = sorted(p.stem for p in (config / "zombies").glob("*.json"))
        for kind in kinds:
            command("kill @e[type=dolfinzombies:" + kind + "]", r"Killed|No entit")
        for kind in kinds:
            command(
                "execute positioned 0 70 0 run zombies invocar " + kind, "Invocados 1/1 " + kind
            )
            command("loot spawn 0 70 0 loot dolfinzombies:entities/" + kind, r"Dropped|dropped")
        command("tag @e[type=dolfinzombies:minero_zombie] add foundation_old", r"Added tag")
        path = config / "zombies/minero_zombie.json"
        raw = json.loads(path.read_text())
        raw["vida"] = 77
        path.write_text(json.dumps(raw))
        command("zb recargar", r"Config recargada:.*errors=0")
        command("execute positioned 0 70 0 run zombies invocar minero_zombie", r"Invocados 1/1")
        command(
            "data get entity @e[type=dolfinzombies:minero_zombie,tag=!foundation_old,limit=1] Health",
            r"77\.0f",
        )
        command(
            "data get entity @e[type=dolfinzombies:minero_zombie,tag=foundation_old,limit=1] Health",
            r"24\.0f",
        )
        raw["vida"] = -1
        raw["danio"] = 7
        path.write_text(json.dumps(raw))
        command("zombies recargar", r"Config recargada:.*errors=1")
        report = json.loads((config / "diagnostics/last-reload.json").read_text())
        assert report["zombies"]["minero_zombie"]["vida"] == 24
        assert report["zombies"]["minero_zombie"]["danio"] == 7
        assert report["problems"][0]["field"] == "vida"
        path.write_text("{broken")
        command("zombies recargar", r"Config recargada:.*errors=1")
        assert (
            json.loads((config / "diagnostics/last-reload.json").read_text())["problems"][0][
                "field"
            ]
            == "$"
        )
        path.write_text(json.dumps(raw | {"vida": 24}))
        command("zombies desactivar minero_zombie", r"activo=false")
        command("zombies invocar minero_zombie", r"está desactivado")
        assert json.loads(path.read_text())["activo"] is False
        command("zombies activar minero_zombie", r"activo=true")
        command("zb info minero_zombie", r"Minero.*efectivoActivo=true")
        command("zombies invocar unknown_zombie", r"Zombie desconocido")
        command(
            "zombies invocar minero_zombie 65",
            r"not be more than|must not be|Incorrect argument|Invalid integer",
        )
        # Restore a pristine health config for subsequent runs while retaining logs as evidence.
        path.write_text(json.dumps(raw | {"vida": 24, "danio": 3, "activo": True}))
        command("save-all flush", r"Saved the game|Saved the world")
        result["passed"] = True
    except BaseException as error:
        result["error"] = repr(error)
        result["traceback"] = traceback.format_exc()
        result["last_completed_check"] = checks[-1] if checks else None
        result["console_tail"] = "".join(transcript[-80:])
        raise
    finally:
        if process.poll() is None:
            process.stdin.write("stop\n")
            process.stdin.flush()
            try:
                process.wait(timeout=40)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        reader_thread.join(timeout=5)
        result["exit_code"] = process.returncode
        (root / "smoke-result.json").write_text(json.dumps(result, indent=2))
        log.close()
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
