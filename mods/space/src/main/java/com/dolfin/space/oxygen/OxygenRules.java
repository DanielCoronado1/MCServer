package com.dolfin.space.oxygen;

import java.util.Set;

/** Pure survival rules: seconds are integral, empty tanks never break. */
public final class OxygenRules {
    private static final Set<String> TANKS = Set.of("tanque_oxigeno_chico", "tanque_oxigeno_mediano", "tanque_oxigeno_grande");
    private OxygenRules() {}
    public static boolean isTank(String id) { return TANKS.contains(id); }
    public static boolean consumes(boolean kepler, boolean ship, boolean creative, boolean spectator) {
        return kepler && !ship && !creative && !spectator;
    }
    public static int consume(int remaining) { return Math.max(0, remaining - 1); }
    public static boolean low(int remaining, int capacity, double threshold) {
        return capacity <= 0 || remaining <= capacity * threshold;
    }
    public static int refill(int remaining, int capacity, int seconds) {
        return (int) Math.min(capacity, (long) Math.max(0, remaining) + Math.max(0, seconds));
    }
}
