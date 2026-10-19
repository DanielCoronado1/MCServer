package com.dolfin.space;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

import java.util.function.Consumer;

/**
 * Verifies during the configuration phase that connecting clients have {@code dolfinspace}
 * installed and disconnects unmodded clients with a clear modpack message.
 */
public final class SpaceModpackCheck {
    public static final Identifier CHECK_PHASE = DolfinSpace.id("modpack_check");
    private static final ConfigurationTask.Type TASK_TYPE =
            new ConfigurationTask.Type(DolfinSpace.id("modpack_check").toString());
    private static final Identifier FABRIC_REGISTRY_SYNC_ID =
            Identifier.fromNamespaceAndPath("fabric", "registry/sync");

    /** Result of evaluating whether a connecting client satisfies the modpack requirement. */
    public record HandshakeDecision(boolean allowed, String disconnectMessage) {}

    private SpaceModpackCheck() {}

    /** Registers the configuration payload and early connection check listener. */
    public static void init() {
        PayloadTypeRegistry.clientboundConfiguration()
                .register(SpaceHandshakePayload.TYPE, SpaceHandshakePayload.CODEC);
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.addPhaseOrdering(
                CHECK_PHASE, Event.DEFAULT_PHASE);
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register(
                CHECK_PHASE, SpaceModpackCheck::onBeforeConfigure);
    }

    /**
     * Pure evaluation of whether a client connection should be allowed or disconnected with a clear
     * modpack requirement message.
     */
    public static HandshakeDecision evaluateHandshake(
            boolean modRequiredByConfig, boolean clientHasSpaceChannel, String configuredMessage) {
        if (!modRequiredByConfig || clientHasSpaceChannel) {
            return new HandshakeDecision(true, "");
        }
        String msg =
                (configuredMessage == null || configuredMessage.isBlank())
                        ? SpaceConfig.GeneralSection.defaults().mensajeSinMod()
                        : configuredMessage;
        return new HandshakeDecision(false, msg);
    }

    /**
     * Returns the custom disconnect component if this configuration listener lacks {@code
     * dolfinspace}.
     */
    public static Component missingModMessageIfApplicable(
            ServerConfigurationPacketListenerImpl handler) {
        SpaceConfig cfg = DolfinSpace.config();
        if (cfg == null) {
            return null;
        }
        SpaceConfig.GeneralSection general = cfg.current().general();
        if (!general.activo()) {
            return null;
        }
        boolean hasSpaceMod =
                ServerConfigurationNetworking.canSend(handler, SpaceHandshakePayload.TYPE);
        HandshakeDecision decision =
                evaluateHandshake(
                        general.exigirModEnCliente(), hasSpaceMod, general.mensajeSinMod());
        return decision.allowed() ? null : Component.literal(decision.disconnectMessage());
    }

    private static void onBeforeConfigure(
            ServerConfigurationPacketListenerImpl handler, MinecraftServer server) {
        SpaceConfig.Snapshot snapshot = DolfinSpace.config().current();
        if (!snapshot.general().activo()) {
            return;
        }

        boolean hasSpaceMod =
                ServerConfigurationNetworking.canSend(handler, SpaceHandshakePayload.TYPE);
        HandshakeDecision decision =
                evaluateHandshake(
                        snapshot.general().exigirModEnCliente(),
                        hasSpaceMod,
                        snapshot.general().mensajeSinMod());
        var profile = handler.getOwner();

        if (decision.allowed()) {
            DolfinSpace.LOGGER.debug(
                    "event=client_mod_verified player={} uuid={}", profile.name(), profile.id());
            return;
        }

        DolfinSpace.LOGGER.info(
                "event=client_missing_mod player={} uuid={} reason=missing_dolfinspace_channel",
                profile.name(),
                profile.id());

        Component reason = Component.literal(decision.disconnectMessage());
        if (ServerConfigurationNetworking.canSend(handler, FABRIC_REGISTRY_SYNC_ID)) {
            handler.addTask(
                    new ConfigurationTask() {
                        @Override
                        public void start(Consumer<Packet<?>> sender) {
                            handler.disconnect(reason);
                        }

                        @Override
                        public Type type() {
                            return TASK_TYPE;
                        }
                    });
        } else {
            handler.disconnect(reason);
        }
    }
}
