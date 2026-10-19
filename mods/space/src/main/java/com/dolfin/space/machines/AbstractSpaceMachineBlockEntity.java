package com.dolfin.space.machines;

import com.dolfin.space.DolfinSpace;
import java.util.*;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Shared persistent six-slot machine: inputs 0..2, coal 3, product 4, bucket 5. */
public class AbstractSpaceMachineBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, ExtendedMenuProvider<Integer> {
    private NonNullList<ItemStack> items = NonNullList.withSize(6, ItemStack.EMPTY);
    private int progress, burn, total = 200;
    private String active = "";
    public final ContainerData data = new ContainerData() {
        public int get(int i) { return switch(i) { case 0 -> progress; case 1 -> total; default -> burn; }; }
        public void set(int i,int v) { switch(i) { case 0 -> progress=v; case 1 -> total=v; default -> burn=v; } }
        public int getCount() { return 3; }
    };
    public AbstractSpaceMachineBlockEntity(BlockPos pos, BlockState state) { super(MachinesModule.TYPE,pos,state); }
    public MachineRecipes.Kind kind() { return ((MachineBlock)getBlockState().getBlock()).kind; }
    protected NonNullList<ItemStack> getItems() { return items; }
    protected void setItems(NonNullList<ItemStack> value) { items=value; }
    public int getContainerSize() { return 6; }
    protected Component getDefaultName() { return getBlockState().getBlock().getName(); }
    public Integer getScreenOpeningData(ServerPlayer player) { return kind().ordinal(); }
    protected AbstractContainerMenu createMenu(int id, Inventory inv) { return new MachineMenu(id,inv,this,data,kind().ordinal()); }
    public static boolean accepts(ItemStack stack,String id) {
        return id.startsWith("#") ? stack.is(TagKey.create(Registries.ITEM, Identifier.parse(id.substring(1)))) : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id);
    }
    private boolean matches(MachineRecipes.Recipe r) {
        for (int i=0;i<3;i++) {
            if(i>=r.inputs().size()) { if(!getItem(i).isEmpty()) return false; }
            else { var ingredient=r.inputs().get(i); if(!accepts(getItem(i),ingredient.id()) || getItem(i).getCount()<ingredient.count()) return false; }
        }
        return true;
    }
    private boolean room(int slot, Item item) { var stack=getItem(slot); return stack.isEmpty() || stack.is(item) && stack.getCount()<stack.getMaxStackSize(); }
    public void serverTick() {
        if(!DolfinSpace.config().current().general().activo()) return;
        total=MachineRecipes.duration(kind(),DolfinSpace.config().current().maquinas());
        var recipe=MachineRecipes.ALL.stream().filter(r -> r.kind()==kind() && matches(r)).findFirst().orElse(null);
        String key=recipe==null ? "" : recipe.output();
        if(!active.equals(key)) { progress=0; active=key; DolfinSpace.LOGGER.debug("event=machine_recipe pos={} recipe={}",worldPosition,key); }
        boolean ready=recipe!=null && room(4,MachinesModule.item(recipe.output())) && (recipe.remainder().isEmpty() || room(5,Items.BUCKET));
        if(MachineRecipes.consumeFuel(ready,burn,MachineRecipes.fuel(BuiltInRegistries.ITEM.getKey(getItem(3).getItem()).toString()))) {
            getItem(3).shrink(1); burn=1600; DolfinSpace.LOGGER.debug("event=machine_fuel pos={}",worldPosition); setChanged();
        }
        if(!ready || burn==0) return;
        burn--; progress++;
        if(progress>=total) {
            for(int i=0;i<recipe.inputs().size();i++) getItem(i).shrink(recipe.inputs().get(i).count());
            if(getItem(4).isEmpty()) setItem(4,new ItemStack(MachinesModule.item(recipe.output()))); else getItem(4).grow(1);
            if(!recipe.remainder().isEmpty()) { if(getItem(5).isEmpty()) setItem(5,new ItemStack(Items.BUCKET)); else getItem(5).grow(1); }
            progress=0; DolfinSpace.LOGGER.debug("event=machine_complete pos={} output={}",worldPosition,recipe.output());
        }
        setChanged();
    }
    public boolean canPlaceItem(int slot,ItemStack s) {
        if(slot==3) return MachineRecipes.fuel(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
        if(slot<0 || slot>2) return false;
        return MachineRecipes.ALL.stream().filter(r->r.kind()==kind() && r.inputs().size()>slot).anyMatch(r->accepts(s,r.inputs().get(slot).id()));
    }
    public int[] getSlotsForFace(Direction d) { return d==Direction.UP ? new int[]{0,1,2} : d==Direction.DOWN ? new int[]{4,5} : new int[]{3}; }
    private static MachineRecipes.Face face(Direction d) { return d==Direction.UP ? MachineRecipes.Face.TOP : d==Direction.DOWN ? MachineRecipes.Face.BOTTOM : MachineRecipes.Face.SIDE; }
    public boolean canPlaceItemThroughFace(int slot,ItemStack s,Direction d) { return MachineRecipes.insert(slot,face(d)) && canPlaceItem(slot,s); }
    public boolean canTakeItemThroughFace(int slot,ItemStack s,Direction d) { return MachineRecipes.extract(slot,face(d)); }
    @Override protected void saveAdditional(ValueOutput out) { super.saveAdditional(out); ContainerHelper.saveAllItems(out,items); out.putInt("Progress",progress); out.putInt("Burn",burn); out.putString("Recipe",active); DolfinSpace.LOGGER.debug("event=machine_save pos={}",worldPosition); }
    @Override protected void loadAdditional(ValueInput in) { super.loadAdditional(in); ContainerHelper.loadAllItems(in,items); progress=Math.max(0,in.getIntOr("Progress",0)); burn=Math.max(0,in.getIntOr("Burn",0)); active=in.getStringOr("Recipe",""); DolfinSpace.LOGGER.debug("event=machine_load pos={}",worldPosition); }
}
