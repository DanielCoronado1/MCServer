package com.dolfin.zombies.griefing;

import java.util.Collection;

/**
 * Pure decision table behind {@link BlockGuard#puedeModificar}. No Minecraft types so it is
 * unit-testable: callers adapt the world (dimension, gamerule, claim lookup) through {@link Env}.
 * Order is cheapest first; the claim lookup only runs when every earlier check passed.
 */
public final class GuardDecision {
    private GuardDecision() {}

    /** Why a modification was refused. {@code null} from {@link #decide} means allowed. */
    public enum Reason {
        WORLD("world"),
        GAMERULE("gamerule"),
        CLAIM("claim"),
        CLAIM_ERROR("claim_error"),
        FIRE_TICK("fire_tick"),
        HARDNESS("hardness"),
        UNBREAKABLE("unbreakable"),
        BLACKLIST("blacklist"),
        RATE_LIMIT("rate_limit"),
        DISABLED("disabled");
        public final String id;

        Reason(String id) {
            this.id = id;
        }
    }

    /**
     * Result of a claim lookup. ERROR fails closed: an unreadable claim is treated as protected.
     */
    public enum ClaimStatus {
        UNCLAIMED,
        CLAIMED,
        ERROR
    }

    public interface Env {
        String dimension();

        boolean mobGriefing();

        ClaimStatus claim();
    }

    /**
     * @param checkGamerule false for non-block actions (attacking animals), which only honor worlds
     *     and claims
     */
    public static Reason decide(
            Env env,
            Collection<String> allowedWorlds,
            boolean respetarClaims,
            boolean checkGamerule) {
        if (!allowedWorlds.contains(env.dimension())) return Reason.WORLD;
        if (checkGamerule && !env.mobGriefing()) return Reason.GAMERULE;
        if (respetarClaims) {
            switch (env.claim()) {
                case CLAIMED -> {
                    return Reason.CLAIM;
                }
                case ERROR -> {
                    return Reason.CLAIM_ERROR;
                }
                case UNCLAIMED -> {}
            }
        }
        return null;
    }
}
