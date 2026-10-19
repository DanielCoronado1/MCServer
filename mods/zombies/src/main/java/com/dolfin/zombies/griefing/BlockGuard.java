package com.dolfin.zombies.griefing;

import com.dolfin.zombies.ConfiguredZombie;
import com.dolfin.zombies.DolfinZombies;
import com.dolfin.zombies.griefing.GuardDecision.ClaimStatus;
import com.dolfin.zombies.griefing.GuardDecision.Reason;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.HashSet;
import java.util.Set;

/**
 * Shared protection for every zombie-driven world change (#4, reusable by later tickets): {@code
 * mobGriefing}, {@code mundosPermitidos}, {@code respetarClaims} (Open Parties and Claims,
 * optional), plus Ledger logging with the zombie id as the source. Server thread only.
 *
 * <p>Use {@link #puedeModificar} as a pre-check (e.g. before starting a break animation) and the
 * {@code try*} methods to perform the change: they re-check the guard, mutate, log to Ledger and
 * DEBUG.
 */
public final class BlockGuard {
    private BlockGuard() {}

    private static long generation = -1;
    private static Set<String> worlds = Set.of();
    private static boolean respetarClaims = true;

    /** Detects optional integrations once at startup and logs which providers are active. */
    public static void init() {
        var opac = OpacClaims.init();
        var ledger = LedgerLog.init();
        DolfinZombies.LOGGER.info(
                "event=integration name=opac state={}",
                opac.name().toLowerCase(java.util.Locale.ROOT));
        DolfinZombies.LOGGER.info(
                "event=integration name=ledger state={}",
                ledger.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static String claimsProvider() {
        return "opac:"
                + OpacClaims.state().name().toLowerCase(java.util.Locale.ROOT)
                + ",ledger:"
                + LedgerLog.state().name().toLowerCase(java.util.Locale.ROOT);
    }

    private static void refresh() {
        var snapshot = DolfinZombies.config().current();
        if (snapshot.generation() == generation) return;
        var general = snapshot.general();
        Set<String> w = new HashSet<>();
        general.getAsJsonArray("mundosPermitidos").forEach(e -> w.add(e.getAsString()));
        worlds = Set.copyOf(w);
        respetarClaims = general.get("respetarClaims").getAsBoolean();
        generation = snapshot.generation();
        DolfinZombies.LOGGER.info(
                "event=grief_guard_reloaded generation={} worlds={} respetarClaims={} providers={}",
                generation,
                worlds,
                respetarClaims,
                claimsProvider());
    }

    /** Null when the zombie may modify {@code pos}; otherwise the reason. No logging. */
    public static Reason check(ServerLevel level, BlockPos pos) {
        return decide(level, pos, true);
    }

    /**
     * Null when the zombie may attack an animal standing at {@code pos} (worlds + claims; not
     * mobGriefing).
     */
    public static Reason checkAttack(ServerLevel level, BlockPos pos) {
        return decide(level, pos, false);
    }

    private static Reason decide(ServerLevel level, BlockPos pos, boolean block) {
        refresh();
        return GuardDecision.decide(
                new GuardDecision.Env() {
                    public String dimension() {
                        return level.dimension().identifier().toString();
                    }

                    public boolean mobGriefing() {
                        return level.getGameRules().get(GameRules.MOB_GRIEFING);
                    }

                    public ClaimStatus claim() {
                        return OpacClaims.claimAt(level, pos);
                    }
                },
                worlds,
                respetarClaims,
                block);
    }

    /**
     * The ticket's {@code puedeModificar(level, pos, zombie)}: logs a DEBUG rejection with the
     * reason.
     */
    public static boolean puedeModificar(ServerLevel level, BlockPos pos, ConfiguredZombie zombie) {
        return puedeModificar(level, pos, zombie, "modify");
    }

    public static boolean puedeModificar(
            ServerLevel level, BlockPos pos, ConfiguredZombie zombie, String action) {
        Reason r = check(level, pos);
        if (r != null) rejected(zombie, pos, action, r);
        return r == null;
    }

    public static boolean puedeAtacar(
            ServerLevel level, BlockPos pos, ConfiguredZombie zombie, String action) {
        Reason r = checkAttack(level, pos);
        if (r != null) rejected(zombie, pos, action, r);
        return r == null;
    }

    /**
     * Vanilla 26.3 replaced {@code doFireTick} with {@code fire_spread_radius_around_player}; 0
     * disables fire spread.
     */
    public static boolean fireTickEnabled(ServerLevel level) {
        return level.getGameRules().get(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER) != 0;
    }

    public static void rejected(
            ConfiguredZombie zombie, BlockPos pos, String action, Reason reason) {
        if (DolfinZombies.LOGGER.isDebugEnabled())
            DolfinZombies.LOGGER.debug(
                    "event=grief_rejected id={} uuid={} action={} dimension={} pos={} block={}"
                            + " reason={}",
                    zombie.kind().id(),
                    zombie.getUUID(),
                    action,
                    zombie.level().dimension().identifier(),
                    pos.toShortString(),
                    blockId(zombie.level().getBlockState(pos)),
                    reason.id);
    }

    private static void performed(
            ConfiguredZombie zombie, BlockPos pos, String action, BlockState old, BlockState now) {
        if (DolfinZombies.LOGGER.isDebugEnabled())
            DolfinZombies.LOGGER.debug(
                    "event=grief_performed id={} uuid={} action={} dimension={} pos={} from={}"
                            + " to={} ledger={}",
                    zombie.kind().id(),
                    zombie.getUUID(),
                    action,
                    zombie.level().dimension().identifier(),
                    pos.toShortString(),
                    blockId(old),
                    blockId(now),
                    LedgerLog.state() == LedgerLog.State.PRESENT);
    }

    /**
     * Breaks {@code pos} (with crack/break effects) if the guard allows; logged to Ledger as a
     * break.
     */
    public static boolean tryBreak(
            ServerLevel level, ConfiguredZombie zombie, BlockPos pos, boolean drop, String action) {
        if (!puedeModificar(level, pos, zombie, action)) return false;
        BlockState old = level.getBlockState(pos);
        if (old.isAir()) return false;
        var be = level.getBlockEntity(pos);
        if (!level.destroyBlock(pos, drop, zombie)) return false;
        LedgerLog.broke(level, pos, old, be, zombie.kind().id());
        performed(zombie, pos, action, old, level.getBlockState(pos));
        return true;
    }

    /** Places {@code state} at {@code pos} if the guard allows; logged to Ledger as a placement. */
    public static boolean tryPlace(
            ServerLevel level,
            ConfiguredZombie zombie,
            BlockPos pos,
            BlockState state,
            String action) {
        if (!puedeModificar(level, pos, zombie, action)) return false;
        BlockState old = level.getBlockState(pos);
        if (!level.setBlock(pos, state, 3)) return false;
        // A replaced fluid (e.g. water under Congelado ice) is a change, a fresh block in air is a
        // placement.
        if (old.isAir()) LedgerLog.placed(level, pos, state, zombie.kind().id());
        else LedgerLog.changed(level, pos, old, state, null, zombie.kind().id());
        performed(zombie, pos, action, old, state);
        return true;
    }

    /**
     * Replaces the block at {@code pos} (e.g. farmland to dirt) if the guard allows; logged to
     * Ledger as a change.
     */
    public static boolean tryChange(
            ServerLevel level,
            ConfiguredZombie zombie,
            BlockPos pos,
            BlockState state,
            String action) {
        if (!puedeModificar(level, pos, zombie, action)) return false;
        BlockState old = level.getBlockState(pos);
        var be = level.getBlockEntity(pos);
        if (!level.setBlock(pos, state, 3)) return false;
        LedgerLog.changed(level, pos, old, state, be, zombie.kind().id());
        performed(zombie, pos, action, old, state);
        return true;
    }

    public static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }
}
