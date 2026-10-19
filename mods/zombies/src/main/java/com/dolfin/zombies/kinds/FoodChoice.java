package com.dolfin.zombies.kinds;

/** Picks the nearest valid candidate for the Hambriento's food distraction. */
public final class FoodChoice {
    private FoodChoice() {}

    /** Index of the nearest valid entry within {@code maxDistSq}, or -1. Ties keep the first. */
    public static int nearest(double[] distSq, boolean[] valid, double maxDistSq) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < distSq.length; i++) {
            if (!valid[i] || distSq[i] > maxDistSq) continue;
            if (distSq[i] < bestD) { bestD = distSq[i]; best = i; }
        }
        return best;
    }
}
