package com.dolfin.zombies;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.level.saveddata.*;

/**
 * Overworld SavedData wrapper of {@link BloodMoonSchedule}: survives stop/restart; evaluated nights
 * are never rerolled.
 */
public final class BloodMoonState extends SavedData {
    public static final Codec<BloodMoonState> CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Codec.BOOL
                                                    .fieldOf("active")
                                                    .forGetter(s -> s.schedule.active),
                                            Codec.LONG
                                                    .fieldOf("lastNight")
                                                    .forGetter(s -> s.schedule.lastNight),
                                            Codec.LONG
                                                    .fieldOf("activeNight")
                                                    .forGetter(s -> s.schedule.activeNight),
                                            // Absent in saves written by the first #3 prototype.
                                            Codec.LONG
                                                    .optionalFieldOf("scheduledNight", -1L)
                                                    .forGetter(s -> s.schedule.scheduledNight))
                                    .apply(i, BloodMoonState::new));
    public static final SavedDataType<BloodMoonState> TYPE =
            new SavedDataType<>(DolfinZombies.id("bloodmoon"), BloodMoonState::new, CODEC, null);
    public final BloodMoonSchedule schedule;

    public BloodMoonState() {
        schedule = new BloodMoonSchedule();
    }

    public BloodMoonState(boolean active, long last, long started, long scheduled) {
        schedule = new BloodMoonSchedule(active, last, started, scheduled);
    }
}
