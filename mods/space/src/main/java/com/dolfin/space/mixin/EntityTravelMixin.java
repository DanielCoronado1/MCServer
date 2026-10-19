package com.dolfin.space.mixin;

import com.dolfin.space.DolfinSpace;
import com.dolfin.space.KeplerDimension;
import com.dolfin.space.SpaceTravelAccess;
import com.dolfin.space.SpaceTravelAccess.Decision;
import com.dolfin.space.SpaceTravelAccess.TravelMechanism;
import com.dolfin.space.SpaceTravelAccess.TravelRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityTravelMixin {
    @Shadow
    public PortalProcessor portalProcess;

    @Shadow
    public abstract Level level();

    @Inject(method = "setAsInsidePortal", at = @At("HEAD"), cancellable = true)
    private void dolfinspace$blockPortalEntryInKepler(Portal portal, BlockPos pos, CallbackInfo ci) {
        if (!DolfinSpace.config().current().general().activo()) {
            return;
        }
        if (KeplerDimension.isKepler(this.level().dimension())
                && DolfinSpace.config().current().dimension().bloquearPortales()) {
            this.portalProcess = null;
            ci.cancel();
        }
    }

    @Inject(method = "teleportToPortalDestination", at = @At("HEAD"), cancellable = true)
    private void dolfinspace$blockPortalTeleport(
            ServerLevel currentLevel,
            TeleportTransition transition,
            CallbackInfo ci
    ) {
        if (!DolfinSpace.config().current().general().activo() || transition == null) {
            return;
        }
        String fromDim = currentLevel.dimension().identifier().toString();
        String toDim = transition.newLevel().dimension().identifier().toString();
        Decision decision = SpaceTravelAccess.evaluateTravel(
                new TravelRequest(fromDim, toDim, TravelMechanism.PORTAL, false, false),
                DolfinSpace.config().current().dimension()
        );
        if (!decision.allowed()) {
            Entity self = (Entity) (Object) this;
            DolfinSpace.LOGGER.info(
                    "event=kepler_portal_blocked entity={} uuid={} from={} to={} reason={}",
                    self.getName().getString(),
                    self.getUUID(),
                    fromDim,
                    toDim,
                    decision.reason()
            );
            this.portalProcess = null;
            ci.cancel();
        }
    }

    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
    private void dolfinspace$guardEntityTeleport(
            TeleportTransition transition,
            CallbackInfoReturnable<Entity> cir
    ) {
        if (!DolfinSpace.config().current().general().activo() || transition == null) {
            return;
        }
        Entity self = (Entity) (Object) this;
        String fromDim = this.level().dimension().identifier().toString();
        String toDim = transition.newLevel().dimension().identifier().toString();
        Decision decision = SpaceTravelAccess.evaluateTravel(
                new TravelRequest(
                        fromDim,
                        toDim,
                        TravelMechanism.OTHER_TELEPORT,
                        false,
                        SpaceTravelAccess.isAuthorized(self)
                ),
                DolfinSpace.config().current().dimension()
        );
        if (!decision.allowed()) {
            DolfinSpace.LOGGER.info(
                    "event=kepler_travel_blocked entity={} uuid={} from={} to={} mechanism=OTHER_TELEPORT reason={}",
                    self.getName().getString(),
                    self.getUUID(),
                    fromDim,
                    toDim,
                    decision.reason()
            );
            cir.setReturnValue(null);
        }
    }
}
