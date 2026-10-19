package com.dolfin.space.mobs.mixin;

import com.dolfin.space.mobs.Parasite;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class ParasiteHeadMixin {
    // 26.3 rejects non-serializable vehicles even for forced riding; players are noSave.
    @Redirect(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z"))
    private boolean allowParasitePlayerVehicle(
            EntityType<?> type, Entity vehicle, boolean force, boolean emitEvent) {
        return (Object) this instanceof Parasite && vehicle instanceof Player
                || type.canSerialize();
    }

    @Inject(method = "getPassengerAttachmentPoint", at = @At("HEAD"), cancellable = true)
    private void headPosition(
            Entity passenger,
            EntityDimensions dimensions,
            float scale,
            CallbackInfoReturnable<Vec3> ci) {
        if ((Object) this instanceof Player player && passenger instanceof Parasite)
            ci.setReturnValue(new Vec3(0, player.getEyeHeight() + .05, 0));
    }
}
