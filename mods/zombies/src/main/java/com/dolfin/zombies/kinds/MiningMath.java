package com.dolfin.zombies.kinds;

import com.dolfin.zombies.griefing.GuardDecision.Reason;

import java.util.List;

/**
 * Pure target-block selection, eligibility and timing for the Minero; offsets are relative to the
 * zombie's feet block.
 */
public final class MiningMath {
    private MiningMath() {}

    /**
     * Ordered candidate offsets {dx,dy,dz} to dig toward a target at (dx,dy,dz) blocks from the
     * feet. The caller mines the first candidate that is solid; at most three positions are
     * inspected per scan.
     */
    public static List<int[]> candidates(int dx, int dy, int dz) {
        int horizontal = Math.max(Math.abs(dx), Math.abs(dz));
        if (horizontal == 0) {
            if (dy >= 2) return List.of(new int[] {0, 2, 0});
            if (dy <= -1) return List.of(new int[] {0, -1, 0});
            return List.of();
        }
        if (horizontal <= 1 && dy >= 2)
            return List.of(new int[] {0, 2, 0}, stepOf(dx, dz, 1), stepOf(dx, dz, 2));
        if (dy >= 1) return List.of(stepOf(dx, dz, 1), stepOf(dx, dz, 2), new int[] {0, 2, 0});
        if (dy <= -1) return List.of(stepOf(dx, dz, 0), stepOf(dx, dz, 1), stepOf(dx, dz, -1));
        return List.of(stepOf(dx, dz, 0), stepOf(dx, dz, 1));
    }

    /** One block along the dominant horizontal axis toward (dx,dz), at height {@code y}. */
    static int[] stepOf(int dx, int dz, int y) {
        return Math.abs(dx) >= Math.abs(dz)
                ? new int[] {Integer.signum(dx), y, 0}
                : new int[] {0, y, Integer.signum(dz)};
    }

    /** Null when the block itself is minable; otherwise the rejection reason. */
    public static Reason checkBlock(
            float hardness,
            float durezaMax,
            boolean neverBreak,
            boolean blacklisted,
            boolean container) {
        if (hardness < 0 || neverBreak) return Reason.UNBREAKABLE;
        if (blacklisted || container) return Reason.BLACKLIST;
        if (hardness > durezaMax) return Reason.HARDNESS;
        return null;
    }

    /**
     * Ticks to break a block: {@code tiempoPorBloque} scales with hardness (dirt 0.5x, stone 1.5x),
     * at least 5.
     */
    public static int breakTicks(float hardness, int tiempoPorBloque) {
        return Math.max(5, (int) Math.ceil(tiempoPorBloque * Math.max(.5f, hardness)));
    }

    /** Crack stage 0..9 for the vanilla destroy-progress packet. */
    public static int crackStage(int progress, int total) {
        if (total <= 0 || progress <= 0) return 0;
        return Math.min(9, progress * 10 / total);
    }
}
