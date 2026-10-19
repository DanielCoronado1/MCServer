package com.dolfin.space.machines;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class MachineBlock extends BaseEntityBlock {
    public final MachineRecipes.Kind kind;

    public MachineBlock(Properties p, MachineRecipes.Kind kind) {
        super(p);
        this.kind = kind;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AbstractSpaceMachineBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
        if (!l.isClientSide()
                && l.getBlockEntity(p) instanceof AbstractSpaceMachineBlockEntity machine)
            player.openMenu(machine);
        return InteractionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level l, BlockState s, BlockEntityType<T> type) {
        return l.isClientSide()
                ? null
                : createTickerHelper(
                        type,
                        MachinesModule.TYPE,
                        (world, pos, state, machine) -> machine.serverTick());
    }
}
