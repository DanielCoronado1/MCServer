package com.dolfin.zombies;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.sounds.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import java.util.*;
import java.nio.file.*;
public final class BloodMoon {
 private static BloodMoonState state; private static long generation=-1; private static JsonObject options;
 private static boolean globallyEnabled; private static MinecraftServer server; private static final Map<UUID,Boolean> sent=new HashMap<>();
 public static JsonObject options(){long g=DolfinZombies.config().current().generation();if(g!=generation){generation=g;var general=DolfinZombies.config().current().general();globallyEnabled=general.get("activo").getAsBoolean();options=general.getAsJsonObject("lunaDeSangre");}return options;}
 public static boolean active(){return state!=null&&state.active;}
 public static boolean active(ServerLevel level){return active()&&SpawnRules.worldAllowed(level);}
 public static final ThreadLocal<ServerLevel> SPAWNING_LEVEL=new ThreadLocal<>();
 public static double multiplier(){return active()?options().get("multiplicadorSpawns").getAsDouble():1;}
 public static void init(){
  PayloadTypeRegistry.clientboundPlay().register(BloodMoonPayload.TYPE,BloodMoonPayload.CODEC);
  ServerLifecycleEvents.SERVER_STARTED.register(s->{server=s;state=s.overworld().getDataStorage().computeIfAbsent(BloodMoonState.TYPE);sent.clear();DolfinZombies.LOGGER.info("event=bloodmoon_loaded active={} lastNight={} activeNight={} time={} generation={}",active(),state.lastNight,state.activeNight,s.overworld().getOverworldClockTime(),generation);});
  ServerLifecycleEvents.SERVER_STOPPING.register(s->{report();sent.clear();});
  ServerLifecycleEvents.SERVER_STOPPED.register(s->{state=null;server=null;});
  ServerTickEvents.END_SERVER_TICK.register(s->tick(s));
  EntitySleepEvents.ALLOW_SLEEPING.register((player,pos)->{if(active()&&player.level() instanceof ServerLevel l&&active(l)&&options().get("dormirBloqueado").getAsBoolean()){if(player instanceof ServerPlayer p)p.sendSystemMessage(Component.literal("No puedes dormir durante la luna de sangre."));DolfinZombies.LOGGER.debug("event=bloodmoon_sleep_blocked player={} dimension={} pos={} generation={}",player.getUUID(),l.dimension().identifier(),pos,generation);return net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM;}return null;});
 }
 private static void tick(MinecraftServer s){
  options();long time=s.overworld().getOverworldClockTime(),night=BloodMoonState.night(time);
  if(active()&&(!globallyEnabled||!options().get("activo").getAsBoolean()||!BloodMoonState.isNight(time)||night!=state.activeNight))change(false,!BloodMoonState.isNight(time)||night!=state.activeNight?"dawn":"disabled",false);
  if(BloodMoonState.isNight(time)&&night>state.lastNight){
   state.lastNight=night;state.setDirty();double roll=s.overworld().getRandom().nextDouble();boolean start=globallyEnabled&&options().get("activo").getAsBoolean()&&BloodMoonState.scheduled(night,options().get("cadaNNoches").getAsInt(),options().get("probabilidadPorNoche").getAsDouble(),roll);
   DolfinZombies.LOGGER.info("event=bloodmoon_evaluate night={} every={} chance={} roll={} selected={} generation={}",night,options().get("cadaNNoches"),options().get("probabilidadPorNoche"),roll,start,generation);if(start)change(true,"scheduled",false);
  }
  for(var p:s.getPlayerList().getPlayers()){
   boolean red=active(p.level())&&options().get("cieloRojo").getAsBoolean();
   if(!Objects.equals(sent.get(p.getUUID()),red)){if(ServerPlayNetworking.canSend(p,BloodMoonPayload.TYPE))ServerPlayNetworking.send(p,new BloodMoonPayload(red));if(ServerPlayNetworking.canSend(p,BloodMoonPayload.TYPE))sent.put(p.getUUID(),red);}
  }
  sent.keySet().removeIf(id->s.getPlayerList().getPlayer(id)==null);
  if(s.getTickCount()%1200==0)report();
 }
 public static boolean change(boolean start,String reason,boolean forceNight){
  options();if(state==null||start==state.active||start&&(!globallyEnabled||!options().get("activo").getAsBoolean()))return false;
  if(start&&forceNight){long time=server.overworld().getOverworldClockTime();if(!BloodMoonState.isNight(time))server.overworld().clockManager().setTotalTicks(server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_CLOCK).getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD),Math.floorDiv(time,24000)*24000+13000);}
  long night=BloodMoonState.night(server.overworld().getOverworldClockTime());state.active=start;state.activeNight=start?night:-1;state.lastNight=Math.max(state.lastNight,night);state.setDirty();
  server.overworld().getDataStorage().saveAndJoin();
  int burning=0;if(!start&&reason.equals("dawn"))for(var l:server.getAllLevels())for(var e:l.getAllEntities())if(SpawnRules.worldAllowed(l)&&e instanceof ConfiguredZombie z&&z.settings().get("quemaConSol").getAsBoolean()){z.igniteForSeconds(8);burning++;}
  for(var player:server.getPlayerList().getPlayers())if(SpawnRules.worldAllowed(player.level())){
   player.sendSystemMessage(Component.literal(start?"¡La luna de sangre ha comenzado!":"La luna de sangre ha terminado."));
   if(start&&options().get("aviso").getAsBoolean()){
    player.connection.send(new ClientboundSetTitlesAnimationPacket(10,100,20));player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§4Luna de sangre")));
    player.connection.send(new ClientboundSoundPacket(net.minecraft.core.Holder.direct(SoundEvents.WITHER_SPAWN),SoundSource.HOSTILE,player.getX(),player.getY(),player.getZ(),1,.6f,0));
   }
  }
  DolfinZombies.LOGGER.info("event=bloodmoon_transition active={} reason={} night={} burning={} generation={}",start,reason,night,burning,generation);sent.clear();report();return true;
 }
 public static String status(){return "active="+active()+" lastNight="+(state==null?-1:state.lastNight)+" activeNight="+(state==null?-1:state.activeNight)+" generation="+DolfinZombies.config().current().generation()+" multiplier="+multiplier();}
 public static void report(){
  var o=new JsonObject();o.addProperty("timestamp",java.time.Instant.now().toString());o.addProperty("bloodmoon",status());o.addProperty("active",active());o.addProperty("lastNight",state==null?-1:state.lastNight);o.addProperty("activeNight",state==null?-1:state.activeNight);o.addProperty("generation",DolfinZombies.config().current().generation());o.add("spawnCounters",new Gson().toJsonTree(SpawnRules.counters()));o.add("recentSpawns",new Gson().toJsonTree(SpawnRules.samples()));o.add("bloodmoonOptions",options().deepCopy());
  try{Path path=DolfinZombies.config().root().resolve("diagnostics/runtime.json");Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(o));}catch(Exception e){DolfinZombies.LOGGER.error("event=runtime_report_failed",e);}
 }
}
