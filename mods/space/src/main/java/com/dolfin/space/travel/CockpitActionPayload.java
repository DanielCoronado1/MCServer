package com.dolfin.space.travel;

import com.dolfin.space.DolfinSpace;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CockpitActionPayload(int action) implements CustomPacketPayload {
    public static final Type<CockpitActionPayload> TYPE =
            new Type<>(DolfinSpace.id("cockpit_action"));
    public static final StreamCodec<FriendlyByteBuf, CockpitActionPayload> CODEC =
            StreamCodec.of(
                    (b, p) -> b.writeVarInt(p.action()),
                    b -> new CockpitActionPayload(b.readVarInt()));

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
