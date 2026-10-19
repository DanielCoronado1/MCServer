package com.dolfin.space.planet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class AcidMudBlock extends Block {
    public AcidMudBlock(Properties properties) { super(properties); }
    public static void poison(Level level, Entity entity) {
        boolean creative=entity instanceof Player p && p.isCreative();
        if (PlanetCatalog.acidAffects(entity instanceof LivingEntity,creative,entity.isSpectator(),!level.isClientSide())) {
            var living=(LivingEntity)entity;
            var effect=living.getEffect(MobEffects.POISON);
            if (effect==null || effect.getDuration()<40 || effect.getAmplifier()<PlanetCatalog.ACID_AMPLIFIER) {
                living.addEffect(new MobEffectInstance(MobEffects.POISON,PlanetCatalog.ACID_DURATION_TICKS,PlanetCatalog.ACID_AMPLIFIER));
                com.dolfin.space.DolfinSpace.LOGGER.debug("event=acid_poison entity={} pos={}",entity.getUUID(),entity.blockPosition());
            }
        }
    }
    @Override public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) { poison(level,entity); super.stepOn(level,pos,state,entity); }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier applier, boolean intersects) {
        if (intersects) poison(level,entity);
    }
}
