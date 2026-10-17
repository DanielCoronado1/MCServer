package com.dolfin.creepers.mixin;

import com.dolfin.creepers.VarianteCreeper;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cuando un creeper va a explotar, si es una de nuestras variantes usamos
 * su explosion propia en lugar de la normal.
 */
@Mixin(Creeper.class)
public abstract class CreeperExplosionMixin {
	@Inject(method = "explodeCreeper", at = @At("HEAD"), cancellable = true)
	private void dolfin$explosionPropia(CallbackInfo info) {
		if ((Object) this instanceof VarianteCreeper variante) {
			variante.explotar();
			info.cancel();
		}
	}
}
