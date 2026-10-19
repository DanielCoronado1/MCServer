package com.dolfin.zombies.kinds;

import com.dolfin.zombies.DolfinZombies;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;

/**
 * Server-side bookkeeping for Bilioso death clouds. A vanilla AreaEffectCloud cannot exclude zombies, so the cloud
 * is virtual: a bounded list ticked once per server tick that sends particles and applies effects to everything
 * except zombies. Registered lazily on first use; cleared on server stop.
 */
public final class BiliousClouds {
    private BiliousClouds() {}

    static final int MAX_CLOUDS = 64;
    private static final int EFFECT_PERIOD = 20, PARTICLE_PERIOD = 4;
    private static final int COLOR = 0xFF7BA428;

    private record Cloud(ServerLevel level, double x, double y, double z, double radius, long endTick, CombatParams.Bilioso params, UUID source) {}

    private static final List<Cloud> CLOUDS = new ArrayList<>();
    private static boolean registered;

    public static void spawn(ServerLevel level, double x, double y, double z, CombatParams.Bilioso params, UUID source) {
        if (!registered) {
            registered = true;
            ServerTickEvents.END_SERVER_TICK.register(BiliousClouds::tick);
            ServerLifecycleEvents.SERVER_STOPPED.register(s -> CLOUDS.clear());
        }
        if (CLOUDS.size() >= MAX_CLOUDS) {
            var dropped = CLOUDS.removeFirst();
            DolfinZombies.LOGGER.warn("event=bilioso_cloud_cap cap={} droppedSource={}", MAX_CLOUDS, dropped.source());
        }
        CLOUDS.add(new Cloud(level, x, y, z, params.radioNube(), level.getGameTime() + params.duracionNube(), params, source));
        DolfinZombies.LOGGER.debug("event=bilioso_cloud_spawn source={} pos={},{},{} radius={} durationTicks={} active={}", source, x, y, z, params.radioNube(), params.duracionNube(), CLOUDS.size());
    }

    public static int active() { return CLOUDS.size(); }

    private static void tick(MinecraftServer server) {
        if (CLOUDS.isEmpty()) return;
        for (Iterator<Cloud> it = CLOUDS.iterator(); it.hasNext(); ) {
            Cloud c = it.next();
            long now = c.level().getGameTime();
            if (now >= c.endTick()) {
                DolfinZombies.LOGGER.debug("event=bilioso_cloud_end source={}", c.source());
                it.remove();
                continue;
            }
            if (now % PARTICLE_PERIOD == 0) {
                c.level().sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, COLOR), c.x(), c.y() + 0.3, c.z(),
                    CloudMath.particleCount(c.radius()), c.radius() * 0.45, 0.2, c.radius() * 0.45, 0.0);
            }
            if (now % EFFECT_PERIOD == 0) apply(c);
        }
    }

    private static void apply(Cloud c) {
        var p = c.params();
        double r = c.radius();
        AABB box = new AABB(c.x() - r, c.y() - CloudMath.BELOW, c.z() - r, c.x() + r, c.y() + CloudMath.HEIGHT, c.z() + r);
        int affected = 0, skippedZombies = 0;
        for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (!CloudMath.contains(c.x(), c.y(), c.z(), r, e.getX(), e.getY(), e.getZ()) || e.isSpectator()) continue;
            if (e instanceof Zombie) { skippedZombies++; continue; }
            if (p.nauseaTicks() > 0) e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, p.nauseaTicks(), 0));
            if (p.venenoTicks() > 0) e.addEffect(new MobEffectInstance(MobEffects.POISON, p.venenoTicks(), p.venenoNivel() - 1));
            affected++;
        }
        if (affected + skippedZombies > 0) {
            DolfinZombies.LOGGER.debug("event=bilioso_cloud_apply source={} affected={} skippedZombies={} nauseaTicks={} poisonTicks={}", c.source(), affected, skippedZombies, p.nauseaTicks(), p.venenoTicks());
        }
    }
}
