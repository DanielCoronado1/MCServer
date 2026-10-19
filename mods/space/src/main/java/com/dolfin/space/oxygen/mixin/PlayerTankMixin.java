package com.dolfin.space.oxygen.mixin;

import com.dolfin.space.oxygen.*;

import com.dolfin.space.DolfinSpace;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerTankMixin implements TankAccess {
    @Unique private TankContainer dolfinspace$tank;
    @Override public TankContainer dolfinspace$tank() {
        if (dolfinspace$tank == null) dolfinspace$tank = new TankContainer();
        return dolfinspace$tank;
    }
    @Inject(method="readAdditionalSaveData", at=@At("TAIL"))
    private void loadTank(ValueInput input, CallbackInfo ci) {
        dolfinspace$tank().setItem(0, input.read("dolfinspace_tank", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        DolfinSpace.LOGGER.debug("event=tank_loaded player={} tank={}", ((Player)(Object)this).getUUID(), dolfinspace$tank().getItem(0));
    }
    @Inject(method="addAdditionalSaveData", at=@At("TAIL"))
    private void saveTank(ValueOutput output, CallbackInfo ci) {
        output.store("dolfinspace_tank", ItemStack.OPTIONAL_CODEC, dolfinspace$tank().getItem(0));
        DolfinSpace.LOGGER.debug("event=tank_saved player={}", ((Player)(Object)this).getUUID());
    }
}
