package com.dolfin.zombies.kinds;
import com.dolfin.zombies.*;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
abstract class SupportZombie extends ConfiguredZombie {
 protected long nextScan;
 SupportZombie(EntityType<? extends Zombie> type,Level level,ZombieKind kind){super(type,level,kind);}
 protected JsonObject ability(String key){return settings().getAsJsonObject("habilidades").getAsJsonObject(key);}
 protected void reject(String reason){DolfinZombies.LOGGER.debug("event=support_rejected kind={} uuid={} reason={}",kind(),getUUID(),reason);}
 protected void retreat(LivingEntity target,double speed){var pos=DefaultRandomPos.getPosAway(this,12,5,target.position());if(pos!=null)getNavigation().moveTo(pos.x,pos.y,pos.z,speed);else reject("no_retreat_path");}
}
