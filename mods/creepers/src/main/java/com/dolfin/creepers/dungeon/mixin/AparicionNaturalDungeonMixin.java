package com.dolfin.creepers.dungeon.mixin;

import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Sin aparición natural de mobs en la dimensión de dungeons, aunque otro mod añada mobs a su bioma:
 * solo existen los mobs que coloca la dungeon.
 */
@Mixin(NaturalSpawner.class)
public abstract class AparicionNaturalDungeonMixin {
    @Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
    private static void dolfincreepers$sinAparicionNaturalEnDungeons(
            ServerLevel level,
            LevelChunk chunk,
            NaturalSpawner.SpawnState state,
            List<MobCategory> categories,
            CallbackInfo ci) {
        if (DimensionDungeons.es(level)) {
            ci.cancel();
        }
    }
}
