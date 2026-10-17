package com.dolfin.zombies.mixin;
import com.dolfin.zombies.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(NaturalSpawner.SpawnState.class)
public class SpawnStateMixin implements SpawnStateWorld {
 @Unique private ServerLevel zombies$level;
 public void zombies$setLevel(ServerLevel level){zombies$level=level;}
 @Inject(method="canSpawnForCategoryGlobal",at=@At("HEAD"),cancellable=true)
 private void cap(MobCategory category,CallbackInfoReturnable<Boolean> ci){if(category==MobCategory.MONSTER&&zombies$level!=null&&BloodMoon.active(zombies$level)){var self=(NaturalSpawner.SpawnState)(Object)this;int max=(int)Math.ceil(70*BloodMoon.multiplier());ci.setReturnValue(self.getMobCategoryCounts().getInt(category)<max*self.getSpawnableChunkCount()/289);}}
}
