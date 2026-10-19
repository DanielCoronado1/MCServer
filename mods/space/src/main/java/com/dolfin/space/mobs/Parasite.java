package com.dolfin.space.mobs;

import com.dolfin.space.DolfinSpace;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

public final class Parasite extends Spider {
    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return com.dolfin.space.assets.SpaceSounds.PARASITE;
    }

    private final ParasiteCycle cycle = new ParasiteCycle();
    private final SneakCounter sneaks = new SneakCounter();
    private int fleeing, leapCooldown;

    public Parasite(EntityType<? extends Parasite> type, Level level) {
        super(type, level);
        getAttribute(Attributes.MAX_HEALTH)
                .setBaseValue(DolfinSpace.config().current().mobs().parasitoVida());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.36);
        setHealth(getMaxHealth());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(4, new RandomStrollGoal(this, 1.0));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8));
    }

    @Override
    public Vec3 getVehicleAttachmentPoint(Entity vehicle) {
        return Vec3.ZERO;
    }

    private int attached(Player player) {
        return (int) player.getPassengers().stream().filter(e -> e instanceof Parasite).count();
    }

    public void detach(String reason) {
        Entity victim = getVehicle();
        stopRiding();
        cycle.reset();
        sneaks.reset(false);
        fleeing = 100;
        if (victim != null) {
            var away =
                    position()
                            .subtract(victim.position())
                            .add(getRandom().nextDouble() - .5, .1, getRandom().nextDouble() - .5)
                            .normalize();
            setDeltaMovement(away.scale(.6));
            setPos(getX(), victim.getY() + .3, getZ());
        }
        setTarget(null);
        getNavigation().stop();
        DolfinSpace.LOGGER.debug(
                "event=parasite_detached entity={} victim={} reason={}",
                getUUID(),
                victim == null ? null : victim.getUUID(),
                reason);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        if (getVehicle() instanceof Player victim) {
            if (!MobTargets.eligible(victim)) {
                detach("victim_exempt");
                return;
            }
            if (sneaks.update(victim.isShiftKeyDown(), tickCount)) {
                detach("three_sneaks");
                return;
            }
            var attack = cycle.tick();
            if (attack != ParasiteCycle.Attack.NONE) {
                victim.hurtServer(
                        server,
                        MobsModule.parasiteDamage(server, this),
                        (float) DolfinSpace.config().current().mobs().parasitoDanio());
                DolfinSpace.LOGGER.debug(
                        "event=parasite_attack entity={} victim={} phase={}",
                        getUUID(),
                        victim.getUUID(),
                        attack);
                if (attack == ParasiteCycle.Attack.LAUNCH) {
                    victim.addEffect(
                            new MobEffectInstance(
                                    MobEffects.LEVITATION,
                                    DolfinSpace.config()
                                                    .current()
                                                    .mobs()
                                                    .parasitoSegundosLevitacion()
                                            * 20));
                    detach("third_attack_levitation");
                }
            }
            return;
        }
        if (fleeing > 0) {
            fleeing--;
            Player p = MobTargets.nearest(this, 12);
            if (p != null) {
                Vec3 away = position().subtract(p.position()).normalize().scale(.25);
                setDeltaMovement(away.x, getDeltaMovement().y, away.z);
            }
            return;
        }
        if (leapCooldown > 0) leapCooldown--;
        Player target = MobTargets.nearest(this, 20);
        if (target == null) return;
        int count = attached(target);
        double distance = distanceToSqr(target);
        if (FaunaRules.latch(distance, true, count) && hasLineOfSight(target)) {
            if (startRiding(target, true, true)) {
                cycle.reset();
                sneaks.reset(target.isShiftKeyDown());
                getNavigation().stop();
                DolfinSpace.LOGGER.debug(
                        "event=parasite_latched entity={} player={}", getUUID(), target.getUUID());
            }
        } else if (FaunaRules.leap(distance, true, count, fleeing)
                && leapCooldown == 0
                && onGround()
                && hasLineOfSight(target)) {
            Vec3 motion = target.getEyePosition().subtract(position()).normalize().scale(.65);
            setDeltaMovement(motion.x, .5, motion.z);
            leapCooldown = 30;
            DolfinSpace.LOGGER.debug(
                    "event=parasite_leap entity={} player={}", getUUID(), target.getUUID());
        } else if (count == 0) getNavigation().moveTo(target, 1.3);
        else if (tickCount % 20 == 0)
            DolfinSpace.LOGGER.debug(
                    "event=parasite_latch_rejected entity={} reason=already_attached", getUUID());
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof Player && isPassenger()) detach("player_hit");
        return super.hurtServer(level, source, amount);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("SpaceFleeTicks", fleeing);
        DolfinSpace.LOGGER.debug("event=parasite_saved entity={}", getUUID());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        fleeing = Math.clamp(in.getIntOr("SpaceFleeTicks", 0), 0, 100);
        DolfinSpace.LOGGER.debug("event=parasite_loaded entity={}", getUUID());
    }
}
