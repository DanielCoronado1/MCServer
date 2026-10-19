package com.dolfin.space.mixin;

import com.dolfin.space.DolfinSpace;
import com.dolfin.space.KeplerDimension;
import com.dolfin.space.SpacePermissions;
import com.dolfin.space.SpaceTravelAccess;
import com.dolfin.space.SpaceTravelAccess.Decision;
import com.dolfin.space.SpaceTravelAccess.TravelMechanism;
import com.dolfin.space.SpaceTravelAccess.TravelRequest;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.LookAt;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(TeleportCommand.class)
public abstract class TeleportCommandMixin {
    @Inject(method = "performTeleport", at = @At("HEAD"), cancellable = true)
    private static void dolfinspace$guardKeplerTeleport(
            CommandSourceStack source,
            Entity entity,
            ServerLevel targetLevel,
            double x,
            double y,
            double z,
            Set<Relative> relatives,
            float yRot,
            float xRot,
            LookAt lookAt,
            CallbackInfo ci) {
        boolean viaExecuteInKepler =
                SpaceTravelAccess.consumeExecuteInKeplerActive()
                        && KeplerDimension.isKepler(source.getLevel().dimension());
        if (!DolfinSpace.config().current().general().activo()) {
            return;
        }
        if (!KeplerDimension.isKepler(targetLevel.dimension())) {
            return;
        }

        TravelMechanism mechanism =
                viaExecuteInKepler
                        ? TravelMechanism.ADMIN_EXECUTE_IN_KEPLER
                        : TravelMechanism.DIRECT_TP_COMMAND;

        String fromDim = entity.level().dimension().identifier().toString();
        String toDim = targetLevel.dimension().identifier().toString();
        boolean isAdmin = SpacePermissions.isAdmin(source);

        Decision decision =
                SpaceTravelAccess.evaluateTravel(
                        new TravelRequest(
                                fromDim,
                                toDim,
                                mechanism,
                                isAdmin,
                                SpaceTravelAccess.isAuthorized(entity)),
                        DolfinSpace.config().current().dimension());

        if (!decision.allowed()) {
            DolfinSpace.LOGGER.info(
                    "event=kepler_travel_blocked actor={} entity={} uuid={} from={} to={}"
                            + " mechanism={} reason={}",
                    source.getTextName(),
                    entity.getName().getString(),
                    entity.getUUID(),
                    fromDim,
                    toDim,
                    mechanism,
                    decision.reason());
            source.sendFailure(Component.translatable("dolfinspace.travel.blocked"));
            ci.cancel();
            return;
        }

        DolfinSpace.LOGGER.info(
                "event=kepler_travel_allowed actor={} entity={} uuid={} from={} to={} mechanism={}"
                        + " reason={}",
                source.getTextName(),
                entity.getName().getString(),
                entity.getUUID(),
                fromDim,
                toDim,
                mechanism,
                decision.reason());
    }

    @Redirect(
            method = "performTeleport",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/Entity;teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z"))
    private static boolean dolfinspace$authorizeTeleportTo(
            Entity entity,
            ServerLevel targetLevel,
            double x,
            double y,
            double z,
            Set<Relative> relatives,
            float yRot,
            float xRot,
            boolean resetCamera) {
        if (KeplerDimension.isKepler(targetLevel.dimension())) {
            return SpaceTravelAccess.supplyAuthorized(
                    entity,
                    "admin_execute_in_kepler",
                    () ->
                            entity.teleportTo(
                                    targetLevel, x, y, z, relatives, yRot, xRot, resetCamera));
        }
        return entity.teleportTo(targetLevel, x, y, z, relatives, yRot, xRot, resetCamera);
    }
}
