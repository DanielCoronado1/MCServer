package com.dolfin.space.mobs;

import com.dolfin.space.DolfinSpace;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

public final class Sentinel extends Ghast {
    private int cooldown;

    public Sentinel(EntityType<? extends Sentinel> type, Level level) {
        super(type, level);
        setNoGravity(true);
        getAttribute(Attributes.MAX_HEALTH)
                .setBaseValue(DolfinSpace.config().current().mobs().centinelaVida());
        setHealth(getMaxHealth());
    }

    @Override
    protected void registerGoals() {
        /* Flight and aim below replace ghast fireball goals. */
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        setNoGravity(true);
        cooldown = Math.min(FaunaRules.SENTINEL_COOLDOWN, cooldown + 1);
        var target = MobTargets.nearest(this, 32);
        int ground = getBlockY();
        int bottom = Math.max(server.getMinY(), ground - 64);
        while (ground > bottom
                && server.isEmptyBlock(
                        new net.minecraft.core.BlockPos(getBlockX(), ground - 1, getBlockZ())))
            ground--;
        double desired = ground + 8 + Math.sin(tickCount * .025);
        Vec3 horizontal =
                target == null
                        ? Vec3.ZERO
                        : target.position()
                                .subtract(position())
                                .multiply(1, 0, 1)
                                .normalize()
                                .scale(distanceToSqr(target) > 144 ? .10 : -.04);
        setDeltaMovement(horizontal.x, Math.clamp((desired - getY()) * .08, -.3, .3), horizontal.z);
        if (target != null) {
            getLookControl().setLookAt(target, 30, 30);
            if (FaunaRules.projectileReady(cooldown) && hasLineOfSight(target)) {
                var projectile = new EnergyProjectile(MobsModule.PROJECTILE, server);
                projectile.setOwner(this);
                projectile.setPos(getX(), getY() + getBbHeight() * .5, getZ());
                var aim = target.getEyePosition().subtract(projectile.position());
                projectile.shoot(aim.x, aim.y, aim.z, .8F, 1F);
                server.addFreshEntity(projectile);
                cooldown = 0;
                playSound(net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, 1, 1.4F);
                DolfinSpace.LOGGER.debug(
                        "event=sentinel_fire entity={} target={}", getUUID(), target.getUUID());
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return super.hurtServer(
                level,
                source,
                FaunaRules.incomingDamage(
                        amount, source.getDirectEntity() instanceof AbstractArrow));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("SpaceShotCooldown", cooldown);
        DolfinSpace.LOGGER.debug("event=sentinel_saved entity={}", getUUID());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        cooldown = Math.clamp(in.getIntOr("SpaceShotCooldown", 0), 0, FaunaRules.SENTINEL_COOLDOWN);
        DolfinSpace.LOGGER.debug("event=sentinel_loaded entity={}", getUUID());
    }
}
