package com.dolfin.zombies.kinds;

import com.dolfin.zombies.ConfiguredZombie;
import com.dolfin.zombies.DolfinZombies;
import com.dolfin.zombies.ZombieKind;
import com.dolfin.zombies.griefing.BlockGuard;
import com.dolfin.zombies.griefing.GuardDecision.Reason;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;

/**
 * Bombero (#4): always burning (visual only), immune to fire and sun. Each new block it steps into
 * may be set on fire ({@code probabilidadPorPaso}) when next to a flammable block, through {@link
 * BlockGuard} and only when fire spread is enabled. Ignites players it hits. Dies after {@code
 * segundosEnAgua} in water or a thunderstorm.
 */
public class BomberoZombie extends ConfiguredZombie {
    static final String ABILITY = "quemarBloques";
    private CombatParams.Bombero params;
    private BlockPos lastStep;
    private int wetTicks;

    public BomberoZombie(EntityType<ConfiguredZombie> type, Level level) {
        super(type, level, ZombieKind.BOMBERO);
    }

    private CombatParams.Bombero params() {
        if (params == null) params = CombatParams.Bombero.from(settings());
        return params;
    }

    /**
     * Burning look on both sides without fire ticks (so no self damage and no ember drop logic).
     */
    @Override
    public boolean isOnFire() {
        return !isInWater();
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FIRE)) return false;
        return super.hurtServer(level, source, amount);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        CombatParams.Bombero p = params();
        boolean wet =
                isInWater() || level.isThundering() && level.isRainingAt(blockPosition().above());
        wetTicks = wet ? wetTicks + 1 : 0;
        if (wetTicks >= Math.max(1, p.segundosEnAgua() * 20)) {
            DolfinZombies.LOGGER.debug(
                    "event=bombero_extinguished id={} uuid={} pos={} wetTicks={}",
                    kind().id(),
                    getUUID(),
                    blockPosition().toShortString(),
                    wetTicks);
            wetTicks = 0;
            hurtServer(level, damageSources().drown(), Float.MAX_VALUE);
            return;
        }
        BlockPos here = blockPosition();
        if (here.equals(lastStep)) return;
        lastStep = here.immutable();
        if (wet || !abilityEnabled(ABILITY) || getRandom().nextDouble() >= p.probabilidadPorPaso())
            return;
        tryIgnite(level, here);
    }

    private void tryIgnite(ServerLevel level, BlockPos feet) {
        BlockPos pos = findIgnitableAir(level, feet);
        if (pos == null) return;
        if (!BlockGuard.fireTickEnabled(level)) {
            BlockGuard.rejected(this, pos, "ignite", Reason.FIRE_TICK);
            return;
        }
        BlockGuard.tryPlace(level, this, pos, BaseFireBlock.getState(level, pos), "ignite");
    }

    private BlockPos findIgnitableAir(ServerLevel level, BlockPos feet) {
        if (canIgniteAt(level, feet)) return feet;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = feet.relative(d);
            if (canIgniteAt(level, p)) return p;
        }
        return null;
    }

    private boolean canIgniteAt(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir()
                && nextToFlammable(level, pos)
                && BaseFireBlock.canBePlacedAt(level, pos, getDirection());
    }

    private static boolean nextToFlammable(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values())
            if (level.getBlockState(pos.relative(d)).ignitedByLava()) return true;
        return false;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit
                && target instanceof Player player
                && abilityEnabled(ABILITY)
                && params().quemarJugador() > 0) {
            player.igniteForSeconds(params().quemarJugador());
            DolfinZombies.LOGGER.debug(
                    "event=bombero_ignite_player id={} uuid={} player={} seconds={}",
                    kind().id(),
                    getUUID(),
                    player.getName().getString(),
                    params().quemarJugador());
        }
        return hit;
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        super.readAdditionalSaveData(in);
        params = null;
    }
}
