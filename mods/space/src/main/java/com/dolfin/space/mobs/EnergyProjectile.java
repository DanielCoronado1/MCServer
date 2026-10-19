package com.dolfin.space.mobs;

import com.dolfin.space.DolfinSpace;
import com.dolfin.space.planet.PlanetModule;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Non-explosive, non-incendiary projectile; vanilla item renderer shows the energy crystal. */
public final class EnergyProjectile extends ThrowableItemProjectile {
    public EnergyProjectile(EntityType<? extends EnergyProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDefaultItem() {
        return PlanetModule.ITEMS.get("cristal_energia");
    }

    @Override
    protected double getDefaultGravity() {
        return 0;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level() instanceof ServerLevel server
                && hit.getEntity() instanceof LivingEntity victim) {
            victim.hurtServer(
                    server,
                    damageSources().thrown(this, getOwner()),
                    (float) DolfinSpace.config().current().mobs().centinelaDanioProyectil());
            DolfinSpace.LOGGER.debug(
                    "event=sentinel_projectile_hit entity={} target={}",
                    getUUID(),
                    victim.getUUID());
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide()) discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount > 100) discard();
    }
}
