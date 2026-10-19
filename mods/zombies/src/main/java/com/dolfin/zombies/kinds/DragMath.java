package com.dolfin.zombies.kinds;

/** Vector math for the Ahogado's grab-and-pull. Arrays are {x,y,z}. */
public final class DragMath {
    private DragMath() {}

    /** Horizontal slack: closer than this to the zombie the victim is not pulled sideways. */
    public static final double HOLD_RADIUS = 0.6;

    /** Velocity increment per tick: always down by {@code strength}, sideways toward the zombie at half strength. */
    public static double[] drag(double px, double py, double pz, double zx, double zy, double zz, double strength) {
        double dx = zx - px, dz = zz - pz;
        double h = Math.hypot(dx, dz);
        double sx = 0, sz = 0;
        if (h > HOLD_RADIUS) {
            double pull = strength * 0.5;
            sx = dx / h * pull;
            sz = dz / h * pull;
        }
        return new double[] {sx, -strength, sz};
    }

    /** current + add with every component clamped to [-max, max]. */
    public static double[] addClamped(double[] current, double[] add, double max) {
        double[] r = new double[3];
        for (int i = 0; i < 3; i++) r[i] = Math.max(-max, Math.min(max, current[i] + add[i]));
        return r;
    }
}
