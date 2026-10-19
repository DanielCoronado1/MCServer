package com.dolfin.zombies.kinds;

import com.dolfin.zombies.ConfiguredZombie;
import com.dolfin.zombies.DolfinZombies;
import com.dolfin.zombies.ZombieKind;
import com.dolfin.zombies.griefing.BlockGuard;
import com.dolfin.zombies.griefing.GuardDecision.Reason;
import com.dolfin.zombies.griefing.RateLimiter;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Minero (#4): when its player target is within {@code distanciaMaxRompiendo} but unreachable, it
 * digs one block at a time toward the player with a player-like crack animation. Path checks run at
 * most every 20 ticks and inspect at most three positions; all breaks go through {@link
 * BlockGuard}.
 */
public class MineroZombie extends ConfiguredZombie {
    static final String ABILITY = "romperBloques";
    private static final int SCAN_INTERVAL = 20;

    /** Never mined, whatever {@code durezaMax}/{@code listaNegra} say. */
    private static final Set<Block> NEVER =
            Set.of(
                    Blocks.OBSIDIAN,
                    Blocks.CRYING_OBSIDIAN,
                    Blocks.RESPAWN_ANCHOR,
                    Blocks.REINFORCED_DEEPSLATE,
                    Blocks.BEDROCK,
                    Blocks.END_PORTAL_FRAME,
                    Blocks.END_PORTAL,
                    Blocks.END_GATEWAY,
                    Blocks.NETHER_PORTAL,
                    Blocks.SPAWNER,
                    Blocks.BARRIER);

    private final RateLimiter limiter = new RateLimiter(1200);
    private Params params;
    private BlockPos miningPos;
    private BlockState miningState;
    private int progress, required, lastStage = -1, stuckScans;
    private double lastX, lastZ;

    public MineroZombie(EntityType<ConfiguredZombie> type, Level level) {
        super(type, level, ZombieKind.MINERO);
    }

    record Params(
            float durezaMax,
            int tiempoPorBloque,
            double distanciaMax,
            boolean soltar,
            int maxPorMinuto,
            Set<Block> ids,
            List<TagKey<Block>> tags) {}

    private Params params() {
        if (params == null)
            params = parse(settings().getAsJsonObject("habilidades").getAsJsonObject(ABILITY));
        return params;
    }

    static Params parse(JsonObject a) {
        Set<Block> ids = new HashSet<>();
        List<TagKey<Block>> tags = new java.util.ArrayList<>();
        if (a.has("listaNegra"))
            for (var e : a.getAsJsonArray("listaNegra")) {
                String s = e.getAsString();
                if (s.startsWith("#")) {
                    var id = Identifier.tryParse(s.substring(1));
                    if (id != null) tags.add(TagKey.create(Registries.BLOCK, id));
                } else {
                    var id = Identifier.tryParse(s);
                    if (id != null) BuiltInRegistries.BLOCK.getOptional(id).ifPresent(ids::add);
                }
            }
        return new Params(
                (float) num(a, "durezaMax", 3),
                (int) num(a, "tiempoPorBloque", 40),
                num(a, "distanciaMaxRompiendo", 16),
                a.has("soltarBloques") && a.get("soltarBloques").getAsBoolean(),
                (int) num(a, "maxBloquesPorMinuto", 20),
                Set.copyOf(ids),
                List.copyOf(tags));
    }

    private static double num(JsonObject a, String k, double d) {
        return a.has(k) ? a.get(k).getAsDouble() : d;
    }

    @Override
    protected void populateDefaultEquipmentSlots(
            net.minecraft.util.RandomSource random,
            net.minecraft.world.DifficultyInstance difficulty) {
        setItemSlot(
                net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.IRON_PICKAXE));
        setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0f);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!abilityEnabled(ABILITY)) {
            if (miningPos != null) cancel(level, "disabled");
            return;
        }
        if (miningPos != null) {
            continueMining(level);
            return;
        }
        if (com.dolfin.zombies.ZombieTickPolicy.scanDue(kind(), tickCount, getId())) scan(level);
    }

    private void scan(ServerLevel level) {
        Params p = params();
        if (!(getTarget() instanceof Player target)
                || !target.isAlive()
                || target.isCreative()
                || target.isSpectator()) {
            stuckScans = 0;
            return;
        }
        double dist = distanceTo(target);
        boolean moved = Math.abs(getX() - lastX) + Math.abs(getZ() - lastZ) > .35;
        lastX = getX();
        lastZ = getZ();
        if (dist > p.distanciaMax() || dist < 1.2) {
            stuckScans = 0;
            return;
        }
        var path = getNavigation().getPath();
        boolean noPath = path == null || !path.canReach();
        stuckScans = moved ? 0 : stuckScans + 1;
        if (!noPath && stuckScans < 2) return;
        BlockPos feet = blockPosition(), goal = target.blockPosition();
        for (int[] o :
                MiningMath.candidates(
                        goal.getX() - feet.getX(),
                        goal.getY() - feet.getY(),
                        goal.getZ() - feet.getZ())) {
            BlockPos pos = feet.offset(o[0], o[1], o[2]);
            if (!level.isLoaded(pos)) return;
            BlockState state = level.getBlockState(pos);
            if (state.getCollisionShape(level, pos).isEmpty()) continue;
            Reason r = blockRejection(level, pos, state, p);
            if (r == null) r = BlockGuard.check(level, pos);
            if (r == null && !limiter.canAcquire(level.getGameTime(), p.maxPorMinuto()))
                r = Reason.RATE_LIMIT;
            if (r != null) {
                BlockGuard.rejected(this, pos, "mine", r);
                return;
            }
            start(level, pos, state, p);
            return;
        }
    }

    /**
     * Hardness, unbreakable, blacklist and container checks; null when the block itself is minable.
     */
    static Reason blockRejection(ServerLevel level, BlockPos pos, BlockState state, Params p) {
        boolean tagHit = false;
        for (var tag : p.tags())
            if (state.is(tag)) {
                tagHit = true;
                break;
            }
        boolean blacklisted = p.ids().contains(state.getBlock()) || tagHit;
        // Defence in depth for modded chests/barrels not covered by listaNegra.
        boolean container =
                state.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container;
        return MiningMath.checkBlock(
                state.getDestroySpeed(level, pos),
                p.durezaMax(),
                NEVER.contains(state.getBlock()),
                blacklisted,
                container);
    }

    private void start(ServerLevel level, BlockPos pos, BlockState state, Params p) {
        miningPos = pos.immutable();
        miningState = state;
        progress = 0;
        lastStage = -1;
        required = MiningMath.breakTicks(state.getDestroySpeed(level, pos), p.tiempoPorBloque());
        getNavigation().stop();
        DolfinZombies.LOGGER.debug(
                "event=mine_start id={} uuid={} pos={} block={} ticks={}",
                kind().id(),
                getUUID(),
                miningPos.toShortString(),
                BlockGuard.blockId(state),
                required);
    }

    private void continueMining(ServerLevel level) {
        if (!(getTarget() instanceof Player t) || !t.isAlive()) {
            cancel(level, "target_lost");
            return;
        }
        if (distanceToSqr(miningPos.getX() + .5, miningPos.getY() + .5, miningPos.getZ() + .5)
                > 4.5 * 4.5) {
            cancel(level, "moved_away");
            return;
        }
        if (level.getBlockState(miningPos) != miningState) {
            cancel(level, "block_changed");
            return;
        }
        getLookControl()
                .setLookAt(miningPos.getX() + .5, miningPos.getY() + .5, miningPos.getZ() + .5);
        progress++;
        int stage = MiningMath.crackStage(progress, required);
        if (stage != lastStage) {
            lastStage = stage;
            level.destroyBlockProgress(getId(), miningPos, stage);
        }
        if (progress % 5 == 0)
            level.playSound(
                    null,
                    miningPos,
                    miningState.getSoundType().getHitSound(),
                    getSoundSource(),
                    .5f,
                    .75f);
        if (progress < required) return;
        Params p = params();
        BlockPos pos = miningPos;
        level.destroyBlockProgress(getId(), pos, -1);
        miningPos = null;
        miningState = null;
        // Re-check at completion: claims, gamerule or the minute cap may have changed while
        // digging.
        if (!limiter.canAcquire(level.getGameTime(), p.maxPorMinuto())) {
            BlockGuard.rejected(this, pos, "mine", Reason.RATE_LIMIT);
            return;
        }
        if (BlockGuard.tryBreak(level, this, pos, p.soltar(), "mine"))
            limiter.tryAcquire(level.getGameTime(), p.maxPorMinuto());
    }

    private void cancel(ServerLevel level, String why) {
        level.destroyBlockProgress(getId(), miningPos, -1);
        DolfinZombies.LOGGER.debug(
                "event=mine_cancel id={} uuid={} pos={} reason={}",
                kind().id(),
                getUUID(),
                miningPos.toShortString(),
                why);
        miningPos = null;
        miningState = null;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (miningPos != null && level() instanceof ServerLevel l)
            l.destroyBlockProgress(getId(), miningPos, -1);
        super.remove(reason);
    }
}
