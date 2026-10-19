package com.dolfin.space.mixin;

import com.dolfin.space.DolfinSpace;
import com.dolfin.space.KeplerDimension;

import net.minecraft.server.level.ServerLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelSpawnersMixin {
    @Inject(method = "tickCustomSpawners", at = @At("HEAD"), cancellable = true)
    private void dolfinspace$skipCustomVanillaSpawnersInKepler(
            boolean spawnEnemies, CallbackInfo ci) {
        if (!DolfinSpace.config().current().general().activo()) {
            return;
        }
        ServerLevel self = (ServerLevel) (Object) this;
        if (KeplerDimension.isKepler(self.dimension())
                && DolfinSpace.config().current().dimension().sinMobsVanilla()) {
            ci.cancel();
        }
    }
}
