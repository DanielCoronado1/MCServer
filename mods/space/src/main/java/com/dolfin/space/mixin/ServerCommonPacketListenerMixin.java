package com.dolfin.space.mixin;

import com.dolfin.space.SpaceModpackCheck;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerMixin {
    @ModifyVariable(
            method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private DisconnectionDetails dolfinspace$overrideMissingModpackReason(DisconnectionDetails details) {
        if ((Object) this instanceof ServerConfigurationPacketListenerImpl cfg) {
            Component custom = SpaceModpackCheck.missingModMessageIfApplicable(cfg);
            if (custom != null) {
                return new DisconnectionDetails(custom);
            }
        }
        return details;
    }
}
