package com.dolfin.creepers.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Acceso a campos privados del creeper (la mecha y si esta cargado). */
@Mixin(Creeper.class)
public interface CreeperAccessor {
	@Accessor("maxSwell")
	void dolfin$setMecha(int ticks);

	@Accessor("swell")
	void dolfin$setHinchado(int ticks);

	@Accessor("DATA_IS_POWERED")
	static EntityDataAccessor<Boolean> dolfin$datoCargado() {
		throw new AssertionError();
	}
}
