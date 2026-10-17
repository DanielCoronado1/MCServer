package com.dolfin.zombies.mixin;
import com.dolfin.zombies.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.util.random.WeightedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {
 private static boolean extraPass;
 @Inject(method="createState",at=@At("RETURN"))
 private static void capture(int count,ServerLevel level,NaturalSpawner.ChunkGetter getter,LocalMobCapCalculator local,CallbackInfoReturnable<NaturalSpawner.SpawnState> ci){((SpawnStateWorld)ci.getReturnValue()).zombies$setLevel(level);}
 @Inject(method="spawnForChunk",at=@At("HEAD"))
 private static void enter(ServerLevel l,LevelChunk chunk,NaturalSpawner.SpawnState state,java.util.List<MobCategory> c,CallbackInfo ci){BloodMoon.SPAWNING_LEVEL.set(l);}

 @Inject(method="mobsAt",at=@At("RETURN"),cancellable=true)
 private static void pool(ServerLevel l,StructureManager s,ChunkGenerator g,MobCategory c,BlockPos p,CallbackInfoReturnable<WeightedList<MobSpawnSettings.SpawnerData>> ci){if(c==MobCategory.MONSTER)ci.setReturnValue(SpawnRules.pool(l,p,ci.getReturnValue()));}
 @Inject(method="isValidSpawnPostitionForType",at=@At("HEAD"),cancellable=true)
 private static void cap(ServerLevel l,MobCategory c,StructureManager s,ChunkGenerator g,MobSpawnSettings.SpawnerData data,BlockPos.MutableBlockPos p,double distance,CallbackInfoReturnable<Boolean> ci){var k=SpawnRules.kind(data.type());if(k!=null&&SpawnRules.rejection(l,p,k,true)!=null)ci.setReturnValue(false);}
 @Inject(method="spawnForChunk",at=@At("RETURN"))
 private static void frequency(ServerLevel l,LevelChunk chunk,NaturalSpawner.SpawnState state,java.util.List<MobCategory> categories,CallbackInfo ci){
  if(extraPass)return;
  if(!BloodMoon.active(l)||!categories.contains(MobCategory.MONSTER)){BloodMoon.SPAWNING_LEVEL.remove();return;}
  double extra=Math.max(0,BloodMoon.multiplier()-1);int passes=(int)extra+(l.getRandom().nextDouble()<extra-(int)extra?1:0);
  extraPass=true;try{for(int i=0;i<passes;i++)NaturalSpawner.spawnForChunk(l,chunk,state,java.util.List.of(MobCategory.MONSTER));}finally{extraPass=false;BloodMoon.SPAWNING_LEVEL.remove();}
 }
}
