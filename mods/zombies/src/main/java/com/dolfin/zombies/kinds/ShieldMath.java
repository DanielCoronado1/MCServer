package com.dolfin.zombies.kinds;

/**
 * Geometry and decision logic of the Escudero's frontal block. No Minecraft types so it is
 * unit-testable.
 */
public final class ShieldMath {
    private ShieldMath() {}

    public enum Decision {
        BLOCK,
        ABILITY_OFF,
        BYPASS,
        NO_SOURCE_POSITION,
        PROJECTILE_IGNORED,
        SHIELD_DISABLED,
        OUTSIDE_CONE
    }

    /**
     * Horizontal angle in degrees [0,180] between the facing direction (Minecraft yaw) and the
     * direction from the zombie to the source. NaN when both coincide horizontally.
     */
    public static double angleBetween(
            double yawDeg, double fromX, double fromZ, double srcX, double srcZ) {
        double dx = srcX - fromX, dz = srcZ - fromZ;
        double len = Math.hypot(dx, dz);
        if (len < 1.0E-6) return Double.NaN;
        double yaw = Math.toRadians(yawDeg);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double dot = (fx * dx + fz * dz) / len;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
    }

    /**
     * {@code coneDeg} is the full aperture (90 means 45 degrees to each side). Boundary inclusive.
     */
    public static boolean inCone(
            double yawDeg, double fromX, double fromZ, double srcX, double srcZ, double coneDeg) {
        if (coneDeg >= 360) return true;
        if (coneDeg <= 0) return false;
        double a = angleBetween(yawDeg, fromX, fromZ, srcX, srcZ);
        return !Double.isNaN(a) && a <= coneDeg / 2 + 1.0E-9;
    }

    public static Decision decide(
            boolean abilityOn,
            boolean bypassesShield,
            boolean sourceKnown,
            boolean projectile,
            boolean blockProjectiles,
            boolean shieldDisabled,
            boolean inCone) {
        if (!abilityOn) return Decision.ABILITY_OFF;
        if (bypassesShield) return Decision.BYPASS;
        if (!sourceKnown) return Decision.NO_SOURCE_POSITION;
        if (projectile && !blockProjectiles) return Decision.PROJECTILE_IGNORED;
        if (shieldDisabled) return Decision.SHIELD_DISABLED;
        if (!inCone) return Decision.OUTSIDE_CONE;
        return Decision.BLOCK;
    }
}
