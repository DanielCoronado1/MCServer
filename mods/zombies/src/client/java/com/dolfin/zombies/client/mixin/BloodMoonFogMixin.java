package com.dolfin.zombies.mixin.client;

import com.dolfin.zombies.client.DolfinZombiesClient;

import net.minecraft.client.renderer.fog.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class BloodMoonFogMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void tint(CallbackInfoReturnable<FogData> ci) {
        if (DolfinZombiesClient.red)
            ci.getReturnValue().color.set(.4f, .025f, .035f, ci.getReturnValue().color.w);
    }
}
