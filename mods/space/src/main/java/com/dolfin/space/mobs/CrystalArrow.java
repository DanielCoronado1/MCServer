package com.dolfin.space.mobs;

import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.Level;

/** Distinct renderer/texture and guaranteed slowness, preserving vanilla arrow flight. */
public final class CrystalArrow extends Arrow {
    public CrystalArrow(EntityType<? extends CrystalArrow> type, Level level) { super(type,level); }
    @Override protected void doPostHurtEffects(LivingEntity victim) {
        super.doPostHurtEffects(victim);
        if (!level().isClientSide()) victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,100,0));
    }
}
