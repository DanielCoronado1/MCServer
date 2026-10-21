package com.dolfin.creepers.dungeon.plantilla;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@code dolfincreepers:marcador_mob}: bloque invisible y sin colisión que marca dónde aparecen
 * mobs al empezar una partida. Su {@link MarcadorMobEntidad} dice qué y cuántos. Solo se ve su
 * contorno con el propio objeto en la mano, como el bloque de luz.
 *
 * <p>Se coloca con {@code /setblock x y z dolfincreepers:marcador_mob{entidad:"minecraft:zombie",
 * cantidad:3}} o con el objeto y luego {@code /data merge block}. Clic con la mano vacía en
 * creativo muestra su configuración.
 */
public final class MarcadorMob extends BaseEntityBlock {
    public MarcadorMob(Properties propiedades) {
        super(propiedades);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState estado) {
        return new MarcadorMobEntidad(pos, estado);
    }

    @Override
    protected RenderShape getRenderShape(BlockState estado) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(
            BlockState estado, BlockGetter nivel, BlockPos pos, CollisionContext contexto) {
        return contexto.isHoldingItem(asItem()) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState estado, Level nivel, BlockPos pos, Player jugador, BlockHitResult golpe) {
        if (!jugador.isCreative()) {
            return InteractionResult.PASS;
        }
        if (!nivel.isClientSide() && nivel.getBlockEntity(pos) instanceof MarcadorMobEntidad m) {
            jugador.sendSystemMessage(Component.literal(m.descripcion()));
        }
        return InteractionResult.SUCCESS;
    }
}
