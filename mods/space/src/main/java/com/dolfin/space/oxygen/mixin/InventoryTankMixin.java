package com.dolfin.space.oxygen.mixin;

import com.dolfin.space.oxygen.*;

import com.dolfin.space.DolfinSpace;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryMenu.class)
public abstract class InventoryTankMixin extends AbstractContainerMenu {
    @Unique private int dolfinspace$tankIndex;
    protected InventoryTankMixin(MenuType<?> type, int id) { super(type, id); }
    @Inject(method="<init>", at=@At("TAIL"))
    private void addTank(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        dolfinspace$tankIndex = slots.size();
        addSlot(new Slot(((TankAccess)owner).dolfinspace$tank(), 0, 77, 44) {
            @Override public boolean mayPlace(ItemStack stack) { return TankItem.isTank(stack); }
            @Override public int getMaxStackSize() { return 1; }
            @Override public Identifier getNoItemIcon() { return DolfinSpace.id("container/slot/tank"); }
        });
    }
    @Inject(method="quickMoveStack", at=@At("HEAD"), cancellable=true)
    private void moveTank(Player player, int index, CallbackInfoReturnable<ItemStack> ci) {
        if (index < 0 || index >= slots.size()) return;
        Slot slot = slots.get(index);
        ItemStack stack = slot.getItem();
        if (index == dolfinspace$tankIndex || (index >= 9 && index <= 44 && TankItem.isTank(stack) && !slots.get(dolfinspace$tankIndex).hasItem())) {
            ItemStack copy = stack.copy();
            boolean moved = index == dolfinspace$tankIndex ? moveItemStackTo(stack, 9, 45, false)
                    : moveItemStackTo(stack, dolfinspace$tankIndex, dolfinspace$tankIndex + 1, false);
            if (!moved) { ci.setReturnValue(ItemStack.EMPTY); return; }
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
            slot.onTake(player, stack);
            ci.setReturnValue(copy);
        }
    }
}
