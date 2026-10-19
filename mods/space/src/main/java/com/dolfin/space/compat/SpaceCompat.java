package com.dolfin.space.compat;

import com.dolfin.space.*;
import com.dolfin.space.ships.SpaceshipEntity;
import com.dolfin.space.travel.FlightLedger;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.UUID;

public final class SpaceCompat {
    public static void init() {
        SpaceClaims.init();
        DolfinSpace.LOGGER.info(
                "event=integration name=luckperms state={} provider={}",
                net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("luckperms")
                        ? "available"
                        : "absent",
                SpacePermissions.provider());
        ServerLifecycleEvents.SERVER_STARTED.register(
                server -> {
                    SpaceLedgerIntegration.init();
                    DolfinSpace.LOGGER.info(
                            "event=space_pvp state={} protection=opac_native",
                            server.overworld().getGameRules().get(GameRules.PVP)
                                    ? "enabled"
                                    : "disabled_by_admin");
                });
        PlayerBlockBreakEvents.BEFORE.register(
                (world, player, pos, state, be) ->
                        !(world instanceof ServerLevel level)
                                || !KeplerDimension.isKepler(world.dimension())
                                || SpaceClaims.allowed(
                                        player, level, pos, CompatPolicy.Action.BREAK));
        PlayerBlockBreakEvents.AFTER.register(
                (world, player, pos, state, be) -> {
                    if (world instanceof ServerLevel level)
                        SpaceLedgerIntegration.record(
                                SpaceLedgerIntegration.Action.BREAK,
                                player,
                                level,
                                pos,
                                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (victim, source, amount) -> {
                    if (!(victim instanceof ServerPlayer target)
                            || !KeplerDimension.isKepler(target.level().dimension())
                            || !(source.getEntity() instanceof ServerPlayer attacker)) return true;
                    return CompatPolicy.pvp(
                            target.level().getGameRules().get(GameRules.PVP),
                            attacker.canHarmPlayer(target),
                            SpaceClaims.pvp(attacker, target, source.getDirectEntity()));
                });
    }

    public static boolean container(Player player, ServerLevel world, BlockPos pos) {
        return SpaceClaims.allowed(player, world, pos, CompatPolicy.Action.CONTAINER);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> recoveryTree() {
        return RecoveryCommandTree.build(
                SpacePermissions::isAdmin,
                GameProfileArgument.gameProfile(),
                ctx -> {
                    int count = 0;
                    for (var profile : GameProfileArgument.getGameProfiles(ctx, "jugador"))
                        count += recover(ctx.getSource(), profile.id());
                    final int result = count;
                    ctx.getSource()
                            .sendSuccess(
                                    () -> Component.translatable("space.command.recovery", result),
                                    true);
                    return count;
                });
    }

    public static int recover(CommandSourceStack source, UUID owner) {
        if (!SpacePermissions.isAdmin(source)) return 0;
        var server = source.getServer();
        var ledger = FlightLedger.get(server);
        for (var level : server.getAllLevels())
            for (var entity : level.getAllEntities())
                if (entity instanceof SpaceshipEntity ship && owner.equals(ship.owner))
                    ledger.track(ship);
        int count = RecoveryRequests.queue(true, owner, ledger.entries);
        if (count > 0) ledger.setDirty();
        DolfinSpace.LOGGER.info(
                "event=ship_admin_recovery_queued actor={} owner={} count={} fuel_on_return=0",
                source.getTextName(),
                owner,
                count);
        return count;
    }

    private SpaceCompat() {}
}
