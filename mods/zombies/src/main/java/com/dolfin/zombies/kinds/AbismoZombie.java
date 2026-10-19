package com.dolfin.zombies.kinds;

import com.dolfin.zombies.ConfiguredZombie;
import com.dolfin.zombies.ZombieKind;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;

/**
 * Grabs a player in deep water and drags them toward the bottom. The grab always ends through
 * {@link #release(String)}: duration, victim hit/damage, death or disconnect, leaving the water, distance or removal.
 */
public class AbismoZombie extends ConfiguredZombie {
    private static final int FATIGUE_TICKS = 40, LOG_PERIOD = 20, SCAN_PERIOD = 5, RETRY_AFTER_REJECT = 20;
    private static final double MAX_COMPONENT = 0.6, RELEASE_DISTANCE = 3.5;

    private CombatParams.Abismo params;
    private ServerPlayer grabbed;
    private int ticksLeft, heldTicks;
    private long nextAttempt;
    private final TickCooldown cooldown = new TickCooldown();

    public AbismoZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level, ZombieKind.ABISMO);
        setPathfindingMalus(PathType.WATER, 0.0f); // prefers the water it hunts in
    }

    CombatParams.Abismo params() {
        var p = params;
        if (p == null) params = p = CombatParams.Abismo.from(settings());
        return p;
    }

    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); params = null; }

    public boolean isGrabbing() { return grabbed != null; }

    @Override public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) serverTick(server);
    }

    private void serverTick(ServerLevel level) {
        if (grabbed != null) { hold(level); return; }
        if (!isAlive() || !com.dolfin.zombies.ZombieTickPolicy.scanDue(kind(),tickCount,getId()) || !abilityEnabled(CombatParams.Abismo.ABILITY)) return;
        if (getTarget() instanceof ServerPlayer player) tryGrab(level, player, "proximity");
    }

    private void tryGrab(ServerLevel level, ServerPlayer player, String trigger) {
        long now = level.getGameTime();
        if (now < nextAttempt || !cooldown.ready(now)) return;
        var p = params();
        String reject = null;
        double dx = player.getX() - getX(), dz = player.getZ() - getZ(), dy = Math.abs(player.getY() - getY());
        if (!player.isAlive() || player.hasDisconnected()) reject = "target_invalid";
        else if (player.isCreative() || player.isSpectator()) reject = "target_creative_or_spectator";
        else if (!isInWater()) reject = "zombie_not_in_water";
        else if (!player.isInWater()) reject = "target_not_in_water";
        else if (dx * dx + dz * dz > p.alcanceAgarre() * p.alcanceAgarre() || dy > 2.5) return; // not in reach yet: silent
        int depth = 0;
        if (reject == null) {
            depth = waterDepth(level, player);
            if (depth < p.profundidadMinima()) reject = "too_shallow";
        }
        if (reject != null) {
            nextAttempt = now + RETRY_AFTER_REJECT;
            Abilities.debug(this, "abismo_grab_rejected", "reason={} trigger={} target={} depth={} minDepth={}", reject, trigger, player.getName().getString(), depth, p.profundidadMinima());
            return;
        }
        grabbed = player;
        ticksLeft = p.duracionAgarre();
        heldTicks = 0;
        playGrabSound();
        Abilities.debug(this, "abismo_grab_start", "trigger={} target={} depth={} ticks={} strength={} releaseDamage={}", trigger, player.getName().getString(), depth, ticksLeft, p.fuerzaArrastre(), p.danioParaSoltar());
    }

    private static int waterDepth(ServerLevel level, ServerPlayer player) {
        var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        int x = player.getBlockX(), z = player.getBlockZ();
        return WaterColumn.depth(y -> level.getFluidState(pos.set(x, y, z)).is(FluidTags.WATER), player.getBlockY(), level.getMinY(), level.getMaxY(), 32);
    }

    private void hold(ServerLevel level) {
        var p = params();
        var player = grabbed;
        boolean present = !player.isRemoved() && !player.hasDisconnected() && player.level() == level;
        String reason = GrabRules.releaseReason(new GrabRules.Inputs(ticksLeft, abilityEnabled(CombatParams.Abismo.ABILITY), isAlive(), isInWater(),
            present, player.isAlive(), !player.isCreative() && !player.isSpectator(), player.isInWater(),
            distanceToSqr(player), RELEASE_DISTANCE * RELEASE_DISTANCE));
        if (reason != null) { release(reason); return; }
        ticksLeft--;
        heldTicks++;

        double[] drag = DragMath.drag(player.getX(), player.getY(), player.getZ(), getX(), getY(), getZ(), p.fuerzaArrastre());
        Vec3 v = player.getDeltaMovement();
        double[] next = DragMath.addClamped(new double[] {v.x, v.y, v.z}, drag, MAX_COMPONENT);
        player.setDeltaMovement(next[0], next[1], next[2]);
        player.connection.send(new ClientboundSetEntityMotionPacket(player)); // players own their movement: push it to the client
        Vec3 own = getDeltaMovement();
        setDeltaMovement(own.x, Math.min(own.y, 0) - p.fuerzaArrastre() * 0.5, own.z);

        if (p.nivelFatiga() > 0 && heldTicks % 20 == 1) player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, FATIGUE_TICKS, p.nivelFatiga() - 1, false, false));
        if (heldTicks % LOG_PERIOD == 0) {
            Abilities.debug(this, "abismo_grab_tick", "target={} held={} left={} y={} velocity={}", player.getName().getString(), heldTicks, ticksLeft, player.getY(), next[1]);
        }
    }

    /** Ends the grab (idempotent) and starts the cooldown. */
    public void release(String reason) {
        var player = grabbed;
        if (player == null) return;
        grabbed = null;
        long now = level().getGameTime();
        cooldown.start(now, params().enfriamiento());
        Abilities.debug(this, "abismo_grab_release", "reason={} target={} held={} cooldown={}", reason, player.getName().getString(), heldTicks, params().enfriamiento());
        heldTicks = 0;
        ticksLeft = 0;
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && grabbed != null) {
            String reason = GrabRules.damageReason(source.getEntity() == grabbed, amount, params().danioParaSoltar());
            if (reason != null) release(reason);
        }
        return hurt;
    }

    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && grabbed == null && target instanceof ServerPlayer player && abilityEnabled(CombatParams.Abismo.ABILITY)) tryGrab(level, player, "melee_hit");
        return hit;
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        release(GrabRules.ZOMBIE_DEAD);
    }

    @Override public void remove(Entity.RemovalReason reason) {
        if (grabbed != null) release("zombie_removed_" + reason);
        super.remove(reason);
    }

    /** No swimming upward while holding the victim, otherwise the pull would be undone each tick. */
    @Override protected void jumpInLiquid(TagKey<Fluid> fluid) {
        if (grabbed == null) super.jumpInLiquid(fluid);
    }
}
