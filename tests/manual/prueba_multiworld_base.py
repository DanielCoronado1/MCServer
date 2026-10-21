"""Full installed pack: provision twice, restart, and prove structures/edits survive.

Run only in an isolated installed server: this writes test blocks in three worlds.
The server EULA must already be accepted. No interactive Minecraft client is required.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from tests.manual.servidor import ServidorFabric

WORLDS = ("dolfin:lobby", "dolfin:showcase")


def properties(file: Path) -> dict[str, str]:
    return dict(
        line.split("=", 1)
        for line in file.read_text().splitlines()
        if "=" in line and not line.startswith("#")
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-dir", type=Path, required=True)
    parser.add_argument("--java", required=True, help="Explicit Java 25 binary")
    args = parser.parse_args()
    root = args.server_dir.resolve()
    before = properties(root / "server.properties")
    assert before["server-ip"] == "127.0.0.1", "This destructive fixture must stay isolated"
    config = (
        json.loads((root / "config/dolfin-lobby.json").read_text())
        if (root / "config/dolfin-lobby.json").exists()
        else {}
    )
    assert config.get("lobby", WORLDS[0]) == WORLDS[0]
    assert config.get("showcase", WORLDS[1]) == WORLDS[1]
    checks = []

    def boot(number: int) -> ServidorFabric:
        return ServidorFabric(
            root,
            java=args.java,
            memoria="3G",
            timeout_arranque=600,
            log=f"multiworld-base-{number}.log",
            comprobaciones=checks,
        )

    def command(s: ServidorFabric, value: str, expected: str | None = None) -> str:
        return s.comando_y_esperar(value, expected, timeout=180)

    def load(s: ServidorFabric) -> None:
        for world in (*WORLDS, "minecraft:overworld"):
            command(s, f"execute in {world} run forceload add -48 -32 48 32")
        s.comando("list", espera=2)

    def verify(s: ServidorFabric) -> None:
        for world in WORLDS:
            command(s, f"execute in {world} run difficulty", "Peaceful")
            command(
                s,
                f"execute in {world} if block 0 128 0 minecraft:sea_lantern run say DECK_OK",
                "DECK_OK",
            )
            command(
                s,
                f"execute in {world} if block 48 131 0 minecraft:tinted_glass run say WALL_OK",
                "WALL_OK",
            )
            command(
                s,
                f"execute in {world} if block 1 128 1 minecraft:diamond_block run say EDIT_OK",
                "EDIT_OK",
            )
            command(
                s,
                f"execute in {world} if entity @e[type=minecraft:text_display,tag=dolfin_station_label] run say LABEL_OK",
                "LABEL_OK",
            )
        for world, x, z, expected in (
            (WORLDS[0], -20, -1, "Supervivencia"),
            (WORLDS[0], 20, -1, "Exhibición de mods"),
            (WORLDS[0], 0, 24, "Estación Dolfin"),
            (WORLDS[1], -30, -3, "Ala Creepers"),
            (WORLDS[1], 0, -3, "Ala Zombies"),
            (WORLDS[1], 30, -3, "Ala Space"),
            (WORLDS[1], 0, 25, "Exhibición Dolfin"),
        ):
            command(
                s,
                f"execute in {world} run data get entity @e[type=minecraft:text_display,tag=dolfin_station_label,x={x},y=132,z={z},distance=..1,limit=1] text",
                expected,
            )
        command(
            s,
            "execute in minecraft:overworld if block 4 200 4 minecraft:emerald_block run say SURVIVAL_OK",
            "SURVIVAL_OK",
        )
        assert "event=provision_failed" not in s.salida

    with boot(1) as s:
        command(s, "execute in minecraft:overworld run forceload add 0 0")
        s.comando("list", espera=1)
        command(s, "execute in minecraft:overworld run setblock 4 200 4 minecraft:emerald_block")
        command(s, "dolfinlobby provisionar", "Estación preparada")
        load(s)
        # Modify completed decks, then require provisioning to preserve these edits.
        for world in WORLDS:
            command(s, f"execute in {world} run setblock 1 128 1 minecraft:diamond_block")
        manifest = root / "world/dolfin-lobby/provisioning.json"
        digest = hashlib.sha256(manifest.read_bytes()).hexdigest()
        command(s, "dolfinlobby provisionar", "Estación preparada")
        verify(s)
        command(s, "save-all flush", "Saved")
    assert s.codigo_salida == 0
    for number in (2, 3):
        with boot(number) as s:
            command(s, "execute in dolfin:lobby run say LOBBY_REOPENED", "LOBBY_REOPENED")
            load(s)
            verify(s)
            command(s, "dolfinlobby provisionar", "Estación preparada")
            verify(s)
            assert hashlib.sha256(manifest.read_bytes()).hexdigest() == digest
            # Return the runtime to no forced test chunks before stopping.
            for world in (*WORLDS, "minecraft:overworld"):
                command(s, f"execute in {world} run forceload remove all")
        assert s.codigo_salida == 0
    after = properties(root / "server.properties")
    for key in ("level-name", "gamemode", "difficulty", "level-type", "level-seed"):
        assert before[key] == after[key], key
    report = {
        "passed": True,
        "boots": 3,
        "provisionCalls": 4,
        "worlds": WORLDS,
        "manifestSha256": digest,
        "commands": checks,
        "pending": "Actual player UI, permissions, isolation and session behavior belong to later stages.",
    }
    (root / "multiworld-base-results.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    )
    print(
        f"PASS: 3 full-pack boots, 4 provisions, persistent structures/labels/edits/survival; {root}"
    )


if __name__ == "__main__":
    main()
