package com.dolfin.space.ships;
import com.dolfin.space.DolfinSpace;
import com.dolfin.space.machines.MachinesModule;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.AABB;
public final class FuelLoaderBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
 private NonNullList<ItemStack> items=NonNullList.withSize(2,ItemStack.EMPTY);
 public FuelLoaderBlockEntity(BlockPos p,BlockState s){super(ShipsModule.LOADER_TYPE,p,s);}
 protected NonNullList<ItemStack> getItems(){return items;}protected void setItems(NonNullList<ItemStack> v){items=v;}
 public int getContainerSize(){return 2;}protected Component getDefaultName(){return Component.translatable("block.dolfinspace.cargador_combustible");}
 protected AbstractContainerMenu createMenu(int id,Inventory i){return null;}
 public void serverTick(){
  if(level.getGameTime()%20!=0||getItem(0).isEmpty()||!DolfinSpace.config().current().general().activo())return;
  for(var ship:level.getEntitiesOfClass(SpaceshipEntity.class,new AABB(worldPosition).inflate(4,3,4))){
   var center=ship.blockPosition().below();int r=ship.tier().radius();
   int dx=Math.abs(center.getX()-worldPosition.getX()),dz=Math.abs(center.getZ()-worldPosition.getZ());
   boolean adjacent=(dx==r+1&&dz<=r)||(dz==r+1&&dx<=r);
   if(!adjacent||ship.inFlight||Math.abs(center.getY()-worldPosition.getY())>1||!PlatformRules.valid(level,center,ship.tier()))continue;
   int transfer=ShipState.transfer(ship.fuel(),getItem(0).getCount(),getItem(1).isEmpty()?16:16-getItem(1).getCount());
   if(transfer==0)continue;ship.fuel(ship.fuel()+transfer);getItem(0).shrink(1);
   if(getItem(1).isEmpty())setItem(1,new ItemStack(MachinesModule.item("bidon_vacio")));else getItem(1).grow(1);
   setChanged();DolfinSpace.LOGGER.debug("event=ship_refueled ship={} fuel={} loader={}",ship.getUUID(),ship.fuel(),worldPosition);break;
  }
 }
 public boolean canPlaceItem(int slot,ItemStack s){return slot==0&&s.is(MachinesModule.item("bidon_combustible"));}
 public int[] getSlotsForFace(Direction d){return d==Direction.DOWN?new int[]{1}:new int[]{0};}
 public boolean canPlaceItemThroughFace(int s,ItemStack i,Direction d){return d!=Direction.DOWN&&canPlaceItem(s,i);}
 public boolean canTakeItemThroughFace(int s,ItemStack i,Direction d){return s==1&&d==Direction.DOWN;}
 protected void saveAdditional(ValueOutput o){super.saveAdditional(o);ContainerHelper.saveAllItems(o,items);DolfinSpace.LOGGER.debug("event=loader_save pos={}",worldPosition);}
 protected void loadAdditional(ValueInput i){super.loadAdditional(i);ContainerHelper.loadAllItems(i,items);DolfinSpace.LOGGER.debug("event=loader_load pos={}",worldPosition);}
}
