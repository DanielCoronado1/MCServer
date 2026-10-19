package com.dolfin.space.oxygen;

import com.dolfin.space.DolfinSpace;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** Not exposed as a world container; automation cannot access a player's equipped tank. */
public final class TankContainer extends SimpleContainer {
    public TankContainer() { super(1); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 0 && TankItem.isTank(stack); }
    @Override public void setItem(int slot, ItemStack stack) {
        if (!stack.isEmpty() && !canPlaceItem(slot, stack)) {
            DolfinSpace.LOGGER.debug("event=tank_slot_rejected reason=not_tank item={}", stack);
            return;
        }
        super.setItem(slot, stack);
    }
    @Override public int getMaxStackSize() { return 1; }
}
