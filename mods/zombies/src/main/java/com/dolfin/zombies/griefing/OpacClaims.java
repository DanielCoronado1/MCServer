package com.dolfin.zombies.griefing;

import com.dolfin.zombies.DolfinZombies;
import com.dolfin.zombies.griefing.GuardDecision.ClaimStatus;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;

/**
 * Optional Open Parties and Claims bridge, by reflection so OPAC stays a soft dependency. Verified
 * against open-parties-and-claims-fabric-26.3-0.32.8: {@code
 * OpenPACServerAPI.get(server).getServerClaimsManager().get(Identifier dim, BlockPos)} returns
 * {@code null} for wilderness and a claim object for any claimed chunk (player or server claim).
 */
final class OpacClaims {
    private OpacClaims() {}

    enum State {
        PRESENT,
        ABSENT,
        ERROR
    }

    private static State state = State.ABSENT;
    private static Method apiGet, claimsManager, claimAt;
    private static boolean runtimeErrorLogged;

    static State init() {
        if (!FabricLoader.getInstance().isModLoaded("openpartiesandclaims")) {
            state = State.ABSENT;
            return state;
        }
        try {
            Class<?> api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            apiGet = api.getMethod("get", net.minecraft.server.MinecraftServer.class);
            claimsManager = api.getMethod("getServerClaimsManager");
            Class<?> mgr =
                    Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
            claimAt = mgr.getMethod("get", Identifier.class, BlockPos.class);
            state = State.PRESENT;
        } catch (ReflectiveOperationException | LinkageError e) {
            state = State.ERROR;
            DolfinZombies.LOGGER.warn(
                    "event=integration_error name=opac reason={} effect=claims_fail_closed",
                    e.toString());
        }
        return state;
    }

    static State state() {
        return state;
    }

    static ClaimStatus claimAt(ServerLevel level, BlockPos pos) {
        switch (state) {
            case ABSENT -> {
                return ClaimStatus.UNCLAIMED;
            }
            case ERROR -> {
                return ClaimStatus.ERROR;
            }
            case PRESENT -> {}
        }
        try {
            Object api = apiGet.invoke(null, level.getServer());
            Object claim =
                    claimAt.invoke(claimsManager.invoke(api), level.dimension().identifier(), pos);
            return claim == null ? ClaimStatus.UNCLAIMED : ClaimStatus.CLAIMED;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // Fail closed: if OPAC is installed but unreadable, treat every position as protected.
            if (!runtimeErrorLogged) {
                runtimeErrorLogged = true;
                DolfinZombies.LOGGER.warn(
                        "event=integration_error name=opac phase=lookup reason={}"
                                + " effect=claims_fail_closed",
                        e.toString());
            }
            return ClaimStatus.ERROR;
        }
    }
}
