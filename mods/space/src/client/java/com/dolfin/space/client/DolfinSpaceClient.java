package com.dolfin.space.client;

import com.dolfin.space.DolfinSpace;
import com.dolfin.space.SpaceHandshakePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.resources.Identifier;

/**
 * Client entrypoint for {@code dolfinspace}: registers configuration-phase handshake support and
 * tracks client sky rendering state for {@code dolfinspace:kepler}.
 */
public final class DolfinSpaceClient implements ClientModInitializer {
    public static final Identifier EARTH_SPRITE = DolfinSpace.id("earth");
    public static volatile boolean renderingKeplerSky;

    @Override
    public void onInitializeClient() {
        ClientConfigurationNetworking.registerGlobalReceiver(
                SpaceHandshakePayload.TYPE,
                (payload, context) -> DolfinSpace.LOGGER.debug(
                        "event=client_handshake_received serverModVersion={}",
                        payload.version()
                )
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> renderingKeplerSky = false);
        DolfinSpace.LOGGER.info("event=client_startup modId={} earthSprite={}", DolfinSpace.MOD_ID, EARTH_SPRITE);
    }
}
