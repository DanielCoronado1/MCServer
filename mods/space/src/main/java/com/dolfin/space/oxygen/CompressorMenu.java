package com.dolfin.space.oxygen;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Uses the vanilla five-slot screen, with identical client/server slot filters. */
public final class CompressorMenu extends HopperMenu {
    public CompressorMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(5));
    }

    public CompressorMenu(int id, Inventory inventory, Container container) {
        super(id, inventory, container);
    }

    @Override
    public MenuType<?> getType() {
        return OxygenModule.MENU;
    }

    @Override
    protected Slot addSlot(Slot slot) {
        if (slots.size() < 5) {
            int index = slots.size();
            slot =
                    new Slot(slot.container, index, slot.x, slot.y) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return CompressorEntity.accepts(index, stack);
                        }

                        @Override
                        public int getMaxStackSize(ItemStack stack) {
                            return index >= 2 ? 1 : super.getMaxStackSize(stack);
                        }
                    };
        }
        return super.addSlot(slot);
    }
}
