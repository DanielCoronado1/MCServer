package com.dolfin.zombies;

/** Expensive periodic work only; zero means event-driven/O(1) per-tick state, never a scan. */
public final class ZombieTickPolicy {
    private ZombieTickPolicy() {}

    public static int scanPeriod(ZombieKind kind) {
        return switch (kind) {
            case MINERO, GRANJERO, HAMBRIENTO, GRITON, BRUJA, EXPLORADOR -> 20;
            case ABISMO -> 5;
            default -> 0;
        };
    }

    public static boolean scanDue(ZombieKind kind, long tick, int entityId) {
        int period = scanPeriod(kind);
        return period > 0 && Math.floorMod(tick + entityId, period) == 0;
    }

    public static boolean navigationDue(long tick, int entityId) {
        return Math.floorMod(tick + entityId, 20) == 0;
    }
}
