# Crafty Controller

The deployment in `deploy/crafty/compose.yaml` uses the official Crafty 4.11.0 image and persistent bind mounts under ignored `runtime/crafty/`. It publishes HTTPS on 8443 and Minecraft on 25565. AutoModpack uses the Minecraft port with the supplied HOLEPUNCH configuration.

Requires Docker Engine with Compose on a Linux host (including a Linux VM). Crafty's upstream documentation does not support Minecraft world storage on Docker Desktop for Windows; use native Crafty there instead. Follow [official Docker installation](https://docs.craftycontrol.com/pages/getting-started/installation/docker/) and [server import instructions](https://docs.craftycontrol.com/pages/user-guide/server-creation/minecraft/) for platform details.

For the unattended Windows deployment using Docker Engine inside Ubuntu WSL2, see [Windows host setup](windows-host.md).

## First setup

1. Build the custom mods and run `python3 scripts/install_server.py`. Review settings and accept the EULA in `runtime/server/eula.txt`. Stop any directly running server.
2. Start the controller from the repository/release root:

   ```sh
   docker compose -f deploy/crafty/compose.yaml up -d
   docker compose -f deploy/crafty/compose.yaml logs -f
   ```

3. Open `https://localhost:8443` (or the host's address). The initial certificate is self-signed. Find the generated initial credentials in `runtime/crafty/config/default-creds.txt`, then change the password in the panel. Keep this directory private.
4. On the Linux Docker host, run `python3 deploy/crafty/configure_server.py` as root. It reads the private initial credentials, imports `runtime/server/` through Crafty's API using a temporary ZIP, selects Java 25, and enables Minecraft autostart and crash detection. It preserves the existing EULA acceptance and starts the server. For other architectures, pass the image's Java 25 path with `--java`.
5. In the dashboard, verify the imported server uses Java 25, a 2 GB initial / 10 GB maximum heap, and enabled autostart/crash detection. Crafty handles lifecycle; run Java directly rather than the restarting Windows launcher.
6. Start from Crafty and verify the console reaches `Done`, the whitelist is populated, and a player can connect and download the hosted mods.

Only run one copy of the server on port 25565. After import, the active world and configuration are in `runtime/crafty/servers/<server-id>/`; the original `runtime/server/` is a preserved import source. Protect access to the admin panel through your host firewall or private network.

## Updates and backups

Stop the server from Crafty before installing mods into its **managed** directory:

```sh
python3 scripts/install_server.py --server-dir runtime/crafty/servers/<server-id>
```

Then start it from Crafty. Review template changes separately; the installer preserves live settings. Configure world backups in the panel. Back up all of `runtime/crafty/` for controller recovery, including its configuration/database and managed servers. To stop the controller, run `docker compose -f deploy/crafty/compose.yaml down`; bind-mounted data persists.

The image version is pinned for reviewable upgrades. Change the tag deliberately, back up state, then run `docker compose -f deploy/crafty/compose.yaml pull` followed by `up -d`. Verify Java 25 remains available after an upgrade.
