package com.dolfin.zombies.custody;

import com.dolfin.zombies.DolfinZombies;
import java.lang.reflect.Method;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Optional runtime bridge to Open Parties and Claims (OPAC) and Ledger.
 * Uses reflection only when the target mod is loaded so compilation and runtime never require hard dependencies.
 */
public final class ExternalIntegrations {
    private ExternalIntegrations(){}

    public static boolean opacLoaded(){return FabricLoader.getInstance().isModLoaded("openpartiesandclaims");}
    public static boolean ledgerLoaded(){return FabricLoader.getInstance().isModLoaded("ledger");}

    /**
     * Returns true if {@code pos} in {@code level} is inside an OPAC claim that does NOT belong to {@code allowedOwner}
     * (or their party). When OPAC is absent or {@code respetarClaims} is false, returns false.
     */
    public static boolean blockedByForeignClaim(ServerLevel level,BlockPos pos,UUID allowedOwner){
        if(!DolfinZombies.config().current().general().get("respetarClaims").getAsBoolean()||!opacLoaded())return false;
        try{
            MinecraftServer server=level.getServer();
            Class<?> apiClass=Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Object api=apiClass.getMethod("get",MinecraftServer.class).invoke(null,server);
            if(api==null)return false;
            Object claims=apiClass.getMethod("getServerClaimsManager").invoke(api);
            if(claims==null)return false;
            Method getClaim=claims.getClass().getMethod("get",Identifier.class,BlockPos.class);
            Object claim=getClaim.invoke(claims,level.dimension().identifier(),pos);
            if(claim==null)return false;
            UUID claimOwner=(UUID)claim.getClass().getMethod("getPlayerId").invoke(claim);
            if(claimOwner==null)return false;
            if(allowedOwner!=null&&(claimOwner.equals(allowedOwner)||sameParty(server,claimOwner,allowedOwner)))return false;
            return true;
        }catch(Throwable t){
            DolfinZombies.LOGGER.debug("event=opac_claim_check_error pos={} reason={}",pos,t.toString());
            return false;
        }
    }

    /** Returns true if {@code a} and {@code b} are the same player or belong to the same OPAC party (or ally party). */
    public static boolean sameParty(MinecraftServer server,UUID a,UUID b){
        if(a==null||b==null)return false;
        if(a.equals(b))return true;
        if(server==null||!opacLoaded())return false;
        try{
            Class<?> apiClass=Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Object api=apiClass.getMethod("get",MinecraftServer.class).invoke(null,server);
            if(api==null)return false;
            Object partyMgr=apiClass.getMethod("getPartyManager").invoke(api);
            if(partyMgr==null)return false;
            Method byMember=partyMgr.getClass().getMethod("getPartyByMember",UUID.class);
            Object partyA=byMember.invoke(partyMgr,a);
            if(partyA==null)return false;
            Object partyB=byMember.invoke(partyMgr,b);
            if(partyB!=null&&partyA.equals(partyB))return true;
            Method getMember=partyA.getClass().getMethod("getMemberInfo",UUID.class);
            return getMember.invoke(partyA,b)!=null;
        }catch(Throwable t){
            DolfinZombies.LOGGER.debug("event=opac_party_check_error a={} b={} reason={}",a,b,t.toString());
            return false;
        }
    }

    /** Logs chest placement and inserted items to Ledger when installed, plus an audit log line. */
    public static void logChestToLedger(ServerLevel level,BlockPos pos,BlockState placedState,BlockEntity be,List<ItemStack> items,String sourceId){
        DolfinZombies.LOGGER.info("event=ledger_chest_placed source={} dimension={} pos={} items={}",sourceId,level.dimension().identifier(),pos,items.size());
        if(!ledgerLoaded())return;
        try{
            Class<?> cbClass=Class.forName("com.github.quiltservertools.ledger.callbacks.BlockPlaceCallback");
            Object eventObj=cbClass.getField("EVENT").get(null);
            Object invoker=eventObj.getClass().getMethod("invoker").invoke(eventObj);
            for(Method m:invoker.getClass().getMethods()){
                if(m.getName().equals("place")&&m.getParameterCount()==5&&m.getParameterTypes()[4]==String.class){
                    m.invoke(invoker,level,pos,placedState,be,sourceId);
                    break;
                }
            }
            Class<?> insertClass=Class.forName("com.github.quiltservertools.ledger.callbacks.ItemInsertCallback");
            Object insertEvent=insertClass.getField("EVENT").get(null);
            Object insertInvoker=insertEvent.getClass().getMethod("invoker").invoke(insertEvent);
            for(Method m:insertInvoker.getClass().getMethods()){
                if(m.getName().equals("insert")&&m.getParameterCount()==5){
                    for(ItemStack stack:items)if(!stack.isEmpty())m.invoke(insertInvoker,stack,pos,level,sourceId,null);
                    break;
                }
            }
        }catch(Throwable t){
            DolfinZombies.LOGGER.warn("event=ledger_hook_failed source={} pos={} reason={}",sourceId,pos,t.toString());
        }
    }
}
