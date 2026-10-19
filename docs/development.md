# Development

Each custom mod is an independent Gradle project under `mods/<name>/`, with its own wrapper and standard `src/main` / `src/client` source sets. Keep binaries and caches in its ignored `build/` and `.gradle/` directories. Use JDK 25 via `JAVA_HOME`; no machine-specific JDK paths belong in Gradle properties.

## Creepers

`mods/creepers/` adds fire, ice, electric, jumping, mini, multiplying, toxic, giant, and king creepers. Build with `./gradlew build` from that directory. On Windows, `scripts/update_creepers.bat` builds and installs the mod; stop the server first. Deploy through the installer so the server and AutoModpack client copies stay synchronized. Bump `version` in `gradle.properties` when publishing changes.

Generate textures with `python3 tools/generate_creeper_textures.py`. Textures and their preview live in the mod project. Install `requirements-dev.txt` for asset and visual tools.

## Third-party mods

Manage Packwiz metadata from `pack/`:

```sh
packwiz modrinth add <project>
packwiz update <project>
packwiz remove <project>
packwiz refresh
```

Set `side` in each `mods/*.pw.toml`: `server` installs only on the server; `client` only in AutoModpack's hosted pack; `both` in both. AutoModpack itself is installed separately on the server and via the player `.mrpack`, and is excluded from its own hosted pack. CI checks index consistency.

## Validation

Run `python3 -m unittest discover -s tests -p 'test_*.py'` for isolated tooling tests. CI also builds every custom mod and creates release artifacts.

Manual game scripts live in `tests/manual/`:

- `arranque_fabric.py ["command" ...]` starts the **real** `runtime/server/`, executes commands, and stops it. Use only while it is otherwise stopped.
- `prueba_mod_servidor.py` checks variants in `runtime/test-server/` on port 25570.
- `prueba_saltarin.py` measures jumping creeper launches in that test instance.
- `prueba_visual_mod.py` uses `runtime/test-server/` and `runtime/test-client/` and captures variants. It requires Windows desktop access and exclusive use of the machine.
- `hoja.py [directory]` creates a contact sheet from captures.

These manual scripts expect pre-provisioned Fabric test instances; they are not the automated unit suite. Visual automation remains Windows-specific. Configure `JAVA_HOME` for the test JDK. Test scripts can reset the test world. A successful build or release archive does not establish in-game behavior.

## Zombies foundation (#1 / #2)

`mods/zombies/` follows the current `mods/creepers/` layout (the issues' `mods-src/` path predates this repository layout). Build with `JAVA_HOME=<JDK25> mods/zombies/gradlew -p mods/zombies build`. CI builds every `mods/*` project; the release builder includes both custom JARs and the installer synchronizes them to server and AutoModpack clients without a separate mod list.

The foundation registers sixteen independent `Zombie` entity types, matching spawn eggs and datapack loot tables under `dolfinzombies:entities/<id>`. Each loot table currently delegates to vanilla zombie loot. Entity/egg names ship in en_us, es_mx, es_es and es_ar. Rendering uses the vanilla zombie model/texture until #8. Natural spawning and the blood moon event (#3) are documented in `docs/zombies/issue-03.md`. The individual mechanics (#4–#7) and player-health-derived Mimo spawning (#7) are subsequent work; the epic remains open.

The first load creates `config/dolfinzombies/general.json` and `zombies/<id>.json`. Every missing field receives its default; invalid fields fall back independently while valid siblings survive. Malformed JSON falls back for that file without overwriting the original. Configuration is parsed only during startup/reload/toggle, on the server thread. No asynchronous world mutations or block-changing mechanics are introduced.

`/zombies` (`/zb`) exposes `invocar <id> [cantidad 1..64] [jugador]`, `recargar`, `activar|desactivar <id>`, `info <id>`, `lunadesangre iniciar|detener`, `inspeccionar <id>` and `diagnostico`. Console invocation uses its own position unless a player is supplied. Tab completion lists IDs. Reload reports the 17 files and all file/field errors and explains that existing entities retain their stats; toggles atomically save `activo`. Disabled kinds/global configuration reject command summons and discard disabled entities loaded through eggs, vanilla summon, or world loading. Disabling does not retroactively destroy currently loaded entities; it gates subsequent loads/spawns. Abilities expose independent live boolean switches for future mechanics, with Granjero's two destructive abilities disabled by default. `dondeesta` explicitly reports its pending issue (#7) until implemented.

LuckPerms is optional: explicit nodes `dolfinzombies.admin.recargar`, `.admin.invocar`, `.admin.config`, `.admin.luna`, `.info` and `.dondeesta` are checked through its API when installed. An explicit denial overrides the default; unset admin nodes fall back to vanilla operator level 2, while info/location default to everyone. Console uses vanilla permissions. With LuckPerms absent there is no runtime API dependency. Active settings are captured per new entity and saved in NBT alongside the reload generation. Vanilla loads saved attributes/health, so config reloads/restarts do not silently heal or update existing entities. Per-ability toggles are checked against the current config.

Observability and validation:

- Startup logs registered counts, absolute config root, reload generation, permission provider and deferred feature scope.
- Every invalid field logs `event=config_invalid` with file, field, reason and generation. `config/dolfinzombies/diagnostics/last-reload.json` records effective configuration, all fallback reasons, timestamp and duration without altering input files.
- Admin reload, toggle and summon events include actor, kind, count/results, dimension, position and generation. Per-entity spawn and disabled-load events are DEBUG to avoid production log floods; enable DEBUG for `dolfinzombies` when investigating spawn behavior. Entity NBT contains `DolfinZombieSettings` and `DolfinConfigGeneration`, alongside vanilla attributes/health.
- JUnit reports are `mods/zombies/build/reports/tests/test/index.html` and `build/test-results/test/`. Tests cover first-load defaults, per-field fallback, malformed JSON, reload snapshot isolation, immutable reads, global/kind/ability switches, persistence, array/identifier validation and permission resolution.
- `tests/manual/prueba_zombies.py --java <JDK25>/bin/java` drives an isolated Fabric server with both custom mods in a single session: every entity/loot table, reload health preservation, malformed/invalid configuration, toggles and command diagnostics. Provision `runtime/zombies-smoke/` with the Fabric 26.3/0.19.5 launcher named `fabric-server-launch.jar`, Fabric API and the two built custom JARs in `mods/`, and accept the Minecraft EULA there first. Use a dedicated `server.properties` port, `online-mode=false`, `view-distance=2`, `simulation-distance=2` and `pause-when-empty-seconds=0`. Keep this directory separate from production. Artifacts are `console-smoke.log`, `smoke-result.json`, `logs/latest.log` and the reload diagnostic JSON. The harness saves the world and always stops the process on failure; no repeated manual gameplay is needed to inspect a failed assertion.

Headless checks establish server registration and command/config behavior. They do not establish client rendering, permission-provider integration with real online users, AutoModpack transfer or future zombie mechanics.
