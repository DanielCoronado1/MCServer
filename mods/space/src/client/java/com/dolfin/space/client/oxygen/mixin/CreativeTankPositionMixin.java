package com.dolfin.space.client.oxygen.mixin;

import com.dolfin.space.oxygen.TankContainer;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeTankPositionMixin {
    @ModifyArgs(
            method = "selectTab",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$SlotWrapper;<init>(Lnet/minecraft/world/inventory/Slot;III)V"))
    private void tankPosition(Args args) {
        if (((Slot) args.get(0)).container instanceof TankContainer) {
            args.set(2, 126);
            args.set(3, 20);
        }
    }
}
