package com.dolfin.space.mobs;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;

/** Retains vanilla explosion path so protection/explosion mixins remain authoritative. */
public final class SpaceCreeper extends Creeper {
    public SpaceCreeper(EntityType<? extends SpaceCreeper> type, Level level) {
        super(type, level);
    }
}
