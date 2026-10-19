package com.dolfin.creepers.mixin;

import com.dolfin.creepers.VarianteCreeper;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Quien fue lanzado por un Creeper Saltarin no recibe dano al caer (solo esa caida). */
@Mixin(LivingEntity.class)
public abstract class CaidaMixin {
    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void dolfin$sinDanoTrasLanzamiento(
            double distancia,
            float multiplicador,
            DamageSource origen,
            CallbackInfoReturnable<Boolean> info) {
        LivingEntity entidad = (LivingEntity) (Object) this;
        if (entidad.removeTag(VarianteCreeper.ETIQUETA_LANZADO)) {
            info.setReturnValue(false);
        }
    }
}
