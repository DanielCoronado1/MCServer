package com.dolfin.zombies.mixin;

import com.dolfin.zombies.custody.CustodyManager;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerExplosion.class)
public abstract class ServerExplosionProtectionMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method="interactWithBlocks",at=@At("HEAD"))
    private void filterProtectedChests(List<BlockPos> positions,CallbackInfo ci){
        positions.removeIf(pos->CustodyManager.findProtectedChest(this.level,pos).isPresent());
    }
}
