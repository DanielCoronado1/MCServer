package com.dolfin.space.compat.mixin;

import com.dolfin.space.*;
import com.dolfin.space.compat.*;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockPlacementMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void space$guard(BlockPlaceContext c, CallbackInfoReturnable<InteractionResult> ci) {
        if (c.getPlayer() != null
                && c.getLevel() instanceof ServerLevel level
                && KeplerDimension.isKepler(level.dimension())
                && !SpaceClaims.allowed(
                        c.getPlayer(), level, c.getClickedPos(), CompatPolicy.Action.PLACE))
            ci.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void space$audit(BlockPlaceContext c, CallbackInfoReturnable<InteractionResult> ci) {
        if (ci.getReturnValue().consumesAction()
                && c.getPlayer() != null
                && c.getLevel() instanceof ServerLevel level)
            SpaceLedgerIntegration.record(
                    SpaceLedgerIntegration.Action.PLACE,
                    c.getPlayer(),
                    level,
                    c.getClickedPos(),
                    BuiltInRegistries.BLOCK
                            .getKey(level.getBlockState(c.getClickedPos()).getBlock())
                            .toString());
    }
}
