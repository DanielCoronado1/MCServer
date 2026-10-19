package com.dolfin.space.oxygen;

import com.dolfin.space.DolfinSpace;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OxygenPayload(int remaining, int capacity, boolean low) implements CustomPacketPayload {
    public static final Type<OxygenPayload> TYPE = new Type<>(DolfinSpace.id("oxygen"));
    public static final StreamCodec<RegistryFriendlyByteBuf,OxygenPayload> CODEC = StreamCodec.of(
        (b,p) -> { b.writeVarInt(p.remaining); b.writeVarInt(p.capacity); b.writeBoolean(p.low); },
        b -> new OxygenPayload(b.readVarInt(), b.readVarInt(), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
