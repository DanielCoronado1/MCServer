#!/usr/bin/env bash
# Run as root inside an Ubuntu WSL2 distribution. Stop the source server first.
set -euo pipefail
source_repository=${1:?Usage: provision-wsl.sh /mnt/c/Users/name/Projects/MCServer}
linux_repository=${2:-/srv/dolfin}
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git python3
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
. /etc/os-release
printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu %s stable\n' \
    "$(dpkg --print-architecture)" "${UBUNTU_CODENAME:-$VERSION_CODENAME}" > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
# The source checkout is owned by the Windows user, while this setup runs as root.
for trusted in "$source_repository" "$source_repository/.git"; do
    git config --global --get-all safe.directory | grep -Fx -- "$trusted" >/dev/null || git config --global --add safe.directory "$trusted"
done
if [ ! -d "$linux_repository/.git" ]; then
    git clone --no-hardlinks "$source_repository" "$linux_repository"
fi
mkdir -p "$linux_repository/runtime"
if [ ! -d "$linux_repository/runtime/server" ]; then
    cp -a "$source_repository/runtime/server" "$linux_repository/runtime/server"
fi
cd "$linux_repository"
docker compose -f deploy/crafty/compose.yaml up -d
