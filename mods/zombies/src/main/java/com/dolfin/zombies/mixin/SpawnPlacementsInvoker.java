package com.dolfin.zombies.mixin;

import net.minecraft.world.entity.*;
import net.minecraft.world.level.levelgen.Heightmap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SpawnPlacements.class)
public interface SpawnPlacementsInvoker {
    @Invoker("register")
    static <T extends Mob> void register(
            EntityType<T> type,
            SpawnPlacementType placement,
            Heightmap.Types height,
            SpawnPlacements.SpawnPredicate<T> predicate) {
        throw new AssertionError();
    }
}
