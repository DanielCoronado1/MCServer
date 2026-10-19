package com.dolfin.zombies.kinds;

/** Containment test of the Bilioso's death cloud: a vertical cylinder resting on the death position. */
public final class CloudMath {
    private CloudMath() {}

    public static final double BELOW = 1.0, HEIGHT = 2.5;

    public static boolean contains(double cx, double cy, double cz, double radius, double x, double y, double z) {
        double dx = x - cx, dz = z - cz;
        return dx * dx + dz * dz <= radius * radius && y >= cy - BELOW && y <= cy + HEIGHT;
    }

    /** Particle burst size: grows with the radius but is capped so a blood-moon pile of clouds stays cheap. */
    public static int particleCount(double radius) { return (int) Math.max(4, Math.min(24, Math.ceil(radius * 5))); }
}
