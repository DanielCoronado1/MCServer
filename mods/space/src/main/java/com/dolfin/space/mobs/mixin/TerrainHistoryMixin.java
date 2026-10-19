package com.dolfin.space.mobs.mixin;

import com.dolfin.space.mobs.TerrainHistory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class TerrainHistoryMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void trackTerrain(
            BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> ci) {
        if (ci.getReturnValue() != null && ci.getReturnValue() != state)
            TerrainHistory.changed((LevelChunk) (Object) this, pos, state);
    }
}
