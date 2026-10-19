package com.dolfin.zombies.mixin.client;

import com.dolfin.zombies.client.DolfinZombiesClient;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class BloodMoonSkyMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void tint(ClientLevel l, float partial, Camera c, SkyRenderState s, CallbackInfo ci) {
        if (DolfinZombiesClient.red) s.skyColor = new org.joml.Vector3f(.45f, .015f, .025f);
    }
}
