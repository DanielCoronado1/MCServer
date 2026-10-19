package com.dolfin.zombies.kinds;

/** Release decision of the Ahogado's grab. Pure: the entity code only gathers the inputs. */
public final class GrabRules {
    private GrabRules() {}

    public static final String DURATION = "duration";
    public static final String ABILITY_OFF = "ability_off";
    public static final String ZOMBIE_DEAD = "zombie_dead";
    public static final String ZOMBIE_OUT_OF_WATER = "zombie_out_of_water";
    public static final String TARGET_DEAD = "target_dead";
    public static final String TARGET_GONE = "target_gone";
    public static final String TARGET_INVALID = "target_invalid";
    public static final String TARGET_LEFT_WATER = "target_left_water";
    public static final String TOO_FAR = "too_far";
    public static final String HIT_BY_TARGET = "hit_by_target";
    public static final String DAMAGE = "damage";

    /** Per-tick facts about a running grab. */
    public record Inputs(int ticksLeft, boolean abilityEnabled, boolean zombieAlive, boolean zombieInWater,
                         boolean targetPresent, boolean targetAlive, boolean targetValid, boolean targetInWater,
                         double distanceSq, double maxDistanceSq) {}

    /** Reason to release now, or {@code null} to keep holding. Ordered from most to least decisive. */
    public static String releaseReason(Inputs i) {
        if (!i.zombieAlive()) return ZOMBIE_DEAD;
        if (!i.targetPresent()) return TARGET_GONE;
        if (!i.targetAlive()) return TARGET_DEAD;
        if (!i.targetValid()) return TARGET_INVALID;
        if (!i.abilityEnabled()) return ABILITY_OFF;
        if (!i.zombieInWater()) return ZOMBIE_OUT_OF_WATER;
        if (!i.targetInWater()) return TARGET_LEFT_WATER;
        if (i.distanceSq() > i.maxDistanceSq()) return TOO_FAR;
        if (i.ticksLeft() <= 0) return DURATION;
        return null;
    }

    /** Release caused by a damage event on the zombie: a hit from the victim or any hit of at least {@code threshold}. */
    public static String damageReason(boolean fromVictim, double amount, double threshold) {
        if (fromVictim) return HIT_BY_TARGET;
        if (amount >= threshold) return DAMAGE;
        return null;
    }
}
