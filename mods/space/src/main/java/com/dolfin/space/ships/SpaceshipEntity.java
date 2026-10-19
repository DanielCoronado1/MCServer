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
 private static final EntityDataAccessor<Boolean> BURNING=SynchedEntityData.defineId(SpaceshipEntity.class,EntityDataSerializers.BOOLEAN);
 private static final EntityDataAccessor<Integer> FUEL=SynchedEntityData.defineId(SpaceshipEntity.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Integer> TIER=SynchedEntityData.defineId(SpaceshipEntity.class,EntityDataSerializers.INT);
 public UUID owner;
 public BlockPos home;
 public boolean passengersAllowed;
 public boolean inFlight;
 public final SimpleContainer cargo=new SimpleContainer(27){public boolean stillValid(Player p){return !isRemoved()&&distanceToSqr(p)<64&&ShipAccess.allowed(owner,p)&&com.dolfin.space.compat.SpaceClaims.entityUse(p,SpaceshipEntity.this);} public void setChanged(){super.setChanged();DolfinSpace.LOGGER.debug("event=ship_cargo_changed ship={}",getUUID());}};
 /** Persistent flight journal supplied by #28; opaque to the construction module. */
 public CompoundTag flight=new CompoundTag();
 public SpaceshipEntity(EntityType<? extends SpaceshipEntity> t,Level l){super(t,l);setNoGravity(true);setPermanentlyInvulnerable(true);}
 protected void defineSynchedData(SynchedEntityData.Builder b){b.define(BURNING,false);b.define(FUEL,0);b.define(TIER,0);}
 public int fuel(){return entityData.get(FUEL);}
 public void fuel(int n){entityData.set(FUEL,Math.clamp(n,0,100));}
 public ShipTier tier(){return entityData==null?ShipTier.BASICA:ShipTier.safe(entityData.get(TIER));}
 public void tier(ShipTier t){entityData.set(TIER,t.ordinal());}
 public boolean burning(){return entityData.get(BURNING);}
 @Override public EntityDimensions getDimensions(Pose pose){var d=ShipDesign.of(tier());return EntityDimensions.scalable(d.width(),d.height());}
 @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key.equals(TIER))refreshDimensions();}
 public boolean clearBoarding(Player p){
  var to=position().add(0,1.6,0);
  boolean sight=level().clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(),to,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p)).getType()==net.minecraft.world.phys.HitResult.Type.MISS;
  var seat=position().add(getPassengers().isEmpty()?-.45:.45,1.2,0);
  boolean clear=level().noCollision(p,p.getBoundingBox().move(seat.subtract(p.position())));
  return ShipDesign.boarding(sight,clear,distanceToSqr(p));
 }
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
  if(player.getVehicle()!=this&&!clearBoarding(player)){DolfinSpace.LOGGER.debug("event=ship_access_rejected ship={} player={} reason=wall_or_range",getUUID(),player.getUUID());return InteractionResult.FAIL;}
  if(!com.dolfin.space.compat.SpaceClaims.entityUse(player,this))return InteractionResult.FAIL;
  boolean allowed=ShipAccess.allowed(owner,player);
  if(getFirstPassenger()==player&&player.getItemInHand(hand).is(Items.LEVER)){
   passengersAllowed=!passengersAllowed;player.sendOverlayMessage(Component.translatable(passengersAllowed?"space.ui.passengers_on":"space.ui.passengers_off"));
   DolfinSpace.LOGGER.info("event=ship_passenger_consent ship={} pilot={} allowed={}",getUUID(),player.getUUID(),passengersAllowed);return InteractionResult.SUCCESS;
  }
  if(player.isShiftKeyDown()&&player.getUUID().equals(owner)&&!inFlight){
   if(isVehicle()){player.sendOverlayMessage(Component.translatable("space.ui.leave_first"));return InteractionResult.FAIL;}
   ItemStack stack=portable();if(!player.getInventory().add(stack))spawnAtLocation((ServerLevel)level(),stack);
   DolfinSpace.LOGGER.info("event=ship_pickup ship={} owner={} fuel={}",getUUID(),owner,fuel());discard();return InteractionResult.SUCCESS;
  }
  if(player.getItemInHand(hand).is(Items.CHEST)&&tier()==ShipTier.CARGA&&allowed){
   com.dolfin.space.compat.SpaceLedgerIntegration.container(player,(ServerLevel)level(),blockPosition());
   player.openMenu(new SimpleMenuProvider((id,inv,p)->ChestMenu.threeRows(id,inv,cargo),Component.translatable("space.ui.cargo")));return InteractionResult.SUCCESS;
  }
  if(inFlight)return InteractionResult.FAIL;
  if(getPassengers().isEmpty()?allowed:passengersAllowed&&tier()==ShipTier.CARGA){
   boolean pilot=getPassengers().isEmpty();
   if(player.startRiding(this,true,true) && pilot && player instanceof ServerPlayer sp)ShipsModule.onPilotMounted.accept(sp);
   DolfinSpace.LOGGER.info("event=ship_board ship={} player={} pilot={}",getUUID(),player.getUUID(),pilot);return InteractionResult.SUCCESS;
  }
  DolfinSpace.LOGGER.debug("event=ship_access_rejected ship={} player={} reason=ownership_or_passenger_consent",getUUID(),player.getUUID());return InteractionResult.FAIL;
 }
 @Override public void tick(){super.tick();if(!level().isClientSide()){ShipsModule.flightTick.accept(this);entityData.set(BURNING,inFlight&&!flight.getStringOr("phase","").equals("COUNTDOWN"));if(burning()&&tickCount%40==0)level().playSound(null,blockPosition(),com.dolfin.space.assets.SpaceSounds.ENGINE,net.minecraft.sounds.SoundSource.NEUTRAL,.4f,tier()==ShipTier.RAPIDA?1.3f:.8f);}
 else if(burning()&&tickCount%3==0){int engines=tier()==ShipTier.CARGA?2:1;for(int i=0;i<engines;i++){double x=getX()+(engines==2?(i==0?-1.12:1.12):0);level().addParticle(net.minecraft.core.particles.ParticleTypes.FLAME,x,getY()-.15,getZ(),0,-.12,0);level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,x,getY()-.3,getZ(),0,-.08,0);}}
 }
}
