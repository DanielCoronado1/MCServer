package com.dolfin.lobby.mixin;

import com.dolfin.lobby.StationProtection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelProtectionMixin {
    @Inject(
            method =
                    "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void protect(
            BlockPos pos,
            BlockState state,
            int flags,
            int depth,
            CallbackInfoReturnable<Boolean> ci) {
        if (StationProtection.rejectMutation((Level) (Object) this, pos)) ci.setReturnValue(false);
    }
}
