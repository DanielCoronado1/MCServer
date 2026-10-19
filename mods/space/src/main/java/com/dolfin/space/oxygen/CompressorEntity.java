package com.dolfin.space.oxygen;

import com.dolfin.space.DolfinSpace;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class CompressorEntity extends BaseContainerBlockEntity implements WorldlyContainer, ExtendedMenuProvider<Integer> {
    private NonNullList<ItemStack> items = NonNullList.withSize(5,ItemStack.EMPTY);
    private int progress;
    private boolean running;
    public CompressorEntity(BlockPos pos, BlockState state) { super(OxygenModule.COMPRESSOR_ENTITY,pos,state); }
    public static boolean accepts(int slot, ItemStack stack) {
        return switch(slot) {
            case 0 -> stack.is(ItemTags.LEAVES) || stack.is(ItemTags.SAPLINGS) || stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,DolfinSpace.id("plantas_compresor")));
            case 1 -> stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
            case 2,3,4 -> TankItem.isTank(stack);
            default -> false;
        };
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return accepts(slot,stack); }
    @Override public int[] getSlotsForFace(Direction direction) { return direction == Direction.DOWN ? new int[]{2,3,4} : direction == Direction.UP ? new int[]{0} : new int[]{1,2,3,4}; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) { return accepts(slot,stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) { return slot >= 2 && TankItem.remaining(stack) >= stack.getMaxDamage(); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> list) { items = list; }
    @Override public int getContainerSize() { return 5; }
    @Override protected Component getDefaultName() { return Component.translatable("block.dolfinspace.compresor_oxigeno"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inv) { return new CompressorMenu(id,inv,this); }
    @Override public Integer getScreenOpeningData(ServerPlayer player) { return 0; }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input); ContainerHelper.loadAllItems(input,items); progress = Math.max(0,input.getIntOr("Progress",0));
        DolfinSpace.LOGGER.debug("event=compressor_loaded pos={} progress={}",worldPosition,progress);
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output); ContainerHelper.saveAllItems(output,items); output.putInt("Progress",progress);
        DolfinSpace.LOGGER.debug("event=compressor_saved pos={} progress={}",worldPosition,progress);
    }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos,state);
        if (level != null) Containers.dropContents(level,pos,this);
    }
    public void tick() {
        int selected = -1;
        for (int i=2;i<5;i++) {
            var stack = items.get(i); TankItem.normalize(stack);
            if (TankItem.isTank(stack) && TankItem.remaining(stack)<stack.getMaxDamage()) { selected=i; break; }
        }
        boolean ready = selected >= 0 && accepts(0,items.get(0)) && accepts(1,items.get(1)) && DolfinSpace.config().current().general().activo();
        if (ready != running) { running=ready; DolfinSpace.LOGGER.debug("event=compressor_state pos={} running={} reason={}",worldPosition,running,ready?"ingredients_ready":"missing_input_or_full"); }
        if (!ready) { if (progress!=0) { progress=0; setChanged(); } return; }
        var config = DolfinSpace.config().current().maquinas();
        if (++progress >= config.ticksCompresorOxigeno()) {
            var tank = items.get(selected);
            TankItem.remaining(tank,OxygenRules.refill(TankItem.remaining(tank),tank.getMaxDamage(),config.segundosRecargaPorCicloOxigeno()));
            items.get(0).shrink(1); items.get(1).shrink(1); progress=0;
            DolfinSpace.LOGGER.debug("event=compressor_refill pos={} slot={} remaining={}",worldPosition,selected,TankItem.remaining(tank));
        }
        setChanged();
    }
}
