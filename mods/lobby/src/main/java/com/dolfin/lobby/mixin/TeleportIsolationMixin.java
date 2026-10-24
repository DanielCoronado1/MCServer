package com.dolfin.lobby.mixin;

import com.dolfin.lobby.StationProtection;
import com.dolfin.lobby.StationSessions;
import com.dolfin.space.SpaceTravelAccess;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Isolated players move only through the station router or an authorized Space flight. Blocks
 * third-party warps (Waystones scrolls, inventory button) that would bypass the escrow.
 */
@Mixin(ServerPlayer.class)
public abstract class TeleportIsolationMixin {
    @Inject(
            method =
                    "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("HEAD"),
            cancellable = true)
    private void guard(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
        var p = (ServerPlayer) (Object) this;
        if (transition == null
                || !StationProtection.isolated(p)
                || StationProtection.routing()
                || SpaceTravelAccess.isAuthorized(p)) return;
        var target = transition.newLevel();
        if (target == p.level() && StationProtection.station(target)) return;
        StationSessions.message(
                p,
                "Durante la demostración solo puedes salir con /lobby o los marcos de la"
                        + " estación.");
        cir.setReturnValue(null);
    }
}
