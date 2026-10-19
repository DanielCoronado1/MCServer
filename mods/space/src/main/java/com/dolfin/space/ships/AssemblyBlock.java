package com.dolfin.space.ships;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class AssemblyBlock extends BaseEntityBlock {
    public AssemblyBlock(Properties p) {
        super(p);
    }

    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new AssemblyBlockEntity(p, s);
    }

    protected InteractionResult useWithoutItem(
            BlockState s, Level l, BlockPos p, Player player, BlockHitResult h) {
        if (!l.isClientSide() && l.getBlockEntity(p) instanceof AssemblyBlockEntity be)
            player.openMenu(be);
        return InteractionResult.SUCCESS;
    }
}
