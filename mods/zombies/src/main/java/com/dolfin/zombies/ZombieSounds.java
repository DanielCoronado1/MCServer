package com.dolfin.zombies;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
/** Shared by support mechanics and the #5/#7 ability-success hooks. */
public final class ZombieSounds {
 private ZombieSounds(){}
 public static final SoundEvent GRITON_SCREAM=register("griton_scream"), BILIOSO_BURST=register("bilioso_burst"), ABISMO_GRAB=register("abismo_grab"), LADRON_STEAL=register("ladron_steal");
 private static SoundEvent register(String name){var id=DolfinZombies.id(name);return Registry.register(BuiltInRegistries.SOUND_EVENT,id,SoundEvent.createVariableRangeEvent(id));}
 public static void init(){DolfinZombies.LOGGER.info("event=zombie_sounds_registered count=4");}
 public static void play(Entity source,SoundEvent sound){source.playSound(sound,1,1);DolfinZombies.LOGGER.debug("event=zombie_sound uuid={} sound={}",source.getUUID(),sound.location());}
}
