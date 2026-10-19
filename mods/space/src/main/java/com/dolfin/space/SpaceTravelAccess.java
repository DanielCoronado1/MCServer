package com.dolfin.space;

import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Evaluates and authorizes travel into {@code dolfinspace:kepler}. Direct {@code /tp} and portals
 * are blocked; ship transitions (#28) and administrator {@code /execute in dolfinspace:kepler}
 * commands authorize travel through {@link #runAuthorized}.
 */
public final class SpaceTravelAccess {
    /** Describes the source of a dimension transition or teleport request. */
    public enum TravelMechanism {
        SHIP,
        ADMIN_EXECUTE_IN_KEPLER,
        DIRECT_TP_COMMAND,
        PORTAL,
        OTHER_TELEPORT
    }

    /** Value object describing a travel attempt for pure policy evaluation. */
    public record TravelRequest(
            String fromDimension,
            String toDimension,
            TravelMechanism mechanism,
            boolean isAdmin,
            boolean hasAuthorizedToken) {}

    /** Result of evaluating a travel attempt against Kepler access rules. */
    public record Decision(boolean allowed, String reason) {}

    private static final ThreadLocal<Map<UUID, String>> AUTHORIZED_ENTITIES =
            ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<Boolean> EXECUTE_IN_KEPLER_ACTIVE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private SpaceTravelAccess() {}

    /**
     * Marks that the current command execution chain traversed {@code /execute in
     * dolfinspace:kepler}.
     */
    public static void markExecuteInKeplerActive() {
        EXECUTE_IN_KEPLER_ACTIVE.set(Boolean.TRUE);
    }

    /**
     * Consumes and returns whether {@code /execute in dolfinspace:kepler} is active on this thread.
     */
    public static boolean consumeExecuteInKeplerActive() {
        boolean active = Boolean.TRUE.equals(EXECUTE_IN_KEPLER_ACTIVE.get());
        EXECUTE_IN_KEPLER_ACTIVE.set(Boolean.FALSE);
        return active;
    }

    /**
     * Pure policy evaluation for travel into or out of {@code dolfinspace:kepler}. Unit tests
     * exercise all combinations of mechanism, admin status, and config flags.
     */
    public static Decision evaluateTravel(
            TravelRequest request, SpaceConfig.DimensionSection config) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(config, "config");

        boolean fromKepler = KeplerDimension.isKeplerId(request.fromDimension());
        boolean toKepler = KeplerDimension.isKeplerId(request.toDimension());

        if (!fromKepler && !toKepler) {
            return new Decision(true, "non_kepler_dimension");
        }

        if (request.mechanism() == TravelMechanism.PORTAL) {
            if (config.bloquearPortales()) {
                return new Decision(false, "portal_forbidden");
            }
            return new Decision(true, "portal_allowed_by_config");
        }

        if (!toKepler) {
            return new Decision(true, "leaving_kepler");
        }

        if (request.mechanism() == TravelMechanism.SHIP || request.hasAuthorizedToken()) {
            return new Decision(true, "ship_or_authorized_travel");
        }

        if (request.mechanism() == TravelMechanism.ADMIN_EXECUTE_IN_KEPLER) {
            if (!config.permitirExecuteAdmin()) {
                return new Decision(false, "admin_execute_disabled");
            }
            if (!request.isAdmin()) {
                return new Decision(false, "admin_permission_required");
            }
            return new Decision(true, "admin_execute_in_kepler");
        }

        if (request.mechanism() == TravelMechanism.DIRECT_TP_COMMAND) {
            if (config.bloquearTpDirecto()) {
                return new Decision(false, "direct_tp_forbidden");
            }
            return new Decision(true, "direct_tp_allowed_by_config");
        }

        if (fromKepler) {
            return new Decision(true, "intra_kepler_movement");
        }

        return new Decision(false, "unauthorized_kepler_entry");
    }

    /**
     * Executes {@code action} while granting temporary travel authorization into Kepler for {@code
     * entity} (and its passengers/vehicle). Used by #28 ships and admin {@code /execute in}.
     */
    public static <T> T supplyAuthorized(Entity entity, String reason, Supplier<T> action) {
        Map<UUID, String> map = AUTHORIZED_ENTITIES.get();
        String previous = map.put(entity.getUUID(), reason);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                map.remove(entity.getUUID());
            } else {
                map.put(entity.getUUID(), previous);
            }
        }
    }

    /** Void variant of {@link #supplyAuthorized(Entity, String, Supplier)}. */
    public static void runAuthorized(Entity entity, String reason, Runnable action) {
        supplyAuthorized(
                entity,
                reason,
                () -> {
                    action.run();
                    return null;
                });
    }

    /**
     * Returns true if the entity (or its root vehicle) currently holds a travel authorization
     * token.
     */
    public static boolean isAuthorized(Entity entity) {
        return authorizationReason(entity) != null;
    }

    /**
     * Returns the active authorization reason for the entity or its root vehicle, or {@code null}.
     */
    public static String authorizationReason(Entity entity) {
        if (entity == null) {
            return null;
        }
        Map<UUID, String> map = AUTHORIZED_ENTITIES.get();
        String direct = map.get(entity.getUUID());
        if (direct != null) {
            return direct;
        }
        Entity root = entity.getRootVehicle();
        if (root != null && root != entity) {
            return map.get(root.getUUID());
        }
        return null;
    }
}
