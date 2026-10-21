package com.dolfin.creepers.dungeon.muerte;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.Dungeons;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bloque {@code dolfincreepers:tumba}: lápida que marca una {@link Tumba}. Irrompible (como la roca
 * madre), resiste explosiones, los pistones no la mueven y no suelta nada. Al usarla abre el cofre
 * de la tumba si el jugador pertenece a la partida ({@link Tumbas#abrir}). No tiene block entity:
 * el contenido vive en {@link DatosMuerte}.
 */
public final class BloqueTumba extends Block {
    private static final VoxelShape FORMA =
            Shapes.or(Block.box(1, 0, 1, 15, 2, 15), Block.box(3, 2, 6, 13, 14, 10));

    private static BloqueTumba bloque;

    private BloqueTumba(BlockBehaviour.Properties propiedades) {
        super(propiedades);
    }

    /** Registra el bloque; se llama una vez al iniciar el mod. */
    public static void registrar() {
        ResourceKey<Block> clave = ResourceKey.create(Registries.BLOCK, DolfinCreepers.id("tumba"));
        bloque =
                Registry.register(
                        BuiltInRegistries.BLOCK,
                        clave,
                        new BloqueTumba(
                                BlockBehaviour.Properties.of()
                                        .setId(clave)
                                        .mapColor(MapColor.STONE)
                                        .sound(SoundType.STONE)
                                        .strength(-1.0F, 3_600_000.0F)
                                        .pushReaction(PushReaction.IMMOVEABLE)
                                        .noLootTable()
                                        .noOcclusion()));
    }

    public static BloqueTumba bloque() {
        return bloque;
    }

    public static boolean es(BlockState estado) {
        return bloque != null && estado.is(bloque);
    }

    @Override
    protected VoxelShape getShape(
            BlockState estado, BlockGetter nivel, BlockPos pos, CollisionContext contexto) {
        return FORMA;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState estado, Level nivel, BlockPos pos, Player jugador, BlockHitResult golpe) {
        if (jugador instanceof ServerPlayer servidor) {
            Dungeons.tumbas().abrir(servidor, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
