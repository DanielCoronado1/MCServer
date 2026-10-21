package com.dolfin.creepers.dungeon.mixin;

import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Portal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ningún portal (Nether, End, gateway) funciona dentro de la dimensión de dungeons: se sale solo
 * por la entrada/salida segura. Desde fuera ningún portal vanilla lleva a ella.
 */
@Mixin(Entity.class)
public abstract class PortalesDungeonMixin {
    @Inject(method = "setAsInsidePortal", at = @At("HEAD"), cancellable = true)
    private void dolfincreepers$sinPortalesEnDungeons(
            Portal portal, BlockPos pos, CallbackInfo ci) {
        if (DimensionDungeons.es(((Entity) (Object) this).level())) {
            ci.cancel();
        }
    }
}
