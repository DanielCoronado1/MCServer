package com.dolfin.creepers.dungeon.mixin;

import com.dolfin.creepers.dungeon.reglas.ReglasDungeon;

import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Los élitros no despegan dentro de la dimensión de dungeons. */
@Mixin(Player.class)
public abstract class ElitrosDungeonMixin {
    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void dolfincreepers$sinPlaneoEnDungeons(CallbackInfoReturnable<Boolean> cir) {
        if (!ReglasDungeon.permitePlaneo((Player) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
