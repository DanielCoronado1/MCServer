package com.dolfin.space.mobs;

import com.dolfin.space.DolfinSpace;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;

/** Optional OPAC 0.32.8 public API. Fail closed for hazards, without replacing explosion protection. */
public final class ClaimGuard {
    private static Method apiGet, managerGet, claimGet;
    private static boolean failed;
    private ClaimGuard() {}
    public static boolean allows(ServerLevel level, BlockPos pos) {
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) return reject(pos,"mob_griefing_false");
        if (!DolfinSpace.config().current().general().respetarClaims() || !FabricLoader.getInstance().isModLoaded("openpartiesandclaims")) return true;
        if (failed) return false;
        try {
            if (apiGet==null) {
                var api=Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
                apiGet=api.getMethod("get",MinecraftServer.class);managerGet=api.getMethod("getServerClaimsManager");
                claimGet=Class.forName("xaero.pac.common.claims.api.IClaimsManagerAPI").getMethod("get",Identifier.class,BlockPos.class);
            }
            var manager=managerGet.invoke(apiGet.invoke(null,level.getServer()));
            return claimGet.invoke(manager,level.dimension().identifier(),pos)==null || reject(pos,"claimed_chunk");
        } catch (ReflectiveOperationException | LinkageError ex) {
            failed=true;DolfinSpace.LOGGER.warn("event=acid_claim_guard_failed action=disable_acid_hazards",ex);return false;
        }
    }
    private static boolean reject(BlockPos pos, String reason) { DolfinSpace.LOGGER.debug("event=acid_hazard_rejected pos={} reason={}",pos,reason);return false; }
}
