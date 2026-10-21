package com.dolfin.lobby.mixin;

import com.dolfin.lobby.StationProtection;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ServerPlayer.class)
public abstract class ProgressIsolationMixin {
    @Inject(
            method = {
                "awardStat",
                "giveExperiencePoints",
                "giveExperienceLevels",
                "awardKillScore",
                "awardRecipesByKey"
            },
            at = @At("HEAD"),
            cancellable = true)
    private void progress(CallbackInfo ci) {
        if (StationProtection.isolated((ServerPlayer) (Object) this)) ci.cancel();
    }

    @Inject(method = "awardRecipes", at = @At("HEAD"), cancellable = true)
    private void recipes(CallbackInfoReturnable<Integer> ci) {
        if (StationProtection.isolated((ServerPlayer) (Object) this)) ci.setReturnValue(0);
    }

    @Inject(
            method =
                    "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"),
            cancellable = true)
    private void drop(
            ItemStack stack,
            boolean random,
            Prediction prediction,
            CallbackInfoReturnable<ItemEntity> ci) {
        if (StationProtection.isolated((ServerPlayer) (Object) this)) ci.setReturnValue(null);
    }
}
