package com.dolfin.zombies.kinds;
import com.dolfin.zombies.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
public final class ExploradorZombie extends SupportZombie {
 private static final EntityDataAccessor<Boolean> REVEALED=SynchedEntityData.defineId(ExploradorZombie.class,EntityDataSerializers.BOOLEAN);
 private long damagedUntil;
 private boolean dayState;
 public ExploradorZombie(EntityType<? extends Zombie> type,Level level){super(type,level,ZombieKind.EXPLORADOR);}
 @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(REVEALED,true);}
 public boolean bodyRevealed(){return entityData.get(REVEALED);}
 @Override protected boolean isSunSensitive(){return false;}
 private boolean day(){return level().isBrightOutside();}
 @Override public void setTarget(LivingEntity target){super.setTarget(day()?null:target);}
 @Override public boolean hurtServer(ServerLevel world,DamageSource source,float amount){boolean hit=super.hurtServer(world,source,amount);if(hit&&abilityEnabled("invisibilidad")){damagedUntil=world.getGameTime()+(long)(ability("invisibilidad").get("visibleAlRecibirDanio").getAsDouble()*20);reveal(true,"damage");}return hit;}
 private void reveal(boolean value,String reason){if(value!=bodyRevealed()){entityData.set(REVEALED,value);DolfinZombies.LOGGER.debug("event=explorador_reveal uuid={} revealed={} reason={}",getUUID(),value,reason);}setInvisible(!value);}
 @Override public void tick(){super.tick();if(!(level() instanceof ServerLevel world))return;
  if(day())setTarget(null);
  long now=world.getGameTime();if(!SupportRules.scanDue(now,nextScan))return;nextScan=now+20;
  var a=ability("invisibilidad");boolean enabled=abilityEnabled("invisibilidad"),day=day();
  int light=Math.max(world.getBrightness(LightLayer.BLOCK,blockPosition()),Math.max(0,world.getBrightness(LightLayer.SKY,blockPosition())-world.getSkyDarken()));
  reveal(SupportRules.revealed(enabled,day,light,a.get("revelarConLuz").getAsBoolean(),now,damagedUntil),!enabled?"disabled":day?"day":"light_or_timeout");
  if(day!=dayState){dayState=day;DolfinZombies.LOGGER.debug("event=explorador_day_state uuid={} day={}",getUUID(),day);}
  if(day&&world.canSeeSky(blockPosition())){boolean found=false;for(int i=0;i<12;i++){BlockPos pos=blockPosition().offset(random.nextInt(21)-10,random.nextInt(7)-3,random.nextInt(21)-10);if(!world.canSeeSky(pos)&&world.getBlockState(pos.below()).isSolidRender()&&world.getBlockState(pos).isAir()){getNavigation().moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,1.2);found=true;break;}}if(!found)reject("no_shade");}
 }
 @Override protected void playStepSound(BlockPos pos,BlockState state){if(level().isClientSide()||!abilityEnabled("invisibilidad")||!ability("invisibilidad").get("pasosSilenciosos").getAsBoolean())super.playStepSound(pos,state);}
 @Override protected void addAdditionalSaveData(ValueOutput out){super.addAdditionalSaveData(out);out.putLong("DolfinRevealUntil",damagedUntil);DolfinZombies.LOGGER.debug("event=explorador_saved uuid={} until={}",getUUID(),damagedUntil);}
 @Override protected void readAdditionalSaveData(ValueInput in){super.readAdditionalSaveData(in);damagedUntil=in.getLongOr("DolfinRevealUntil",0);DolfinZombies.LOGGER.debug("event=explorador_loaded uuid={} until={}",getUUID(),damagedUntil);}
}
