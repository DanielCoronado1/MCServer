package com.dolfin.space.mobs;

import com.dolfin.space.DolfinSpace;
import com.dolfin.space.planet.AcidMudBlock;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;

/** No vanilla potion payload: each application is claim-checked, including across boundaries. */
public final class AcidCloud extends AreaEffectCloud {
    public AcidCloud(EntityType<? extends AcidCloud> type, Level level) {
        super(type, level);
        setRadius(3);
        setDuration(200);
        setWaitTime(0);
        setRadiusOnUse(0);
        setRadiusPerTick(0);
        setCustomParticle(ParticleTypes.SPORE_BLOSSOM_AIR);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)
                || !isAlive()
                || tickCount % 20 != 0
                || !DolfinSpace.config().current().general().activo()) return;
        if (!ClaimGuard.allows(server, blockPosition())) return;
        for (var player : server.players()) {
            if (player.distanceToSqr(this) <= 9
                    && Math.abs(player.getY() - getY()) < 2
                    && MobTargets.eligible(player)
                    && ClaimGuard.allows(server, player.blockPosition()))
                AcidMudBlock.poison(server, player);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        DolfinSpace.LOGGER.debug("event=acid_cloud_saved entity={}", getUUID());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        DolfinSpace.LOGGER.debug("event=acid_cloud_loaded entity={}", getUUID());
    }

    public static void afterExplosion(SpaceCreeper creeper) {
        if (!(creeper.level() instanceof ServerLevel server)
                || !DolfinSpace.config().current().general().activo()
                || !ClaimGuard.allows(server, creeper.blockPosition())) return;
        var cloud = new AcidCloud(MobsModule.ACID_CLOUD, server);
        cloud.setPos(creeper.getX(), creeper.getY(), creeper.getZ());
        cloud.setOwner(creeper);
        server.addFreshEntity(cloud);
        DolfinSpace.LOGGER.debug(
                "event=acid_cloud_spawned pos={} duration=200", creeper.blockPosition());
    }
}
