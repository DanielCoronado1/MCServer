package com.dolfin.lobby.mixin;

import com.dolfin.lobby.StationProtection;

import net.minecraft.server.level.ServerLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class SpecialSpawnMixin {
    @Inject(method = "tickCustomSpawners", at = @At("HEAD"), cancellable = true)
    private void spawn(CallbackInfo ci) {
        if (StationProtection.station((ServerLevel) (Object) this)) ci.cancel();
    }
}
