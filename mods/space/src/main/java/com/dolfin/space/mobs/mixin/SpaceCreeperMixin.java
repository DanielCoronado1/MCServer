package com.dolfin.space.mobs.mixin;

import com.dolfin.space.mobs.*;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
public abstract class SpaceCreeperMixin {
    @Inject(method="explodeCreeper",at=@At("TAIL"))
    private void afterExplosion(CallbackInfo ci) {
        if ((Object)this instanceof SpaceCreeper creeper) AcidCloud.afterExplosion(creeper);
    }
}
