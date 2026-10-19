package com.dolfin.zombies.kinds;
import com.dolfin.zombies.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.storage.*;
public final class GritonZombie extends SupportZombie {
 private long readyAt,fleeUntil;
 private LivingEntity fleeFrom;
 public GritonZombie(EntityType<? extends Zombie> type,Level level){super(type,level,ZombieKind.GRITON);
  goalSelector.addGoal(1,new net.minecraft.world.entity.ai.goal.Goal(){
   {setFlags(java.util.EnumSet.of(Flag.MOVE,Flag.LOOK));}
   public boolean canUse(){return !level().isClientSide()&&abilityEnabled("llamada")&&level().getGameTime()<fleeUntil&&fleeFrom!=null;}
   public void start(){setTarget(null);retreat(fleeFrom,1.4);}
   public void tick(){setTarget(null);}
  });}
 @Override public boolean hurtServer(ServerLevel world,net.minecraft.world.damagesource.DamageSource source,float amount){
  // Vanilla hard-mode reinforcements must never bypass the explicit blood-moon budget.
  var chance=getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SPAWN_REINFORCEMENTS_CHANCE);
  chance.removeModifiers();chance.setBaseValue(0);return super.hurtServer(world,source,amount);
 }
 @Override public void tick(){super.tick();if(!(level() instanceof ServerLevel world))return;
  long now=world.getGameTime();if(nextScan==0)nextScan=now+Math.floorMod(-getId()-now,20);if(!SupportRules.scanDue(now,nextScan))return;nextScan=now+20;
  if(!abilityEnabled("llamada")){reject("disabled");return;}
  if(now<fleeUntil&&fleeFrom!=null){setTarget(null);retreat(fleeFrom,1.4);return;}
  if(fleeFrom!=null){DolfinZombies.LOGGER.debug("event=griton_flee_end uuid={}",getUUID());fleeFrom=null;}
  if(now<readyAt){reject("cooldown");return;}
  var a=ability("llamada");double radius=Math.min(64,a.get("radioLlamada").getAsDouble());
  Player player=world.getEntitiesOfClass(Player.class,getBoundingBox().inflate(32),p->p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&distanceToSqr(p)<=1024&&getSensing().hasLineOfSight(p)).stream().min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);if(player==null){reject("no_visible_player");return;}
  int count=0,limit=Math.min(64,a.get("maxZombiesLlamados").getAsInt());
  for(var z:world.getEntitiesOfClass(Zombie.class,getBoundingBox().inflate(radius),z->z!=this&&z.isAlive()&&SupportRules.within(distanceToSqr(z),radius)&&(a.get("incluyeVanilla").getAsBoolean()||z instanceof ConfiguredZombie))){
   if(count>=limit)break;z.setTarget(player);z.getNavigation().moveTo(player,1.1);count++;
  }
  int spawned=0;
  if(BloodMoon.active(world))for(int i=0;i<Math.min(15,a.get("refuerzosLunaDeSangre").getAsInt());i++){
   var pos=blockPosition().offset(random.nextInt(7)-3,0,random.nextInt(7)-3);
   var z=EntityTypes.ZOMBIE.create(world,EntitySpawnReason.REINFORCEMENT);
   if(z==null)continue;z.snapTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
   if(!world.noCollision(z)||!world.getBlockState(pos.below()).isSolidRender()){reject("reinforcement_obstructed");continue;}
   z.finalizeSpawn(world,world.getCurrentDifficultyAt(pos),EntitySpawnReason.REINFORCEMENT,null);z.setTarget(player);world.addFreshEntity(z);spawned++;
  }else reject("reinforcements_no_blood_moon");
  playSound(ZombieSounds.GRITON_SCREAM,1.5f,1);world.sendParticles(ParticleTypes.ANGRY_VILLAGER,getX(),getEyeY(),getZ(),12,.4,.4,.4,0);
  readyAt=now+Math.max(20,a.get("enfriamiento").getAsInt());fleeUntil=now+60;fleeFrom=player;setTarget(null);retreat(player,1.4);
  DolfinZombies.LOGGER.debug("event=griton_call uuid={} target={} called={} spawned={} radius={} fleeUntil={}",getUUID(),player.getUUID(),count,spawned,radius,fleeUntil);
 }
 @Override protected void addAdditionalSaveData(ValueOutput out){super.addAdditionalSaveData(out);out.putLong("DolfinCallReady",readyAt);DolfinZombies.LOGGER.debug("event=griton_saved uuid={} readyAt={}",getUUID(),readyAt);}
 @Override protected void readAdditionalSaveData(ValueInput in){super.readAdditionalSaveData(in);readyAt=in.getLongOr("DolfinCallReady",0);DolfinZombies.LOGGER.debug("event=griton_loaded uuid={} readyAt={}",getUUID(),readyAt);}
}
