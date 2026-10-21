package com.dolfin.lobby.mixin;

import com.dolfin.lobby.StationProtection;

import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class AdvancementIsolationMixin {
    @Shadow private ServerPlayer player;

    @Inject(method = "award", at = @At("HEAD"), cancellable = true)
    private void award(CallbackInfoReturnable<Boolean> ci) {
        if (StationProtection.isolated(player)) ci.setReturnValue(false);
    }
}
