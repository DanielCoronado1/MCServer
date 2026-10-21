package com.dolfin.lobby;

import com.dolfin.space.KeplerDimension;
import com.dolfin.space.travel.FlightController;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;

import org.slf4j.*;

public final class DolfinLobby implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("dolfinlobby");
    private static StationSessions sessions;

    public static StationSessions sessions() {
        return sessions;
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, environment) -> {
                    dispatcher.register(
                            Commands.literal("dolfinlobby")
                                    .requires(
                                            s ->
                                                    s.permissions()
                                                            .hasPermission(
                                                                    Permissions
                                                                            .COMMANDS_GAMEMASTER))
                                    .then(
                                            Commands.literal("provisionar")
                                                    .executes(
                                                            c -> {
                                                                try {
                                                                    if (sessions == null)
                                                                        throw new IllegalStateException(
                                                                                "Configuración no"
                                                                                    + " disponible");
                                                                    new WorldProvisioner(
                                                                                    c.getSource()
                                                                                            .getServer(),
                                                                                    sessions.config)
                                                                            .provision();
                                                                    c.getSource()
                                                                            .sendSuccess(
                                                                                    () ->
                                                                                            Component
                                                                                                    .literal(
                                                                                                            "Mundos"
                                                                                                                + " preparados;"
                                                                                                                + " ejecuta"
                                                                                                                + " dolfinlobby"
                                                                                                                + " instalar"
                                                                                                                + " para"
                                                                                                                + " abrir"
                                                                                                                + " la estación."),
                                                                                    true);
                                                                    return 1;
                                                                } catch (Exception e) {
                                                                    LOGGER.error(
                                                                            "event=provision_failed",
                                                                            e);
                                                                    return 0;
                                                                }
                                                            }))
                                    .then(
                                            Commands.literal("instalar")
                                                    .executes(
                                                            c -> {
                                                                try {
                                                                    StationLayout.install(
                                                                            c.getSource()
                                                                                    .getServer(),
                                                                            sessions.config);
                                                                    c.getSource()
                                                                            .sendSuccess(
                                                                                    () ->
                                                                                            Component
                                                                                                    .literal(
                                                                                                            "Estación"
                                                                                                                + " funcional"
                                                                                                                + " instalada."
                                                                                                                + " Rutas"
                                                                                                                + " abiertas."),
                                                                                    true);
                                                                    return 1;
                                                                } catch (Exception e) {
                                                                    LOGGER.error(
                                                                            "event=station_install_failed",
                                                                            e);
                                                                    return 0;
                                                                }
                                                            })));
                    dispatcher.register(
                            Commands.literal("lobby")
                                    .executes(
                                            c -> {
                                                if (sessions != null)
                                                    sessions.lobby(
                                                            c.getSource().getPlayerOrException());
                                                return 1;
                                            }));
                    dispatcher.register(
                            Commands.literal("exhibicion")
                                    .executes(
                                            c -> {
                                                help(c.getSource().getPlayerOrException());
                                                return 1;
                                            })
                                    .then(
                                            Commands.literal("lista")
                                                    .executes(
                                                            c -> {
                                                                help(
                                                                        c.getSource()
                                                                                .getPlayerOrException());
                                                                return 1;
                                                            }))
                                    .then(
                                            Commands.literal("volver")
                                                    .executes(
                                                            c -> {
                                                                sessions.showcase(
                                                                        c.getSource()
                                                                                .getPlayerOrException());
                                                                return 1;
                                                            }))
                                    .then(
                                            Commands.literal("espacio")
                                                    .executes(
                                                            c -> {
                                                                sessions.rocket(
                                                                        c.getSource()
                                                                                .getPlayerOrException());
                                                                return 1;
                                                            }))
                                    .then(
                                            Commands.literal("despegar")
                                                    .executes(
                                                            c -> {
                                                                sessions.launchOrReturn(
                                                                        c.getSource()
                                                                                .getPlayerOrException());
                                                                return 1;
                                                            }))
                                    .then(
                                            Commands.literal("probar")
                                                    .then(
                                                            Commands.argument(
                                                                            "numero",
                                                                            IntegerArgumentType
                                                                                    .integer(1, 25))
                                                                    .executes(
                                                                            c -> {
                                                                                sessions.demo(
                                                                                        c.getSource()
                                                                                                .getPlayerOrException(),
                                                                                        IntegerArgumentType
                                                                                                        .getInteger(
                                                                                                                c,
                                                                                                                "numero")
                                                                                                - 1);
                                                                                return 1;
                                                                            }))));
                });
        ServerLifecycleEvents.SERVER_STARTED.register(
                server -> {
                    try {
                        var config =
                                LobbyConfig.load(
                                        FabricLoader.getInstance()
                                                .getConfigDir()
                                                .resolve("dolfin-lobby.json"));
                        sessions = new StationSessions(server, config);
                        FlightController.riderBlocked =
                                (ship, id) ->
                                        sessions != null && sessions.recoveryBlocked(ship, id);
                        FlightController.demoLanding =
                                (ship, world) ->
                                        ship.entityTags().contains("dolfin_demo_ship")
                                                        && KeplerDimension.isKepler(
                                                                world.dimension())
                                                ? StationLayout.OUTPOST_PAD.above()
                                                : null;
                        com.dolfin.zombies.SpawnRules.demonstrationWorld = config.showcase();
                        com.dolfin.zombies.griefing.BlockGuard.demonstrationArea =
                                (world, pos) -> StationProtection.mutableArena(world, pos);
                        LOGGER.info(
                                "event=lobby_ready routes={}",
                                sessions.ready() ? "open" : "closed");
                    } catch (Exception e) {
                        sessions = null;
                        LOGGER.error("event=lobby_disabled", e);
                        throw new IllegalStateException(
                                "La estación no puede abrirse: revisa configuración y sesiones. Se"
                                    + " preservan los datos sin exponer equipo de demostración.",
                                e);
                    }
                });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> sessions = null);
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> {
                    if (sessions != null) sessions.login(handler.player);
                });
        ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    if (sessions != null) sessions.tick();
                });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) ->
                        !(entity instanceof ServerPlayer p
                                && StationProtection.isolated(p)
                                && !sessions.vulnerable(p)));
        ServerLivingEntityEvents.ALLOW_DEATH.register(
                (entity, source, amount) -> {
                    if (entity instanceof ServerPlayer p && StationProtection.isolated(p)) {
                        p.setHealth(p.getMaxHealth());
                        sessions.lobby(p);
                        return false;
                    }
                    return true;
                });
        UseBlockCallback.EVENT.register(
                (player, world, hand, hit) -> {
                    if (world.isClientSide()) return InteractionResult.PASS;
                    if (StationProtection.isolated(player)) {
                        if (player instanceof ServerPlayer p
                                && world.dimension()
                                        .equals(LobbyConfig.key(sessions.config.showcase()))) {
                            int index = StationLayout.button(hit.getBlockPos());
                            if (index >= 0) {
                                sessions.demo(p, index);
                                return InteractionResult.SUCCESS;
                            }
                            if (hit.getBlockPos()
                                    .equals(new net.minecraft.core.BlockPos(30, 130, -5))) {
                                sessions.rocket(p);
                                return InteractionResult.SUCCESS;
                            }
                        }
                        return InteractionResult.FAIL;
                    }
                    return StationProtection.protectedAt(world, hit.getBlockPos())
                            ? InteractionResult.FAIL
                            : InteractionResult.PASS;
                });
        PlayerBlockBreakEvents.BEFORE.register(
                (world, player, pos, state, entity) ->
                        !StationProtection.isolated(player)
                                && !StationProtection.protectedAt(world, pos));
        UseEntityCallback.EVENT.register(
                (player, world, hand, entity, hit) -> {
                    if (world.isClientSide()) return InteractionResult.PASS;
                    if (StationProtection.isolated(player))
                        return entity.entityTags().contains("dolfin_demo_" + player.getUUID())
                                        && entity instanceof com.dolfin.space.ships.SpaceshipEntity
                                        && !player.isShiftKeyDown()
                                ? InteractionResult.PASS
                                : InteractionResult.FAIL;
                    return StationProtection.demoEntity(entity)
                            ? InteractionResult.FAIL
                            : InteractionResult.PASS;
                });
        AttackEntityCallback.EVENT.register(
                (player, world, hand, entity, hit) -> {
                    if (world.isClientSide()) return InteractionResult.PASS;
                    if (StationProtection.isolated(player))
                        return sessions.combat((ServerPlayer) player)
                                        && StationProtection.mutableArena(
                                                world, entity.blockPosition())
                                        && !(entity
                                                instanceof net.minecraft.world.entity.player.Player)
                                ? InteractionResult.PASS
                                : InteractionResult.FAIL;
                    return StationProtection.protectedAt(world, entity.blockPosition())
                                    || StationProtection.demoEntity(entity)
                            ? InteractionResult.FAIL
                            : InteractionResult.PASS;
                });
        ServerEntityEvents.ENTITY_LOAD.register(
                (e, world) -> {
                    if (StationLayout.obsoleteLabel(e)) {
                        e.discard();
                        return;
                    }
                    if (e instanceof net.minecraft.world.entity.Mob
                            && StationProtection.protectedAt(world, e.blockPosition())
                            && !StationProtection.mutableArena(world, e.blockPosition())) {
                        e.discard();
                        return;
                    }
                    if ((e instanceof ItemEntity || e instanceof ExperienceOrb)
                            && StationProtection.protectedAt(world, e.blockPosition())) e.discard();
                });
    }

    private static void help(ServerPlayer p) {
        StationSessions.message(
                p,
                "/exhibicion probar <1–25> inicia combate · /exhibicion espacio prepara nave ·"
                        + " /exhibicion despegar · /exhibicion volver · /lobby");
        for (int i = 0; i < ExhibitCatalog.ALL.size(); i++)
            StationSessions.message(
                    p,
                    (i + 1)
                            + ". "
                            + ExhibitCatalog.ALL.get(i).name()
                            + " — "
                            + ExhibitCatalog.ALL.get(i).description());
    }
}
