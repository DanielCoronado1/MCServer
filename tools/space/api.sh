#!/bin/zsh
J=/Users/emi0x7d1/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home/bin/javap
C=$HOME/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common-deobf/26.3/minecraft-common-deobf-26.3.jar
L=$HOME/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/26.3/minecraft-clientonly-deobf-26.3.jar
for cls in "$@"; do "$J" -p -cp "$C:$L:$(find $HOME/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api -name '*.jar' | tr '\n' ':')" "$cls"; done
