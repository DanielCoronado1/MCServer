package com.dolfin.space.mobs;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

public final class SpaceZombie extends Zombie {
    public SpaceZombie(EntityType<? extends SpaceZombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
            net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty,
            net.minecraft.world.entity.EntitySpawnReason reason,
            net.minecraft.world.entity.SpawnGroupData group) {
        var result = super.finalizeSpawn(level, difficulty, reason, group);
        getAttribute(
                        net.minecraft.world.entity.ai.attributes.Attributes
                                .SPAWN_REINFORCEMENTS_CHANCE)
                .setBaseValue(0);
        return result;
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }
}
