package com.dolfin.creepers.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Previene ConcurrentModificationException copiando la lista de jugadores antes de enviar paquetes
 * de explosion mientras jugadores cambian de dimension o desconectan.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelExplosionMixin {
    @Redirect(
            method =
                    "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/util/random/WeightedList;Lnet/minecraft/core/Holder;)V",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/server/level/ServerLevel;players:Ljava/util/List;"))
    private List<ServerPlayer> dolfin$safePlayerList(ServerLevel instance) {
        return List.copyOf(instance.players());
    }
}
