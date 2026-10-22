package com.dolfin.space.travel;

/**
 * Pure motion and feedback curves for the scripted flight. The server samples them every tick, so
 * the client only ever sees smooth, eased positions and never has to predict.
 */
public final class FlightProfile {
    public static final int ASCENT_TICKS = 200;
    public static final int TRANSITION_TICKS = 20;
    public static final int DESCENT_TICKS = 80;

    /** Height above the landing pad where the ship appears after the star transit. */
    public static final double DESCENT_DROP = 20;

    /** Countdown ticks during which the engines pre-ignite before liftoff. */
    public static final int IGNITION_TICKS = 40;

    /**
     * Ascent height above the pad: quadratic ease-in so the ship lifts off gently and accelerates,
     * ending exactly at {@code altitude} on the last tick.
     */
    public static double ascentOffset(double altitude, int ticksLeft) {
        double p = progress(ASCENT_TICKS + 1 - ticksLeft, ASCENT_TICKS);
        return altitude * p * p;
    }

    /**
     * Descent height above the pad: quadratic ease-out (a suicide burn), fast at first and touching
     * down at zero speed when {@code ticksLeft} reaches one.
     */
    public static double descentOffset(int ticksLeft) {
        double p = progress(ticksLeft - 1, DESCENT_TICKS - 1);
        return DESCENT_DROP * p * p;
    }

    /**
     * Engine output in [0, 1] for flames and HUD. Pre-ignition flickers up during the end of the
     * countdown, ascent ramps to full power, descent throttles up as the ground approaches.
     *
     * @param phase journal phase ordinal, or a negative value when idle
     */
    public static float thrust(int phase, int ticks) {
        if (phase < 0 || phase >= FlightJournal.Phase.values().length) return 0;
        return switch (FlightJournal.Phase.values()[phase]) {
            case COUNTDOWN ->
                    ticks > IGNITION_TICKS ? 0 : .35f * (1 - ticks / (float) IGNITION_TICKS);
            case ASCENT -> .6f + .4f * (float) progress(ASCENT_TICKS + 1 - ticks, ASCENT_TICKS / 4);
            case TRANSITION -> 1;
            case DESCENT -> .35f + .5f * (float) (1 - progress(ticks, DESCENT_TICKS));
        };
    }

    /** Vertical speed in blocks per second implied by the curves at a tick, for the HUD. */
    public static double verticalSpeed(double previousY, double y) {
        return Double.isFinite(previousY) && Double.isFinite(y) ? (y - previousY) * 20 : 0;
    }

    /** Countdown beep pitch: steady, then rising over the final three seconds. */
    public static float beepPitch(int ticksLeft) {
        return ticksLeft > 60 ? 1.2f : 2f - ticksLeft / 60f * .8f;
    }

    private static double progress(double done, double total) {
        return total <= 0 ? 1 : Math.clamp(done / total, 0, 1);
    }

    private FlightProfile() {}
}
