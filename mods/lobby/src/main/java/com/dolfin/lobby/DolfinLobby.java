package com.dolfin.lobby;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-owned home for provisioning and, in the next stage, isolated demo sessions. */
public final class DolfinLobby implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("dolfinlobby");
    private static LobbyConfig config;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, environment) ->
                        dispatcher.register(
                                Commands.literal("dolfinlobby")
                                        .requires(
                                                source ->
                                                        source.permissions()
                                                                .hasPermission(
                                                                        Permissions
                                                                                .COMMANDS_GAMEMASTER))
                                        .then(
                                                Commands.literal("provisionar")
                                                        .executes(
                                                                context -> {
                                                                    try {
                                                                        if (config == null)
                                                                            throw new IllegalStateException(
                                                                                    "Configuración"
                                                                                        + " no disponible");
                                                                        new WorldProvisioner(
                                                                                        context.getSource()
                                                                                                .getServer(),
                                                                                        config)
                                                                                .provision();
                                                                        context.getSource()
                                                                                .sendSuccess(
                                                                                        () ->
                                                                                                Component
                                                                                                        .literal(
                                                                                                                "Estación"
                                                                                                                    + " preparada;"
                                                                                                                    + " rutas"
                                                                                                                    + " de demostración"
                                                                                                                    + " cerradas."),
                                                                                        true);
                                                                        return 1;
                                                                    } catch (Exception exception) {
                                                                        LOGGER.error(
                                                                                "event=provision_failed",
                                                                                exception);
                                                                        context.getSource()
                                                                                .sendFailure(
                                                                                        Component
                                                                                                .literal(
                                                                                                        "No se"
                                                                                                            + " pudo"
                                                                                                            + " preparar"
                                                                                                            + " la estación:"
                                                                                                            + " "
                                                                                                                + exception
                                                                                                                        .getMessage()));
                                                                        return 0;
                                                                    }
                                                                }))));
        ServerLifecycleEvents.SERVER_STARTED.register(this::started);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> config = null);
    }

    private void started(MinecraftServer server) {
        try {
            config =
                    LobbyConfig.load(
                            FabricLoader.getInstance().getConfigDir().resolve("dolfin-lobby.json"));
            LOGGER.info(
                    "event=lobby_ready lobby={} showcase={} routes=closed",
                    config.lobby(),
                    config.showcase());
        } catch (Exception exception) {
            config = null;
            LOGGER.error("event=lobby_disabled reason=config_invalid", exception);
        }
    }
}
