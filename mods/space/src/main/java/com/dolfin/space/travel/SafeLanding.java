package com.dolfin.space.travel;

import com.dolfin.space.DolfinSpace;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.Optional;
import java.util.function.*;

public final class SafeLanding {
    // 26.3 motion-blocking heightmaps depend on vanilla block tags; planet blocks may not be
    // tagged.
    public static final Heightmap.Types HEIGHTMAP = Heightmap.Types.WORLD_SURFACE;

    public record Cell(boolean solid, boolean empty, boolean fluid, String id) {
        public boolean hazardous() {
            return fluid
                    || id.contains("lava")
                    || id.contains("water")
                    || id.contains("acid")
                    || id.contains("acido")
                    || id.contains("magma")
                    || id.contains("fire")
                    || id.contains("cactus")
                    || id.contains("campfire");
        }
    }

    @FunctionalInterface
    public interface Terrain {
        Cell at(int x, int y, int z);
    }

    public static boolean safe(Terrain terrain, int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                var floor = terrain.at(x + dx, y - 1, z + dz);
                if (!floor.solid() || floor.hazardous()) return false;
                for (int dy = 0; dy < 5; dy++) {
                    var air = terrain.at(x + dx, y + dy, z + dz);
                    if (!air.empty() || air.hazardous()) return false;
                }
            }
        return true;
    }

    public static Optional<BlockPos> search(
            int x, int z, int radius, IntBinaryOperator height, Terrain terrain) {
        for (int r = 0; r <= radius; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int xx = x + dx, zz = z + dz, yy = height.applyAsInt(xx, zz);
                    if (safe(terrain, xx, yy, zz)) return Optional.of(new BlockPos(xx, yy, zz));
                }
        return Optional.empty();
    }

    /** Level.getHeight returns minY for unloaded chunks. Prepare the whole footprint first. */
    public static int preparedHeight(
            int x,
            int z,
            java.util.function.BiConsumer<Integer, Integer> load,
            IntBinaryOperator sample) {
        for (int cx = (x - 2) >> 4; cx <= (x + 2) >> 4; cx++)
            for (int cz = (z - 2) >> 4; cz <= (z + 2) >> 4; cz++) load.accept(cx, cz);
        return sample.applyAsInt(x, z);
    }

    private static int height(ServerLevel level, int x, int z) {
        return preparedHeight(
                x,
                z,
                (cx, cz) -> level.getChunk(cx, cz),
                (xx, zz) -> level.getHeight(HEIGHTMAP, xx, zz));
    }

    public static int emergencyHeight(int x, int z, int minY, int maxY, IntBinaryOperator height) {
        int y = minY + 10;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) y = Math.max(y, height.applyAsInt(x + dx, z + dz) + 4);
        return Math.min(y, maxY - 6);
    }

    private static Cell cell(ServerLevel level, int x, int y, int z) {
        var pos = new BlockPos(x, y, z);
        var s = level.getBlockState(pos);
        return new Cell(
                s.isFaceSturdy(level, pos, Direction.UP),
                s.isAir(),
                !level.getFluidState(pos).isEmpty(),
                BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
    }

    public static boolean surface(ServerLevel l, BlockPos feet) {
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) {
                var floor = cell(l, feet.getX() + x, feet.getY() - 1, feet.getZ() + z);
                if (!floor.solid() || floor.hazardous()) return false;
                for (int y = 0; y < 5; y++) {
                    var above = cell(l, feet.getX() + x, feet.getY() + y, feet.getZ() + z);
                    if (above.hazardous()) return false;
                }
            }
        return clear(l, feet);
    }

    public static boolean clear(ServerLevel l, BlockPos feet) {
        return l.noCollision(
                new AABB(
                        feet.getX() - 1,
                        feet.getY(),
                        feet.getZ() - 1,
                        feet.getX() + 2,
                        feet.getY() + 5,
                        feet.getZ() + 2));
    }

    public static BlockPos find(ServerLevel level, BlockPos near) {
        Terrain terrain = (x, y, z) -> cell(level, x, y, z);
        var found =
                search(
                        near.getX(),
                        near.getZ(),
                        32,
                        (x, z) ->
                                level.getWorldBorder().isWithinBounds(new BlockPos(x, 0, z))
                                        ? height(level, x, z)
                                        : level.getMaxY(),
                        terrain);
        if (found.isPresent()) return found.get();
        // Emergency platform above the local terrain, avoiding destruction of terrain/structures.
        int y =
                emergencyHeight(
                        near.getX(),
                        near.getZ(),
                        level.getMinY(),
                        level.getMaxY(),
                        (x, z) -> height(level, x, z));
        BlockPos feet = new BlockPos(near.getX(), y, near.getZ());
        if (!clear(level, feet))
            throw new IllegalStateException("No safe emergency landing clearance");
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                level.setBlock(feet.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        DolfinSpace.LOGGER.warn(
                "event=ship_landing_platform dimension={} pos={}",
                level.dimension().identifier(),
                feet);
        return feet;
    }

    private SafeLanding() {}
}
