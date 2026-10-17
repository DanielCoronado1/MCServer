package com.dolfin.zombies;
import com.google.gson.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.tags.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.util.random.*;
import net.minecraft.util.valueproviders.UniformInt;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
/** Cached rules swapped once per reload; bounded counters avoid per-tick log spam. */
public final class SpawnRules {
 private record Rule(boolean enabled,int weight,double extra,int min,int max,int low,int high,int light,List<String> include,List<String> exclude){}
 private static long generation=-1; private static final EnumMap<ZombieKind,Rule> rules=new EnumMap<>(ZombieKind.class);
 private static List<String> worlds=List.of(); private static int cap; private static final Map<String,Long> counters=new TreeMap<>(); private static final ArrayDeque<JsonObject> samples=new ArrayDeque<>();
 private static List<String> strings(JsonArray a){return java.util.stream.StreamSupport.stream(a.spliterator(),false).map(JsonElement::getAsString).toList();}
 private static void refresh(){var s=DolfinZombies.config().current();if(s.generation()==generation)return;if(!counters.isEmpty())DolfinZombies.LOGGER.info("event=spawn_counters generation={} counters={}",generation,counters);counters.clear();generation=s.generation();var general=s.general();worlds=strings(general.getAsJsonArray("mundosPermitidos"));cap=general.get("maxZombiesEspecialesPorJugador").getAsInt();
  for(var k:ZombieKind.values()){var j=s.zombie(k);var g=j.getAsJsonArray("grupo");var h=j.getAsJsonArray("alturaY");rules.put(k,new Rule(s.enabled(k),j.get("peso").getAsInt(),j.getAsJsonObject("lunaDeSangre").get("pesoExtra").getAsDouble(),g.get(0).getAsInt(),g.get(1).getAsInt(),h.get(0).getAsInt(),h.get(1).getAsInt(),j.get("luzMax").getAsInt(),strings(j.getAsJsonArray("biomas")),strings(j.getAsJsonArray("biomasExcluidos"))));}
  DolfinZombies.LOGGER.info("event=spawn_rules_reloaded generation={} cap={} worlds={}",generation,cap,worlds);
 }
 public static int effectiveWeight(int base,double multiplier,boolean blood){return (int)Math.min(100000000,Math.round(base*(blood?multiplier:1)));}
 public static Map<String,Long> counters(){refresh();return Map.copyOf(counters);}
 public static List<JsonObject> samples(){return List.copyOf(samples);}
 public static void spawned(ConfiguredZombie z,EntitySpawnReason reason){var o=new JsonObject();o.addProperty("id",z.kind().id());o.addProperty("uuid",z.getUUID().toString());o.addProperty("reason",reason.toString());o.addProperty("dimension",z.level().dimension().identifier().toString());o.addProperty("pos",z.blockPosition().toString());o.addProperty("generation",z.configGeneration());o.addProperty("health",z.getMaxHealth());o.addProperty("bloodmoon",BloodMoon.active());if(samples.size()==32)samples.removeFirst();samples.addLast(o);counters.merge(z.kind().id()+":spawned_"+reason,1L,Long::sum);}

 public static boolean worldAllowed(ServerLevel l){refresh();return worlds.contains(l.dimension().identifier().toString());}
 private static boolean matches(Holder<Biome> biome,List<String> selectors){for(var id:selectors)if(id.startsWith("#")?biome.is(TagKey.create(Registries.BIOME,Identifier.parse(id.substring(1)))):biome.is(ResourceKey.create(Registries.BIOME,Identifier.parse(id))))return true;return false;}
 public static void init(){refresh();for(var k:ZombieKind.values())com.dolfin.zombies.mixin.SpawnPlacementsInvoker.register(DolfinZombies.type(k),k==ZombieKind.ABISMO?SpawnPlacementTypes.IN_WATER:SpawnPlacementTypes.ON_GROUND,net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,(type,l,reason,pos,random)->l.getDifficulty()!=net.minecraft.world.Difficulty.PEACEFUL&&rejection(l.getLevel(),pos,k,true)==null);for(var k:ZombieKind.values()){var r=rules.get(k);if(k!=ZombieKind.MIMO&&r.weight>0)BiomeModifications.addSpawn(c->matches(c.getBiomeHolder(),r.include)&&!matches(c.getBiomeHolder(),r.exclude),MobCategory.MONSTER,DolfinZombies.type(k),r.weight,r.min,r.max);}}
 public static String inspect(ServerLevel l,BlockPos p,ZombieKind k){refresh();var r=rules.get(k);String reason=rejection(l,p,k,true);return "id="+k.id()+" pos="+p+" dimension="+l.dimension().identifier()+" biome="+l.getBiome(p).unwrapKey().map(v->v.identifier().toString()).orElse("unknown")+" blockLight="+l.getBrightness(LightLayer.BLOCK,p)+" generation="+generation+" reason="+(reason==null?"eligible":reason)+" weight="+effectiveWeight(r.weight,r.extra,BloodMoon.active(l))+" group="+r.min+","+r.max+" placement="+SpawnPlacements.isSpawnPositionOk(DolfinZombies.type(k),l,p)+" cap="+cap+" capAllows="+belowCap(l,p);}
 public static ZombieKind kind(EntityType<?> type){for(var k:ZombieKind.values())if(DolfinZombies.type(k)==type)return k;return null;}
 public static String rejection(ServerLevel l,BlockPos p,ZombieKind k,boolean checkCap){refresh();var r=rules.get(k);String why=null;
  if(!r.enabled)why="disabled";else if(k==ZombieKind.MIMO)why="mimo";else if(!worldAllowed(l))why="world";else if(!BloodMoonState.isNight(l.getOverworldClockTime()))why="day";else if(p.getY()<r.low||p.getY()>r.high)why="height";else if(l.getBrightness(LightLayer.BLOCK,p)>r.light)why="light";else if(k==ZombieKind.ENANO&&(p.getY()>=40||l.getBrightness(LightLayer.BLOCK,p)!=0))why="dwarf";else if(!matches(l.getBiome(p),r.include)||matches(l.getBiome(p),r.exclude))why="biome";else if(k==ZombieKind.CONGELADO&&!l.getBiome(p).value().coldEnoughToSnow(p,l.getSeaLevel()))why="snow";else if(k==ZombieKind.ABISMO&&!deepWater(l,p))why="water_depth";else if(checkCap&&!belowCap(l,p))why="player_cap";
  counters.merge(k.id()+":"+(why==null?"eligible":why),1L,Long::sum);return why;
 }
 private static boolean deepWater(ServerLevel l,BlockPos p){if(!l.getFluidState(p).is(FluidTags.WATER))return false;int n=1;for(int d=1;d<5&&l.getFluidState(p.above(d)).is(FluidTags.WATER);d++)n++;for(int d=1;n<5&&d<5&&l.getFluidState(p.below(d)).is(FluidTags.WATER);d++)n++;return n>=5;}
 public static boolean belowCap(ServerLevel l,BlockPos p){for(var player:l.players())if(!player.isSpectator()&&player.distanceToSqr(p.getX()+.5,p.getY(),p.getZ()+.5)<=4096){int n=l.getEntitiesOfClass(ConfiguredZombie.class,player.getBoundingBox().inflate(64),z->z.isAlive()&&z.distanceToSqr(player)<=4096).size();if(n>=cap)return false;}return cap>0;}
 public static WeightedList<MobSpawnSettings.SpawnerData> pool(ServerLevel l,BlockPos p,WeightedList<MobSpawnSettings.SpawnerData> original){refresh();var entries=new ArrayList<Weighted<MobSpawnSettings.SpawnerData>>();boolean blood=BloodMoon.active(l);
  for(var e:original.unwrap())if(kind(e.value().type())==null&&!(blood&&BloodMoon.options().get("soloEspeciales").getAsBoolean()&&e.value().type()==EntityTypes.ZOMBIE))entries.add(e.value().type()==EntityTypes.ZOMBIE?new Weighted<>(e.value(),100):e);
  for(var k:ZombieKind.values()){var r=rules.get(k);int weight=effectiveWeight(r.weight,r.extra,blood);if(weight>0&&rejection(l,p,k,false)==null)entries.add(new Weighted<>(new MobSpawnSettings.SpawnerData(DolfinZombies.type(k),UniformInt.of(r.min,r.max)),weight));}return WeightedList.of(entries);
 }
}
