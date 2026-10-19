package com.dolfin.space.mobs;

import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.projectile.arrow.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class SpaceSkeleton extends Skeleton {
    public SpaceSkeleton(EntityType<? extends SpaceSkeleton> type, Level level) {
        super(type, level);
    }

    @Override
    protected AbstractArrow getArrow(ItemStack ammo, float power, ItemStack weapon) {
        var arrow = new CrystalArrow(MobsModule.CRYSTAL_ARROW, level());
        arrow.setOwner(this);
        arrow.setPos(getX(), getEyeY() - .1, getZ());
        arrow.setBaseDamageFromMob(power);
        return arrow;
    }
}
