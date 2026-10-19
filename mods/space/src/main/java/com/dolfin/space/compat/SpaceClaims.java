package com.dolfin.space.compat;
import com.dolfin.space.*;
import com.dolfin.space.ships.*;
import java.lang.reflect.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
/** OPAC 0.32.8 native protection decisions: true means BLOCKED, including its exception rules. */
public final class SpaceClaims {
 private static Method apiGet,protectionGet,block,place,entity;private static boolean failed;
 public static void init(){
  if(!installed()){status("absent","");return;}
  try{
   var api=Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");var protection=Class.forName("xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI");
   apiGet=api.getMethod("get",MinecraftServer.class);protectionGet=api.getMethod("getChunkProtection");
   block=protection.getMethod("onBlockInteraction",Entity.class,InteractionHand.class,ItemStack.class,ServerLevel.class,BlockPos.class,Direction.class,boolean.class,boolean.class);
   place=protection.getMethod("onEntityPlaceBlock",Entity.class,ServerLevel.class,BlockPos.class);
   entity=protection.getMethod("onEntityInteraction",Entity.class,Entity.class,Entity.class,ItemStack.class,InteractionHand.class,boolean.class,boolean.class);
   status("available","");
  }catch(ReflectiveOperationException|LinkageError e){failed=true;status("unavailable",e.toString());}
 }
 private static boolean installed(){return FabricLoader.getInstance().isModLoaded("openpartiesandclaims");}
 private static void status(String state,String reason){DolfinSpace.LOGGER.info("event=integration name=opac state={} reason={}",state,reason);}
 private static Object protection(ServerLevel l)throws ReflectiveOperationException{return protectionGet.invoke(apiGet.invoke(null,l.getServer()));}
 public static boolean allowed(Player player,ServerLevel level,BlockPos pos,CompatPolicy.Action action){
  if(!installed()||!DolfinSpace.config().current().general().respetarClaims())return true;
  Boolean denied=null;
  if(!failed)try{
   Object api=protection(level);
   denied=(Boolean)(action==CompatPolicy.Action.PLACE||action==CompatPolicy.Action.SHIP_PLACE?place.invoke(api,player,level,pos):block.invoke(api,player,InteractionHand.MAIN_HAND,ItemStack.EMPTY,level,pos,Direction.UP,action==CompatPolicy.Action.BREAK,false));
  }catch(ReflectiveOperationException|LinkageError e){failed=true;status("unavailable",e.toString());}
  boolean allowed=CompatPolicy.allowed(true,true,denied);
  if(!allowed)DolfinSpace.LOGGER.debug("event=claim_guard_rejected action={} player={} dimension={} pos={} reason={}",action,player.getUUID(),level.dimension().identifier(),pos,failed?"api_unavailable":"native_protection");
  return allowed;
 }
 public static boolean shipPlacement(Player p,ServerLevel l,BlockPos center,ShipTier tier){
  return CompatPolicy.footprint(tier,(x,z)->allowed(p,l,center.offset(x,1,z),CompatPolicy.Action.SHIP_PLACE));
 }
 public static boolean entityUse(Player player,SpaceshipEntity target){
  if(!(target.level() instanceof ServerLevel level)||!installed()||!DolfinSpace.config().current().general().respetarClaims())return true;
  try{return CompatPolicy.allowed(true,true,failed?null:(Boolean)entity.invoke(protection(level),player,player,target,ItemStack.EMPTY,InteractionHand.MAIN_HAND,false,false));}
  catch(ReflectiveOperationException|LinkageError e){failed=true;status("unavailable",e.toString());return false;}
 }
 public static boolean pvp(Player attacker,Player victim,Entity direct){
  if(!(victim.level() instanceof ServerLevel level)||!installed()||!DolfinSpace.config().current().general().respetarClaims())return true;
  try{return CompatPolicy.allowed(true,true,failed?null:(Boolean)entity.invoke(protection(level),attacker,direct==null?attacker:direct,victim,ItemStack.EMPTY,InteractionHand.MAIN_HAND,true,false));}
  catch(ReflectiveOperationException|LinkageError e){failed=true;status("unavailable",e.toString());return false;}
 }
 private SpaceClaims(){}
}
