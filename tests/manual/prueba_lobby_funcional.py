"""Isolated full-pack behavior suite; two headless ServerPlayers and real server events.

Build :mods:lobby:fixtureJar, install the full server, then copy build/fixtures/* into
its mods directory. Never distribute that test JAR. This tests server game state and
command/portal/flight paths; it does not render a Minecraft client or click cockpit UI.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import struct
from pathlib import Path

from tests.manual.servidor import ServidorFabric

PHASES = ("COUNTDOWN", "ASCENT", "TRANSITION", "DESCENT")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-dir", type=Path, required=True)
    parser.add_argument("--java", required=True)
    args = parser.parse_args()
    root = args.server_dir.resolve()
    props = dict(
        line.split("=", 1)
        for line in (root / "server.properties").read_text().splitlines()
        if "=" in line and not line.startswith("#")
    )
    assert props["server-ip"] == "127.0.0.1" and props["online-mode"] == "false"
    assert list((root / "mods").glob("*fixture*.jar")), "Explicit isolated fixture JAR required"
    evidence: list[dict] = []
    logs = []

    def boot(index: int, **kw) -> ServidorFabric:
        log = f"lobby-functional-{index}.log"
        logs.append(log)
        return ServidorFabric(
            root,
            java=args.java,
            memoria="3G",
            timeout_arranque=600,
            log=log,
            comprobaciones=evidence,
            **kw,
        )

    def command(s: ServidorFabric, value: str, expected: str | None = None) -> None:
        s.comando_y_esperar(value, expected, timeout=180)

    def fixture(s: ServidorFabric, step: str) -> None:
        s.comando(f"lobbyfixture {step}", espera=0)
        result = s.esperar(f"FIXTURE_(PASS|FAIL) {step}", timeout=180)
        assert f"FIXTURE_PASS {step}" in result and "FIXTURE_FAIL" not in s.salida, result
        assert "event=session_failed" not in s.salida
        evidence.append({"fixture": step, "passed": True})

    def flight(s: ServidorFabric, dimension: str) -> None:
        before = len(s.salida)
        s.comando("tick sprint 1400t", espera=0)
        s.esperar("Sprint completed", timeout=180)
        # C2ME chunk/entity promotion completes asynchronously; elapsed ticks alone
        # do not establish arrival. Require the actual landing event for this leg.
        pattern = "event=ship_landed.*dimension=" + dimension
        import re

        if not re.search(pattern, s.salida[before:]):
            s.esperar(pattern, timeout=90)
        s.comando("list", espera=0.1)

    with boot(1) as s:
        command(s, "dolfinlobby provisionar", "Mundos preparados")
        command(s, "dolfinlobby instalar", "Rutas abiertas")
        fixture(s, "layout-seed")
        fixture(s, "setup")
        fixture(s, "checks")
        fixture(s, "portals")
        fixture(s, "third-party")
        command(s, "tick rate 200")
        fixture(s, "rocket")
        flight(s, "dolfinspace:kepler")
        fixture(s, "kepler")
        flight(s, "dolfin:showcase")
        fixture(s, "returned")
        fixture(s, "survival-rocket")
        flight(s, "dolfinspace:kepler")
        fixture(s, "survival-kepler")
        flight(s, "minecraft:overworld")
        fixture(s, "survival-returned")
        fixture(s, "survival-death")
        fixture(s, "recovery-pause")
        fixture(s, "dungeon-compat")
        fixture(s, "phases")
        fixture(s, f"prepare-{PHASES[0]}")
    assert s.codigo_salida == 0
    for index, _phase in enumerate(PHASES, 2):
        with boot(index) as s:
            command(s, "tick rate 200")
            fixture(s, "resume")
            if index == len(PHASES) + 1:
                command(s, "dolfinlobby instalar", "Rutas abiertas")
                fixture(s, "layout-verify")
            if index <= len(PHASES):
                fixture(s, f"prepare-{PHASES[index - 1]}")
        assert s.codigo_salida == 0
    # Unknown escrow schema must refuse service and preserve the original bytes.
    bad = root / "world/dolfin-lobby/sessions/00000000-0000-0000-0000-0000000000ff.nbt"
    data = b"\x0a\x00\x00\x03\x00\x06schema" + struct.pack(">i", 99) + b"\x00"
    bad.write_bytes(gzip.compress(data))
    digest = hashlib.sha256(bad.read_bytes()).hexdigest()
    try:
        with boot(6, patron_listo="event=lobby_disabled") as s:
            assert s.proceso is not None
            s.proceso.wait(timeout=90)
            assert "Sesión incompatible" in s.salida
        assert hashlib.sha256(bad.read_bytes()).hexdigest() == digest
    finally:
        bad.unlink()
    results = {
        "passed": True,
        "serverBoots": 6,
        "flightPhases": PHASES,
        "checks": evidence,
        "logs": logs,
        "layer": "Real ServerPlayers, PlayerList login and game events; sink connections; injected phase journals only for failure matrix and restart setup",
        "pending": "Actual client rendering, physical mouse/keyboard use and cockpit packet/UI presentation",
    }
    (root / "lobby-functional-results.json").write_text(
        json.dumps(results, indent=2, ensure_ascii=False) + "\n"
    )
    print(f"PASS: complete server behavior suite, 6 boots; {root}")


if __name__ == "__main__":
    main()
