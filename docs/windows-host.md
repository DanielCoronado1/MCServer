# Unattended Windows host

The Windows source checkout lives at `C:\Users\emi0x7d1\Projects\MCServer`. Docker Engine runs in the existing **Ubuntu WSL2** distribution. The deployed Linux checkout is `/srv/dolfin`; its runtime is on the distribution's Linux filesystem, rather than a Windows bind mount. The original Windows `runtime/server/` remains a preserved migration copy. After import, the active world is `/srv/dolfin/runtime/crafty/servers/<server-id>/`.

## Startup and recovery

- Windows Task Scheduler task **DolfinServer-WSL** runs at boot with the distribution owner's S4U identity and highest privileges. It requires no saved password or interactive login.
- `deploy/windows/start-crafty.ps1` starts Docker, reconciles the Compose deployment, updates Windows port forwarding to the current WSL address, and keeps WSL alive. It retries after a WSL failure. Task Scheduler also restarts the task if its process fails.
- Docker's systemd service is enabled. Crafty's container uses `restart: always`, with 120 seconds for graceful shutdown.
- The imported Minecraft server has Crafty **auto start** and **crash detection** enabled, using Java 25 and a 2 GB initial / 10 GB maximum heap. Crafty checks crashes periodically and stops retrying after repeated failures, to avoid an endless failure loop. A deliberate stop from Crafty remains stopped until explicitly started or the controller restarts.
- Windows forwards TCP 25565 and 8443 into WSL. Firewall rules allow Minecraft connections and restrict the dashboard to LAN/Tailscale addresses.

Host logs are in the Windows checkout's `runtime/host/startup.log`. Crafty logs and data are in the Linux checkout's `runtime/crafty/`. The host's `.wslconfig` caps WSL at 12 GB with 4 GB swap; AC sleep was already disabled.

## Provisioning another Windows host

Use elevated PowerShell with WSL2 virtualization features enabled and an Ubuntu distribution owned by the intended task account. Enabling features can require a reboot. Set `[boot] systemd=true` in Ubuntu's `/etc/wsl.conf` and restart that distribution. Size WSL memory to fit the server heap and controller while reserving memory for Windows.

Build/install the server and stop it before copying its state. From Ubuntu as root, run `bash /mnt/c/Users/<name>/Projects/MCServer/deploy/windows/provision-wsl.sh /mnt/c/Users/<name>/Projects/MCServer`. The script installs Docker using its official Ubuntu repository and copies the preserved runtime into Linux storage only if no Linux runtime exists. Never overwrite an active managed world with the migration copy.

Then run `python3 /srv/dolfin/deploy/crafty/configure_server.py` as root in Ubuntu. It uses the generated private credentials to import the existing accepted-EULA server and enable autostart/crash detection. It does not print passwords or tokens. The default Java path targets the deployed amd64 image; use `--java` for other architectures. After changing the admin password, configure an existing instance through the dashboard instead of this initial provisioning helper.

Finally, from the Windows checkout in elevated PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/windows/install-startup.ps1
```

The installer registers and starts the boot task, enables IP Helper, and creates the firewall rules. Docker Desktop is not used for this deployment and does not need to start at sign-in. Reboot once to verify the task and both services start without desktop login.

## Operations

Open the dashboard at `https://<Windows-host>:8443`. The generated initial login is in `/srv/dolfin/runtime/crafty/config/default-creds.txt` inside Ubuntu; keep it private and change it through Crafty.

Run Docker commands from Windows with:

```powershell
wsl -d Ubuntu -u root -- docker compose -f /srv/dolfin/deploy/crafty/compose.yaml ps
```

Stop/start Minecraft through Crafty. Before maintenance, disable the boot task and stop it, then stop Crafty with Compose; otherwise the supervisor may bring the stack back up. Re-enable and start the task after maintenance. Use the panel's graceful stop before rebooting Windows so Minecraft saves the world.

To update code, update the Windows checkout and fast-forward the Linux checkout from its Windows origin. For server mod updates, use Python 3.11+ and target the **managed Linux runtime**, not the original import copy. On this Ubuntu 22.04 host, Windows Python 3.13 can run `scripts/install_server.py --server-dir \\wsl.localhost\Ubuntu\srv\dolfin\runtime\crafty\servers\<server-id>` from the Windows checkout after stopping Minecraft. Apply template changes separately.

Back up the complete Linux `runtime/crafty/` for recovery, including controller configuration/database and managed worlds. Keep both checkouts' runtime data out of Git.

References: [Docker Engine on Ubuntu](https://docs.docker.com/engine/install/ubuntu/), [Microsoft WSL systemd configuration](https://learn.microsoft.com/en-us/windows/wsl/systemd), and [Crafty Docker setup](https://docs.craftycontrol.com/pages/getting-started/installation/docker/).
