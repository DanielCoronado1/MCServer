package com.dolfin.space.oxygen;

import com.dolfin.space.DolfinSpace;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class TankItem extends Item {
    private final int tier;
    public TankItem(Properties properties, int tier) { super(properties.durability(new int[]{300,600,1200}[tier])); this.tier = tier; }
    public int capacity() {
        var c = DolfinSpace.config().current().oxigeno();
        return switch (tier) { case 0 -> c.segundosTanqueChico(); case 1 -> c.segundosTanqueMediano(); default -> c.segundosTanqueGrande(); };
    }
    public static boolean isTank(ItemStack stack) { return !stack.isEmpty() && stack.getItem() instanceof TankItem && OxygenRules.isTank(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()); }
    public static int remaining(ItemStack stack) { return isTank(stack) ? Math.max(0, stack.getMaxDamage() - stack.getDamageValue()) : 0; }
    public static void normalize(ItemStack stack) {
        if (!isTank(stack)) return;
        int remaining = remaining(stack), capacity = ((TankItem) stack.getItem()).capacity();
        stack.set(DataComponents.MAX_DAMAGE, capacity);
        stack.setDamageValue(capacity - Math.min(capacity, remaining));
    }
    public static void remaining(ItemStack stack, int seconds) {
        if (isTank(stack)) stack.setDamageValue(Math.clamp(stack.getMaxDamage() - seconds, 0, stack.getMaxDamage()));
    }
    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarColor(ItemStack stack) { return OxygenRules.low(remaining(stack), stack.getMaxDamage(), 0.2) ? 0xff5544 : 0x55ddff; }
}
