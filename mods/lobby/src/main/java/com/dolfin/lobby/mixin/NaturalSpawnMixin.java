package com.dolfin.lobby.mixin;

import com.dolfin.lobby.StationProtection;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnMixin {
    @Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
    private static void spawn(
            ServerLevel world,
            LevelChunk chunk,
            NaturalSpawner.SpawnState state,
            List<MobCategory> categories,
            CallbackInfo ci) {
        if (StationProtection.station(world)) ci.cancel();
    }
}
