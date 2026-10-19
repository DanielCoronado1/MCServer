package com.dolfin.space;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Configuration-phase payload advertised by {@code dolfinspace} clients so the server can detect
 * missing-mod connections before world entry and display a clear modpack installation message.
 */
public record SpaceHandshakePayload(String version) implements CustomPacketPayload {
    public static final Type<SpaceHandshakePayload> TYPE = new Type<>(DolfinSpace.id("handshake"));
    public static final StreamCodec<FriendlyByteBuf, SpaceHandshakePayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUtf(payload.version()),
                    buf -> new SpaceHandshakePayload(buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
