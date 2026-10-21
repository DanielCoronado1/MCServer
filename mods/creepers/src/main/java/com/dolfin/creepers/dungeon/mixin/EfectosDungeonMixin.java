package com.dolfin.creepers.dungeon.mixin;

import com.dolfin.creepers.dungeon.reglas.ReglasDungeon;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ningún jugador aplica efectos dañinos a otro dentro de la dimensión de dungeons (pociones
 * arrojadizas, nubes persistentes, flechas con efecto); los beneficiosos sí.
 */
@Mixin(LivingEntity.class)
public abstract class EfectosDungeonMixin {
    @Inject(
            method =
                    "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;"
                            + "Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void dolfincreepers$sinEfectosDaninosEntreJugadores(
            MobEffectInstance efecto, Entity origen, CallbackInfoReturnable<Boolean> cir) {
        if (!ReglasDungeon.permiteEfecto((LivingEntity) (Object) this, efecto, origen)) {
            cir.setReturnValue(false);
        }
    }
}
