package com.dolfin.zombies.mixin;
import com.dolfin.zombies.BloodMoon;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(MobCategory.class)
public class MonsterCapMixin {
 @Inject(method="getMaxInstancesPerChunk",at=@At("RETURN"),cancellable=true)
 private void cap(CallbackInfoReturnable<Integer> ci){if((Object)this==MobCategory.MONSTER&&BloodMoon.SPAWNING_LEVEL.get()!=null&&BloodMoon.active(BloodMoon.SPAWNING_LEVEL.get()))ci.setReturnValue(com.dolfin.zombies.BloodMoonOptions.scaledCap(ci.getReturnValue(),BloodMoon.multiplier()));}
}
