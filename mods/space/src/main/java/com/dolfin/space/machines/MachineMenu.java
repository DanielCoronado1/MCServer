package com.dolfin.space.machines;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class MachineMenu extends AbstractContainerMenu {
    public final Container machine;
    public final ContainerData data;
    public final int kind;

    public MachineMenu(int id, Inventory inv, int kind) {
        this(id, inv, new SimpleContainer(6), new SimpleContainerData(3), kind);
    }

    public MachineMenu(int id, Inventory inv, Container c, ContainerData data, int kind) {
        super(MachinesModule.MENU, id);
        this.machine = c;
        this.data = data;
        this.kind = kind;
        for (int i = 0; i < 6; i++) {
            final int slot = i;
            addSlot(
                    new Slot(c, i, 8 + i * 27, 35) {
                        public boolean mayPlace(ItemStack s) {
                            return slot < 4 && c.canPlaceItem(slot, s);
                        }
                    });
        }
        addStandardInventorySlots(inv, 8, 84);
        addDataSlots(data);
    }

    public boolean stillValid(Player p) {
        return machine.stillValid(p);
    }

    public ItemStack quickMoveStack(Player p, int index) {
        var slot = slots.get(index);
        var s = slot.getItem();
        var copy = s.copy();
        if (s.isEmpty()
                || !(index < 6
                        ? moveItemStackTo(s, 6, slots.size(), true)
                        : moveItemStackTo(s, 0, 4, false))) return ItemStack.EMPTY;
        if (s.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
