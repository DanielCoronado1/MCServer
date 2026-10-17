# Server operations

Run commands from the repository or extracted release root. Install with `python3 scripts/install_server.py`; use `--server-dir /absolute/path` to target a different runtime, including a Crafty-managed server. Always stop that server first.

Installation seeds missing configuration without overwriting local edits. Apply future template changes manually after reviewing them. It merges the name-only whitelist into Minecraft's runtime JSON; existing players remain. EULA acceptance and operator state are local, never release inputs. On a fresh install, edit `runtime/server/eula.txt` to accept the EULA before starting.

The default is 25 players, whitelist enabled, 10 GB heap, view distance 8, and simulation distance 6. Review these for your host. `online-mode=false` is the existing server policy; change it deliberately alongside player identity data when migrating to authenticated accounts.

AutoModpack serves clients from `automodpack/host-modpack/main/`. Server-only mods stay in `mods/` inside the runtime. Share the certificate fingerprint printed by AutoModpack with players so they can verify the initial connection.

## Releases

Build each project in `mods/`, then run:

```sh
python3 scripts/build_release.py 0.0.0-test
```

`dist/` receives a server ZIP, a client `.mrpack` containing Fabric and AutoModpack download metadata, and `notas.md` release notes. Server ZIPs contain templates, locked Packwiz metadata, compiled custom mods, installer, deployment configuration, and documentation. They never package runtime worlds, operator lists, EULA acceptance, or generated player UUIDs. Third-party mod binaries are downloaded and verified during installation.

Extract updates into the existing repository/release root, stop the server, and rerun the installer. Runtime files remain separate. Back up runtime state before upgrading Minecraft, mods, or Crafty.

GitHub Actions validates Packwiz, builds all custom mods, runs tooling tests, and packages artifacts on pushes and pull requests. Push a `v<version>` tag to publish a GitHub release. Import the player `.mrpack` into Prism Launcher or Modrinth App; connect, verify the server fingerprint, accept the hosted mods, and restart when AutoModpack requests it.

## Migrating the old layout

The former `server-fabric/` runtime is now `runtime/server/`; tracked templates moved to `server/`. With the server stopped, back up and copy existing worlds, mods, configuration, operator list, whitelist, EULA, certificates, and `.dolfin-instalados.json` to `runtime/server/` before installing. Do not overwrite these with templates. `mods-src/` is now `mods/`; `scripts/pruebas/` moved to `tests/manual/`. Installer and release commands are now `scripts/install_server.py` and `scripts/build_release.py`.
