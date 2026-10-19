package com.dolfin.space.travel;

import com.dolfin.space.DolfinSpace;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CameraShakePayload(long inicio, float intensidad) implements CustomPacketPayload {
    public static final Type<CameraShakePayload> TYPE = new Type<>(DolfinSpace.id("camera_shake"));
    public static final StreamCodec<FriendlyByteBuf, CameraShakePayload> CODEC =
            StreamCodec.of(
                    (b, p) -> {
                        b.writeLong(p.inicio());
                        b.writeFloat(p.intensidad());
                    },
                    b -> new CameraShakePayload(b.readLong(), b.readFloat()));

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
