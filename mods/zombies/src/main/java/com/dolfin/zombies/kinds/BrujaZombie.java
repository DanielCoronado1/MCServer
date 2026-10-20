package com.dolfin.zombies.kinds;

import com.dolfin.zombies.*;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;

public final class BrujaZombie extends SupportZombie {
    private long potionAt;
    private CombatParams.Bruja params;

    public CombatParams.Bruja params() {
        CombatParams.Bruja p = params;
        if (p == null) params = p = CombatParams.Bruja.from(settings());
        return p;
    }

    public BrujaZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level, ZombieKind.BRUJA);
        goalSelector.addGoal(
                1,
                new net.minecraft.world.entity.ai.goal.Goal() {
                    {
                        setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
                    }

                    public boolean canUse() {
                        return !level().isClientSide()
                                && abilityEnabled("curacion")
                                && getTarget() instanceof Player;
                    }

                    public void tick() {
                        var target = getTarget();
                        if (target == null) return;
                        double preferred = params().distanciaPreferida();
                        getLookControl().setLookAt(target, 30, 30);
                        if (distanceToSqr(target) < preferred * preferred) {
                            if (ZombieTickPolicy.navigationDue(tickCount, getId()))
                                retreat(target, 1.2);
                        } else if (distanceToSqr(target) > (preferred + 2) * (preferred + 2)) {
                            if (ZombieTickPolicy.navigationDue(tickCount, getId()))
                                getNavigation().moveTo(target, 1);
                        } else getNavigation().stop();
                    }
                });
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel world)) return;
        long now = world.getGameTime();
        if (nextScan == 0) nextScan = now + Math.floorMod(-getId() - now, 20);
        if (!SupportRules.scanDue(now, nextScan)) return;
        nextScan = now + 20;
        if (!abilityEnabled("curacion")) {
            reject("disabled");
            return;
        }
        var a = params();
        double radius = a.radioCuracion();
        if (now < potionAt) {
            reject("potion_cooldown");
            return;
        }
        int healed = 0;
        for (var z :
                world.getEntitiesOfClass(
                        Zombie.class,
                        getBoundingBox().inflate(radius),
                        z ->
                                SupportRules.healable(
                                        z == this,
                                        z.isAlive(),
                                        z.getHealth(),
                                        z.getMaxHealth(),
                                        distanceToSqr(z),
                                        radius))) {
            z.heal(a.curacion());
            world.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    z.getX(),
                    z.getEyeY(),
                    z.getZ(),
                    8,
                    .3,
                    .3,
                    .3,
                    0);
            healed++;
        }
        var target = getTarget();
        boolean cast = false;
        if (target instanceof Player player
                && player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()
                && distanceToSqr(player) <= 1024
                && getSensing().hasLineOfSight(player)) {
            var effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(a.efectoLanzado()));
            if (effect.isPresent()) {
                player.addEffect(
                        new MobEffectInstance(effect.get(), a.duracionEfectoSegundos() * 20, 0),
                        this);
                cast = true;
                world.sendParticles(
                        ParticleTypes.WITCH,
                        player.getX(),
                        player.getY() + 1,
                        player.getZ(),
                        12,
                        .3,
                        .5,
                        .3,
                        0);
            } else reject("unknown_effect");
        } else reject("no_visible_target");
        if (healed > 0 || cast) {
            potionAt = now + a.enfriamientoPocion();
            DolfinZombies.LOGGER.debug(
                    "event=bruja_potion uuid={} healed={} cast={} readyAt={}",
                    getUUID(),
                    healed,
                    cast,
                    potionAt);
        } else reject("no_wounded_allies");
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putLong("DolfinPotionReady", potionAt);
        DolfinZombies.LOGGER.debug("event=bruja_saved uuid={} readyAt={}", getUUID(), potionAt);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        params = null;
        potionAt = in.getLongOr("DolfinPotionReady", 0);
        DolfinZombies.LOGGER.debug("event=bruja_loaded uuid={} readyAt={}", getUUID(), potionAt);
    }
}
