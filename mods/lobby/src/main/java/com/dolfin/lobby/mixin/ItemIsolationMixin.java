package com.dolfin.lobby.mixin;

import com.dolfin.lobby.StationProtection;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemIsolationMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void pickup(Player p, CallbackInfo ci) {
        if (StationProtection.isolated(p)) ci.cancel();
    }
}
