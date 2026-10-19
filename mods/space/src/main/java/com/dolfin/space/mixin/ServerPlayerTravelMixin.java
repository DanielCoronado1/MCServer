package com.dolfin.space.mixin;

import com.dolfin.space.DolfinSpace;
import com.dolfin.space.SpaceTravelAccess;
import com.dolfin.space.SpaceTravelAccess.Decision;
import com.dolfin.space.SpaceTravelAccess.TravelMechanism;
import com.dolfin.space.SpaceTravelAccess.TravelRequest;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTravelMixin {
    @Inject(
            method =
                    "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("HEAD"),
            cancellable = true)
    private void dolfinspace$guardPlayerTeleport(
            TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
        if (!DolfinSpace.config().current().general().activo() || transition == null) {
            return;
        }
        ServerPlayer self = (ServerPlayer) (Object) this;
        String fromDim = self.level().dimension().identifier().toString();
        String toDim = transition.newLevel().dimension().identifier().toString();

        Decision decision =
                SpaceTravelAccess.evaluateTravel(
                        new TravelRequest(
                                fromDim,
                                toDim,
                                TravelMechanism.OTHER_TELEPORT,
                                false,
                                SpaceTravelAccess.isAuthorized(self)),
                        DolfinSpace.config().current().dimension());

        if (!decision.allowed()) {
            DolfinSpace.LOGGER.info(
                    "event=kepler_travel_blocked player={} uuid={} from={} to={}"
                            + " mechanism=OTHER_TELEPORT reason={}",
                    self.getName().getString(),
                    self.getUUID(),
                    fromDim,
                    toDim,
                    decision.reason());
            if (DolfinSpace.config().current().general().mensajesChat()) {
                self.sendSystemMessage(Component.translatable("dolfinspace.travel.blocked"));
            }
            cir.setReturnValue(null);
        }
    }
}
