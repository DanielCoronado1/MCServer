package com.dolfin.space.ships;
import com.dolfin.space.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
public final class SpaceshipEntity extends Entity {
 private static final EntityDataAccessor<Integer> FUEL=SynchedEntityData.defineId(SpaceshipEntity.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Integer> TIER=SynchedEntityData.defineId(SpaceshipEntity.class,EntityDataSerializers.INT);
 public UUID owner;
 public BlockPos home;
 public boolean passengersAllowed;
 public boolean inFlight;
 public final SimpleContainer cargo=new SimpleContainer(27){public boolean stillValid(Player p){return !isRemoved()&&distanceToSqr(p)<64&&ShipAccess.allowed(owner,p);} public void setChanged(){super.setChanged();DolfinSpace.LOGGER.debug("event=ship_cargo_changed ship={}",getUUID());}};
 /** Persistent flight journal supplied by #28; opaque to the construction module. */
 public CompoundTag flight=new CompoundTag();
 public SpaceshipEntity(EntityType<? extends SpaceshipEntity> t,Level l){super(t,l);setNoGravity(true);setPermanentlyInvulnerable(true);}
 protected void defineSynchedData(SynchedEntityData.Builder b){b.define(FUEL,0);b.define(TIER,0);}
 public int fuel(){return entityData.get(FUEL);}
 public void fuel(int n){entityData.set(FUEL,Math.clamp(n,0,100));}
 public ShipTier tier(){return ShipTier.safe(entityData.get(TIER));}
 public void tier(ShipTier t){entityData.set(TIER,t.ordinal());}
 public ShipState state(){return new ShipState(owner==null?"":owner.toString(),tier().ordinal(),fuel(),home==null?0:home.asLong(),home!=null,passengersAllowed);}
 public void apply(ShipState s){try{owner=UUID.fromString(s.owner());}catch(IllegalArgumentException e){owner=null;}tier(ShipTier.safe(s.tier()));fuel(s.fuel());home=s.hasHome()?BlockPos.of(s.home()):null;passengersAllowed=s.passengersAllowed();}
 protected void readAdditionalSaveData(ValueInput in){apply(in.read("Ship",ShipState.CODEC).orElse(new ShipState("",0,0,0,false,false)));ContainerHelper.loadAllItems(in,cargo.getItems());flight=in.read("Flight",CompoundTag.CODEC).orElseGet(CompoundTag::new);inFlight=!flight.isEmpty();DolfinSpace.LOGGER.debug("event=ship_load ship={} fuel={} flight={}",getUUID(),fuel(),inFlight);}
 protected void addAdditionalSaveData(ValueOutput out){out.store("Ship",ShipState.CODEC,state());ContainerHelper.saveAllItems(out,cargo.getItems());out.store("Flight",CompoundTag.CODEC,flight);DolfinSpace.LOGGER.debug("event=ship_save ship={} fuel={} flight={}",getUUID(),fuel(),inFlight);}
 public ItemStack portable(){
  ItemStack stack=new ItemStack(ShipsModule.ITEMS.get(tier().itemId()));
  CompoundTag tag=(CompoundTag)ShipState.CODEC.encodeStart(NbtOps.INSTANCE,state()).getOrThrow();
  stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));stack.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(cargo.getItems()));return stack;
 }
 public boolean hurtServer(ServerLevel l,DamageSource s,float amount){DolfinSpace.LOGGER.debug("event=ship_damage_rejected ship={} source={}",getUUID(),s.getMsgId());return false;}
 public boolean isPickable(){return true;}
 public boolean isPushable(){return false;}
 protected boolean canAddPassenger(Entity e){return getPassengers().size()<tier().seats;}
 protected Vec3 getPassengerAttachmentPoint(Entity e,EntityDimensions d,float scale){return new Vec3(getPassengers().indexOf(e)==0?-.45:.45,1.2,0);}
 public InteractionResult interact(Player player,InteractionHand hand,Vec3 location){
  if(level().isClientSide())return InteractionResult.SUCCESS;
  boolean allowed=ShipAccess.allowed(owner,player);
  if(getFirstPassenger()==player&&player.getItemInHand(hand).is(Items.LEVER)){
   passengersAllowed=!passengersAllowed;player.sendOverlayMessage(Component.literal("Pasajeros: "+(passengersAllowed?"permitidos":"bloqueados")));
   DolfinSpace.LOGGER.info("event=ship_passenger_consent ship={} pilot={} allowed={}",getUUID(),player.getUUID(),passengersAllowed);return InteractionResult.SUCCESS;
  }
  if(player.isShiftKeyDown()&&player.getUUID().equals(owner)&&!inFlight){
   if(isVehicle()){player.sendOverlayMessage(Component.literal("Baja a los pasajeros antes de recoger la nave."));return InteractionResult.FAIL;}
   ItemStack stack=portable();if(!player.getInventory().add(stack))spawnAtLocation((ServerLevel)level(),stack);
   DolfinSpace.LOGGER.info("event=ship_pickup ship={} owner={} fuel={}",getUUID(),owner,fuel());discard();return InteractionResult.SUCCESS;
  }
  if(player.getItemInHand(hand).is(Items.CHEST)&&tier()==ShipTier.CARGA&&allowed){
   player.openMenu(new SimpleMenuProvider((id,inv,p)->ChestMenu.threeRows(id,inv,cargo),Component.literal("Carga de nave")));return InteractionResult.SUCCESS;
  }
  if(inFlight)return InteractionResult.FAIL;
  if(getPassengers().isEmpty()?allowed:passengersAllowed&&tier()==ShipTier.CARGA){
   boolean pilot=getPassengers().isEmpty();
   if(player.startRiding(this,true,true) && pilot && player instanceof ServerPlayer sp)ShipsModule.onPilotMounted.accept(sp);
   DolfinSpace.LOGGER.info("event=ship_board ship={} player={} pilot={}",getUUID(),player.getUUID(),pilot);return InteractionResult.SUCCESS;
  }
  DolfinSpace.LOGGER.debug("event=ship_access_rejected ship={} player={} reason=ownership_or_passenger_consent",getUUID(),player.getUUID());return InteractionResult.FAIL;
 }
 @Override public void tick(){super.tick();if(!level().isClientSide())ShipsModule.flightTick.accept(this);}
}
