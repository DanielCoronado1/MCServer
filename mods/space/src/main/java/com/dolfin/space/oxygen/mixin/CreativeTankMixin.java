package com.dolfin.space.oxygen.mixin;

import com.dolfin.space.oxygen.*;

import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CreativeTankMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void creativeTank(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        int index = packet.slotNum();
        if (index < 0
                || index >= player.inventoryMenu.slots.size()
                || !(player.inventoryMenu.slots.get(index).container instanceof TankContainer))
            return;
        PacketUtils.ensureRunningOnSameThread(
                packet, (ServerGamePacketListenerImpl) (Object) this, player.level());
        if (player.isCreative()
                && (packet.itemStack().isEmpty() || TankItem.isTank(packet.itemStack()))
                && packet.itemStack().getCount() <= 1) {
            ((TankAccess) player).dolfinspace$tank().setItem(0, packet.itemStack().copy());
        }
        player.inventoryMenu.broadcastChanges();
        ci.cancel();
    }
}
