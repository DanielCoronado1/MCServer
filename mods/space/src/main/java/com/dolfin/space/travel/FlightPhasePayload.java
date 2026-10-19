package com.dolfin.space.travel;

import com.dolfin.space.DolfinSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FlightPhasePayload(int phase) implements CustomPacketPayload {
  public static final Type<FlightPhasePayload> TYPE = new Type<>(DolfinSpace.id("flight_phase"));
  public static final StreamCodec<FriendlyByteBuf, FlightPhasePayload> CODEC =
      StreamCodec.of(
          (b, p) -> b.writeVarInt(p.phase()), b -> new FlightPhasePayload(b.readVarInt()));

  public Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
