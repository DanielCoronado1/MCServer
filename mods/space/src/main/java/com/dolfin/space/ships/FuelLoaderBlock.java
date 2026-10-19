package com.dolfin.space.ships;
import com.dolfin.space.machines.MachinesModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
public final class FuelLoaderBlock extends BaseEntityBlock {
 public FuelLoaderBlock(Properties p){super(p);}
 public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new FuelLoaderBlockEntity(p,s);}
 protected InteractionResult useItemOn(ItemStack stack,BlockState s,Level l,BlockPos p,Player player,InteractionHand h,BlockHitResult hit){
  if(!l.isClientSide()&&l.getBlockEntity(p) instanceof FuelLoaderBlockEntity be&&stack.is(MachinesModule.item("bidon_combustible"))&&be.getItem(0).getCount()<16){
   if(be.getItem(0).isEmpty())be.setItem(0,new ItemStack(stack.getItem()));else be.getItem(0).grow(1);stack.shrink(1);be.setChanged();return InteractionResult.SUCCESS;
  }return InteractionResult.TRY_WITH_EMPTY_HAND;
 }
 protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
  if(!l.isClientSide()&&l.getBlockEntity(p) instanceof FuelLoaderBlockEntity be){var stack=be.removeItem(1,16);if(!player.getInventory().add(stack))player.spawnAtLocation((net.minecraft.server.level.ServerLevel)l,stack);}return InteractionResult.SUCCESS;
 }
 public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){return l.isClientSide()?null:createTickerHelper(type,ShipsModule.LOADER_TYPE,(w,p,state,be)->be.serverTick());}
}
