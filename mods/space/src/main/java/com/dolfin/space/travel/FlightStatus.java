package com.dolfin.space.travel;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure presentation rules for the pilot HUD, driven only by synced ship data so the client never
 * re-derives server decisions.
 */
public final class FlightStatus {
    /** HUD warnings, each with its {@code space.ui.*} translation suffix. */
    public enum Warning {
        INACTIVE("hud_warn_inactive"),
        FUEL("hud_warn_fuel"),
        SURFACE("hud_warn_surface"),
        TANK("hud_warn_tank");

        public final String key;

        Warning(String key) {
            this.key = key;
        }
    }

    /**
     * Translation suffix for the state line.
     *
     * @param phase synced journal phase ordinal, negative while parked
     * @param flags synced checklist bitmask; zero means not measured yet
     */
    public static String stateKey(int phase, int ticks, int flags) {
        if (phase < 0 || phase >= FlightJournal.Phase.values().length)
            return flags == 0
                    ? "hud_checking"
                    : LaunchRules.Checklist.ready(flags) ? "hud_ready" : "hud_not_ready";
        return switch (FlightJournal.Phase.values()[phase]) {
            case COUNTDOWN ->
                    ticks <= FlightProfile.IGNITION_TICKS ? "hud_ignition" : "hud_countdown";
            case ASCENT -> "hud_ascent";
            case TRANSITION -> "hud_transit";
            case DESCENT -> "hud_descent";
        };
    }

    /**
     * Active warnings, most blocking first. Parked ships report every failed launch check; in
     * flight only the oxygen tank matters.
     */
    public static List<Warning> warnings(int phase, int fuel, int required, int flags) {
        var out = new ArrayList<Warning>();
        if (flags == 0) return out;
        if (phase < 0) {
            if ((flags & LaunchRules.Checklist.ACTIVE) == 0) out.add(Warning.INACTIVE);
            if (fuel < required) out.add(Warning.FUEL);
            if ((flags & LaunchRules.Checklist.SURFACE) == 0) out.add(Warning.SURFACE);
        }
        if ((flags & LaunchRules.Checklist.TANK_WARNING) != 0) out.add(Warning.TANK);
        return out;
    }

    /** True when the flight ends on Kepler: descents land where they are, launches leave. */
    public static boolean boundForKepler(int phase, boolean inKepler) {
        return phase == FlightJournal.Phase.DESCENT.ordinal() ? inKepler : !inKepler;
    }

    /** Whole seconds shown for a countdown, rounding up so "1" is the last number. */
    public static int countdownSeconds(int ticks) {
        return Math.max(0, (ticks + 19) / 20);
    }

    private FlightStatus() {}
}
