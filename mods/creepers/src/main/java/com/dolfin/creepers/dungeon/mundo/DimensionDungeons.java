package com.dolfin.creepers.dungeon.mundo;

import com.dolfin.creepers.DolfinCreepers;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * La dimensión vacía {@code dolfincreepers:dungeons}, definida por datapack dentro del mod ({@code
 * data/dolfincreepers/dimension/dungeons.json}): generador plano sin capas ni estructuras, bioma
 * sin mobs ni precipitación, tiempo fijo. Los mixins de {@code dungeon.mixin} bloquean los portales
 * y la aparición natural de mobs en ella.
 */
public final class DimensionDungeons {
    public static final ResourceKey<Level> NIVEL =
            ResourceKey.create(Registries.DIMENSION, DolfinCreepers.id("dungeons"));

    private DimensionDungeons() {}

    public static boolean es(Level level) {
        return level != null && level.dimension() == NIVEL;
    }

    public static boolean es(ResourceKey<Level> dimension) {
        return NIVEL.equals(dimension);
    }

    /** El nivel cargado; vacío si el datapack del mod no se cargó. */
    public static Optional<ServerLevel> nivel(MinecraftServer server) {
        return Optional.ofNullable(server.getLevel(NIVEL));
    }
}
