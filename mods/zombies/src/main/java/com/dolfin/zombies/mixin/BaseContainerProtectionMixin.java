package com.dolfin.zombies.mixin;

import com.dolfin.zombies.custody.CustodyManager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(BaseContainerBlockEntity.class)
public abstract class BaseContainerProtectionMixin extends BlockEntity implements Container {
    protected BaseContainerProtectionMixin(
            BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        if (level != null
                && !level.isClientSide()
                && CustodyManager.findProtectedChest(level, worldPosition).isPresent()) {
            return false;
        }
        return Container.super.canTakeItem(target, slot, stack);
    }
}
