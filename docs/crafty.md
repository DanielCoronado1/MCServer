# Crafty Controller

The deployment in `deploy/crafty/compose.yaml` uses the official Crafty 4.11.0 image and persistent bind mounts under ignored `runtime/crafty/`. It publishes HTTPS on 8443 and Minecraft on 25565. AutoModpack uses the Minecraft port with the supplied HOLEPUNCH configuration.

Requires Docker Engine with Compose on a Linux host (including a Linux VM). Crafty's upstream documentation does not support Minecraft world storage on Docker Desktop for Windows; use native Crafty there instead. Follow [official Docker installation](https://docs.craftycontrol.com/pages/getting-started/installation/docker/) and [server import instructions](https://docs.craftycontrol.com/pages/user-guide/server-creation/minecraft/) for platform details.

## First setup

1. Build the custom mods and run `python3 scripts/install_server.py`. Review settings and accept the EULA in `runtime/server/eula.txt`. Stop any directly running server.
2. Start the controller from the repository/release root:

   ```sh
   docker compose -f deploy/crafty/compose.yaml up -d
   docker compose -f deploy/crafty/compose.yaml logs -f
   ```

3. Open `https://localhost:8443` (or the host's address). The initial certificate is self-signed. Find the generated initial credentials in `runtime/crafty/config/default-creds.txt`, then change the password in the panel. Keep this directory private.
4. Choose **Create Server → Minecraft Java → Import an Existing Server**, using `/crafty/import/dolfin` as the server directory and `fabric-server-launch.jar` as the executable, port 25565, and a 10 GB heap. This imports a copy into Crafty's managed server directory under `/crafty/servers/<server-id>/`.
5. In the imported server's configuration, select its **Java 25** executable and verify the execution command is `java -Xms10G -Xmx10G -jar fabric-server-launch.jar nogui` with that Java path. Run Java directly; Crafty handles lifecycle, so do not use the restarting Windows launcher.
6. Start from Crafty and verify the console reaches `Done`, the whitelist is populated, and a player can connect and download the hosted mods.

Only run one copy of the server on port 25565. After import, the active world and configuration are in `runtime/crafty/servers/<server-id>/`; the original `runtime/server/` is an import source. Protect access to the admin panel through your host firewall or private network.

## Updates and backups

Stop the server from Crafty before installing mods into its **managed** directory:

```sh
python3 scripts/install_server.py --server-dir runtime/crafty/servers/<server-id>
```

Then start it from Crafty. Review template changes separately; the installer preserves live settings. Configure world backups in the panel. Back up all of `runtime/crafty/` for controller recovery, including its configuration/database and managed servers. To stop the controller, run `docker compose -f deploy/crafty/compose.yaml down`; bind-mounted data persists.

The image version is pinned for reviewable upgrades. Change the tag deliberately, back up state, then run `docker compose -f deploy/crafty/compose.yaml pull` followed by `up -d`. Verify Java 25 remains available after an upgrade.
