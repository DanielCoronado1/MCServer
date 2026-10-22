package com.dolfin.space.travel;

import com.dolfin.space.SpaceConfig;

/** Server and UI share mandatory checks. Oxygen is a warning, never a launch veto. */
public final class LaunchRules {
    public record Checklist(
            boolean surface,
            boolean fuel,
            boolean pilot,
            boolean active,
            boolean tankWarning,
            int required) {
        public static final int SURFACE = 1, FUEL = 2, PILOT = 4, ACTIVE = 8, TANK_WARNING = 16;

        public boolean allowed() {
            return surface && fuel && pilot && active;
        }

        /** Compact bitmask for synced entity data; see the flag constants. */
        public int flags() {
            return (surface ? SURFACE : 0)
                    | (fuel ? FUEL : 0)
                    | (pilot ? PILOT : 0)
                    | (active ? ACTIVE : 0)
                    | (tankWarning ? TANK_WARNING : 0);
        }

        /** True when every mandatory flag in {@code flags} is set. */
        public static boolean ready(int flags) {
            return (flags & (SURFACE | FUEL | PILOT | ACTIVE)) == (SURFACE | FUEL | PILOT | ACTIVE);
        }
    }

    public static Checklist check(
            boolean earth,
            boolean surface,
            int fuel,
            boolean pilot,
            boolean active,
            boolean tank,
            SpaceConfig.ShipsSection cfg) {
        int required = required(earth, cfg);
        return new Checklist(surface, fuel >= required, pilot, active, !tank, required);
    }

    /** Fuel percentage needed to launch: a round trip from Earth, one leg from Kepler. */
    public static int required(boolean earth, SpaceConfig.ShipsSection cfg) {
        return earth
                ? Math.max(
                        cfg.porcentajeCombustibleMinimoDespegueTierra(),
                        2 * cfg.porcentajeCombustiblePorViaje())
                : cfg.porcentajeCombustiblePorViaje();
    }

    public static int deduct(int fuel, int cost) {
        if (cost < 0 || fuel < cost) throw new IllegalArgumentException("insufficient fuel");
        return fuel - cost;
    }

    public static double shake(double serverIntensity, double clientScale) {
        if (!Double.isFinite(serverIntensity) || !Double.isFinite(clientScale)) return 0;
        return Math.clamp(serverIntensity, 0, 1) * Math.clamp(clientScale, 0, 1);
    }

    private LaunchRules() {}
}
