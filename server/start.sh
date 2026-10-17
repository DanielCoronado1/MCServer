#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
exec java -Xms10G -Xmx10G -XX:+UseG1GC -jar fabric-server-launch.jar nogui
