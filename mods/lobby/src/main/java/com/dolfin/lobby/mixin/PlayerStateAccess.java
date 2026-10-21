package com.dolfin.lobby.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerPlayer.class)
public interface PlayerStateAccess {
    @Invoker("setShoulderEntityLeft")
    void dolfinlobby$left(net.minecraft.nbt.CompoundTag tag);

    @Invoker("setShoulderEntityRight")
    void dolfinlobby$right(net.minecraft.nbt.CompoundTag tag);

    @Invoker("readAdditionalSaveData")
    void dolfinlobby$restore(ValueInput input);
}
