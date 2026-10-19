package com.dolfin.space.ships;
import com.dolfin.space.DolfinSpace;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
public final class AssemblyBlockEntity extends BaseContainerBlockEntity implements ExtendedMenuProvider<Integer> {
 private NonNullList<ItemStack> items=NonNullList.withSize(8,ItemStack.EMPTY);
 public int tier;
 public AssemblyBlockEntity(BlockPos p,BlockState s) {super(ShipsModule.ASSEMBLY_TYPE,p,s);}
 public int getContainerSize(){return 8;}
 protected NonNullList<ItemStack> getItems(){return items;}
 protected void setItems(NonNullList<ItemStack> v){items=v;}
 protected Component getDefaultName(){return Component.literal("Mesa de ensamblaje");}
 public Integer getScreenOpeningData(ServerPlayer p){return 0;}
 protected AbstractContainerMenu createMenu(int id,Inventory inv){return new AssemblyMenu(id,inv,this);}
 public boolean canPlaceItem(int slot,ItemStack stack){return ShipTier.safe(tier).accepts(slot,net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());}
 protected void saveAdditional(ValueOutput out){super.saveAdditional(out);ContainerHelper.saveAllItems(out,items);out.putInt("Tier",tier);DolfinSpace.LOGGER.debug("event=assembly_save pos={}",worldPosition);}
 protected void loadAdditional(ValueInput in){super.loadAdditional(in);ContainerHelper.loadAllItems(in,items);tier=ShipTier.safe(in.getIntOr("Tier",0)).ordinal();DolfinSpace.LOGGER.debug("event=assembly_load pos={}",worldPosition);}
}
