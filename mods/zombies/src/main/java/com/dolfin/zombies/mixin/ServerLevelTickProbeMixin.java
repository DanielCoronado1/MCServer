package com.dolfin.zombies.mixin;

import com.dolfin.zombies.ZombieTickProbe;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dispatch boundaries capture the complete subclass tick, including work after super.tick(). */
@Mixin(ServerLevel.class)
public class ServerLevelTickProbeMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void enter(Entity entity, CallbackInfo ci) {
        ZombieTickProbe.enter(entity);
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void exit(Entity entity, CallbackInfo ci) {
        ZombieTickProbe.exit(entity);
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"))
    private void passengerEnter(Entity vehicle, Entity passenger, CallbackInfo ci) {
        ZombieTickProbe.enter(passenger);
    }

    @Inject(method = "tickPassenger", at = @At("RETURN"))
    private void passengerExit(Entity vehicle, Entity passenger, CallbackInfo ci) {
        ZombieTickProbe.exit(passenger);
    }
}
