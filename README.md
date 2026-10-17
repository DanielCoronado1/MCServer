# DolfinServer

Minecraft **26.3 / Fabric / Java 25** server and custom Fabric mods. Packwiz locks third-party mods; AutoModpack distributes client mods when players connect.

```text
.github/workflows/       Build, validate, and publish releases
server/                  Versioned server configuration and startup templates
mods/creepers/           Dolfin Creepers Gradle project (common + client sources)
pack/                    Packwiz metadata, versions, and download hashes
scripts/                 Server installation, mod updates, and release packaging
deploy/crafty/           Crafty Controller Docker Compose deployment
tests/                   Automated tooling tests and manual game checks
tools/                   Asset generation utilities
docs/                    Operations and development guides
runtime/                 Local server, Crafty data, and test instances (ignored)
dist/                    Release archives (ignored)
```

`server/` is configuration source; `runtime/server/` is the runnable server. Worlds, databases, certificates, player UUIDs, operator lists, credentials, downloads, and logs stay in `runtime/`. Custom mod sources belong in `mods/<mod-name>/`; third-party mod JARs do not belong there.

## Quick start

Requires Python 3.11+ and JDK 25 on `PATH` (set `JAVA_HOME` for Gradle).

1. In a source checkout, build custom mods (skip this for releases containing `custom-mods/`): `cd mods/creepers && ./gradlew build` (`gradlew.bat build` on Windows).
2. From the repository root: `python3 scripts/install_server.py`.
3. Review `runtime/server/server.properties` and accept Minecraft's EULA in `runtime/server/eula.txt`.
4. Run `runtime/server/start.sh` or `runtime/server/start.bat`. The Windows launcher restarts after `stop`; interrupt its countdown to shut down completely.

The installer copies missing templates, verifies third-party download hashes, and installs custom mods for both server and client. It preserves live configuration and removes only obsolete JARs recorded in `.dolfin-instalados.json`. Stop the server before updating. Configuration edits in `server/` must be deliberately applied to an existing runtime.

The default whitelist is [`server/whitelist.txt`](server/whitelist.txt): `emi2k01`, `emi0x7d1`, and `reginatoor`, one name per line, without UUIDs. Installation merges these names into the runtime whitelist and preserves existing players. The supplied configuration uses `online-mode=false`; the installer generates the matching offline UUIDs. For an online-mode server, first run `whitelist add <name>` in its console to resolve profiles into `usercache.json`, then rerun the installer. Restart or run `whitelist reload` after changing the generated whitelist. Removing a name from the template does not revoke existing access; use `whitelist remove <name>` in the console.

For a web administration panel, follow [Crafty setup](docs/crafty.md). Crafty owns server start/stop after import.

## Development and releases

- [Development, Packwiz, and tests](docs/development.md)
- [Server operation and releases](docs/operations.md)
- [Crafty Controller](docs/crafty.md)
- [Unattended Windows host](docs/windows-host.md)

Release archives follow the same `server/`, `pack/`, `scripts/`, `deploy/`, and `runtime/` convention. They contain compiled custom mods in `custom-mods/` instead of source projects. Installer and release tools need no Python dependencies. Asset tools and manual visual checks use `pip install -r requirements-dev.txt`.
