package com.dolfin.space.compat.mixin;

import com.dolfin.space.compat.*;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers machines, oxygen compressor, assembly and loader, including direct openMenu calls. */
@Mixin(BaseContainerBlockEntity.class)
public abstract class ContainerAccessMixin {
    private BaseContainerBlockEntity space$self() {
        return (BaseContainerBlockEntity) (Object) this;
    }

    private boolean space$owned() {
        return getClass().getName().startsWith("com.dolfin.space.");
    }

    @Inject(method = "canOpen", at = @At("HEAD"), cancellable = true)
    private void space$guard(Player player, CallbackInfoReturnable<Boolean> ci) {
        var be = space$self();
        if (space$owned()
                && be.getLevel() instanceof ServerLevel level
                && !SpaceCompat.container(player, level, be.getBlockPos()))
            ci.setReturnValue(false);
    }

    @Inject(method = "stillValid", at = @At("RETURN"), cancellable = true)
    private void space$revalidate(Player player, CallbackInfoReturnable<Boolean> ci) {
        var be = space$self();
        if (ci.getReturnValue()
                && space$owned()
                && be.getLevel() instanceof ServerLevel level
                && !SpaceCompat.container(player, level, be.getBlockPos()))
            ci.setReturnValue(false);
    }

    @Inject(
            method =
                    "createMenu(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;",
            at = @At("RETURN"))
    private void space$audit(
            int id,
            Inventory inv,
            Player player,
            CallbackInfoReturnable<AbstractContainerMenu> ci) {
        var be = space$self();
        if (ci.getReturnValue() != null
                && space$owned()
                && be.getLevel() instanceof ServerLevel level)
            SpaceLedgerIntegration.container(player, level, be.getBlockPos());
    }
}
