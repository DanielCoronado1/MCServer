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
