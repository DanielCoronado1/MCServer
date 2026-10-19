package com.dolfin.zombies.kinds;

/** Pure boundaries shared by the support mechanics and their tests. */
public final class SupportRules {
    private SupportRules() {}

    public static boolean scanDue(long now, long next) {
        return now >= next;
    }

    public static boolean within(double squared, double radius) {
        return squared <= radius * radius;
    }

    public static boolean healable(
            boolean self, boolean alive, float health, float max, double squared, double radius) {
        return !self && alive && health < max && within(squared, radius);
    }

    public static boolean revealed(
            boolean enabled,
            boolean day,
            int light,
            boolean lightReveals,
            long now,
            long damagedUntil) {
        return !enabled || day || (lightReveals && light >= 12) || now < damagedUntil;
    }
}
