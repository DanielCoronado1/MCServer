package com.dolfin.space.ships;

import com.dolfin.space.DolfinSpace;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

public final class AssemblyMenu extends AbstractContainerMenu {
    public final Container parts;
    private final ContainerData data;

    public AssemblyMenu(int id, Inventory inv) {
        this(id, inv, new SimpleContainer(8));
    }

    public AssemblyMenu(int id, Inventory inv, Container parts) {
        super(ShipsModule.MENU, id);
        this.parts = parts;
        data =
                parts instanceof AssemblyBlockEntity be
                        ? new ContainerData() {
                            public int get(int i) {
                                return be.tier;
                            }

                            public void set(int i, int v) {
                                be.tier = v;
                                be.setChanged();
                            }

                            public int getCount() {
                                return 1;
                            }
                        }
                        : new SimpleContainerData(1);
        for (int i = 0; i < 8; i++) {
            final int n = i;
            addSlot(
                    new Slot(parts, i, 8 + i * 20, 67) {
                        public boolean mayPlace(ItemStack s) {
                            return tier().accepts(
                                            n,
                                            BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
                        }
                    });
        }
        addStandardInventorySlots(inv, 8, 113);
        addDataSlots(data);
    }

    public ShipTier tier() {
        return ShipTier.safe(data.get(0));
    }

    public boolean missing(int i) {
        return i < tier().template.size()
                && (!tier().accepts(
                                        i,
                                        BuiltInRegistries.ITEM
                                                .getKey(parts.getItem(i).getItem())
                                                .toString())
                        || parts.getItem(i).getCount() < tier().template.get(i).count());
    }

    public boolean ready() {
        for (int i = 0; i < 7; i++)
            if (missing(i) || i >= tier().template.size() && !parts.getItem(i).isEmpty())
                return false;
        return parts.getItem(7).isEmpty();
    }

    public boolean clickMenuButton(Player p, int button) {
        if (button >= 0 && button < 3) {
            // Switching templates with pieces still present would strand mismatched items.
            if (button == 2 && !fastUnlocked(p.getInventory())) {
                DolfinSpace.LOGGER.debug(
                        "event=assembly_rejected player={} reason=fast_locked", p.getUUID());
                return false;
            }
            if (parts.isEmpty()) {
                data.set(0, button);
                return true;
            }
            return false;
        }
        if (button != 3 || !ready()) {
            DolfinSpace.LOGGER.debug(
                    "event=assembly_rejected player={} reason=missing_parts", p.getUUID());
            return false;
        }
        parts.setItem(7, new ItemStack(ShipsModule.ITEMS.get(tier().itemId())));
        for (int i = 0; i < tier().template.size(); i++)
            parts.removeItem(i, tier().template.get(i).count());
        parts.setChanged();
        DolfinSpace.LOGGER.info("event=ship_assembled player={} tier={}", p.getUUID(), tier());
        return true;
    }

    public static boolean fastUnlocked(Inventory inv) {
        int circuits = 0;
        boolean titanium = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (id.equals("dolfinspace:circuito_avanzado")) circuits += stack.getCount();
            if (id.equals("dolfinspace:placa_titanio")
                    || id.equals("dolfinspace:punta_titanio")
                    || id.equals("dolfinspace:placa_casco_titanio")
                    || com.dolfin.space.machines.AbstractSpaceMachineBlockEntity.accepts(
                            stack, "#dolfinspace:lingotes_titanio")) titanium = true;
        }
        return circuits >= 2 && titanium;
    }

    public boolean stillValid(Player p) {
        return parts.stillValid(p);
    }

    public ItemStack quickMoveStack(Player p, int i) {
        var slot = slots.get(i);
        var s = slot.getItem();
        var copy = s.copy();
        if (s.isEmpty()
                || !(i < 8
                        ? moveItemStackTo(s, 8, slots.size(), true)
                        : moveItemStackTo(s, 0, 7, false))) return ItemStack.EMPTY;
        if (s.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
