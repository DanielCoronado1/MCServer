package com.dolfin.space.mobs;

import com.dolfin.space.*;
import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.*;

/**
 * Chunk-local, persistent provenance. Post-generation terrain writes include pistons and commands.
 */
public final class TerrainHistory {
    public static final AttachmentType<Set<Long>> PLACED =
            AttachmentRegistry.create(
                    DolfinSpace.id("placed_terrain"),
                    b ->
                            b.initializer(HashSet::new)
                                    .persistent(
                                            Codec.LONG
                                                    .listOf()
                                                    .xmap(HashSet::new, ArrayList::new)));

    private TerrainHistory() {}

    public static boolean terrain(BlockState state) {
        return FaunaRules.natural(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), false);
    }

    public static void changed(LevelChunk chunk, BlockPos pos, BlockState state) {
        if (chunk.getLevel().isClientSide()
                || !KeplerDimension.isKepler(chunk.getLevel().dimension())) return;
        var target = (AttachmentTarget) chunk;
        Set<Long> old = target.getAttachedOrElse(PLACED, Set.of());
        boolean natural = terrain(state);
        if (!natural && !old.contains(pos.asLong())) return;
        var copy = new HashSet<>(old);
        if (natural) copy.add(pos.asLong());
        else copy.remove(pos.asLong());
        target.setAttached(PLACED, copy);
        chunk.markUnsaved();
        DolfinSpace.LOGGER.debug(
                "event=terrain_provenance_changed pos={} placed={} tracked={}",
                pos,
                natural,
                copy.size());
    }

    public static boolean natural(ServerLevel level, BlockPos pos) {
        if (!KeplerDimension.isKepler(level.dimension()) || !level.hasChunkAt(pos)) return false;
        var placed = ((AttachmentTarget) level.getChunkAt(pos)).getAttachedOrElse(PLACED, Set.of());
        return FaunaRules.natural(
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString(),
                placed.contains(pos.asLong()));
    }
}
