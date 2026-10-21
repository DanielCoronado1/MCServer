package com.dolfin.space.client.travel.mixin;

import com.dolfin.space.client.travel.TravelClientModule;
import com.dolfin.space.travel.LaunchRules;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraShakeMixin {
    @Shadow
    protected abstract void move(float forward, float up, float left);

    @Inject(method = "update", at = @At("TAIL"))
    private void dolfinspace$shake(DeltaTracker tracker, CallbackInfo ci) {
        long elapsed = System.nanoTime() - TravelClientModule.lastPacketNanos;
        // Packets are refreshed during ascent; stale packets never leave permanent camera movement.
        if (elapsed > 700_000_000L) return;
        double amplitude =
                .075 * LaunchRules.shake(TravelClientModule.intensity, TravelClientModule.scale());
        double t = System.nanoTime() / 1_000_000_000d;
        if (amplitude > 0)
            move(0, (float) (Math.sin(t * 41) * amplitude), (float) (Math.sin(t * 53) * amplitude));
    }
}
