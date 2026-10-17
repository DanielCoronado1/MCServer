package com.dolfin.zombies;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record BloodMoonPayload(boolean red) implements CustomPacketPayload {
 public static final Type<BloodMoonPayload> TYPE=new Type<>(DolfinZombies.id("bloodmoon"));
 public static final StreamCodec<RegistryFriendlyByteBuf,BloodMoonPayload> CODEC=StreamCodec.of((b,p)->b.writeBoolean(p.red()),b->new BloodMoonPayload(b.readBoolean()));
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
