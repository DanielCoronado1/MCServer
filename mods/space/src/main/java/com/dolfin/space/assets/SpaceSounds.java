package com.dolfin.space.assets;
import com.dolfin.space.DolfinSpace;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
public final class SpaceSounds {
 public static final SoundEvent HUM=register("cockpit_hum"), BEEP=register("check_beep"), ALARM=register("fuel_alarm"), ENGINE=register("engine"), PARASITE=register("parasite");
 private static SoundEvent register(String name){return Registry.register(BuiltInRegistries.SOUND_EVENT,DolfinSpace.id(name),SoundEvent.createVariableRangeEvent(DolfinSpace.id(name)));}
 public static void init(){}
 private SpaceSounds(){}
}
