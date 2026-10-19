package com.dolfin.zombies.kinds;

import java.util.function.IntPredicate;

/**
 * Depth of the water column through a position, scanning both ways with a hard cap per direction.
 */
public final class WaterColumn {
    private WaterColumn() {}

    /**
     * Contiguous water blocks through {@code y} (inclusive), 0 when {@code y} itself is not water.
     */
    public static int depth(IntPredicate isWater, int y, int minY, int maxY, int capPerDirection) {
        if (y < minY || y > maxY || !isWater.test(y)) return 0;
        int n = 1;
        for (int d = 1; d <= capPerDirection && y + d <= maxY && isWater.test(y + d); d++) n++;
        for (int d = 1; d <= capPerDirection && y - d >= minY && isWater.test(y - d); d++) n++;
        return n;
    }
}
