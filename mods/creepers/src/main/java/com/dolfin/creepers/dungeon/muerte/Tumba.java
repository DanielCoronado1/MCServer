package com.dolfin.creepers.dungeon.muerte;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Objetos que un jugador perdió al morir en dificultad media, guardados en un bloque {@code
 * dolfincreepers:tumba} dentro de la zona. El contenido vive aquí (en {@link DatosMuerte}) y no en
 * el bloque: así sobrevive a una caída del servidor y a que alguien rompa el bloque en creativo.
 *
 * @param id identificador propio de la tumba
 * @param dueno jugador que murió
 * @param nombreDueno nombre del dueño para el título del cofre
 * @param partida partida en la que murió; solo sus miembros pueden abrirla
 * @param pos bloque de la tumba en la dimensión de dungeons
 * @param contenido inventario de 54 casillas; ver {@link #vigilarCambios}
 */
public record Tumba(
        UUID id,
        UUID dueno,
        String nombreDueno,
        UUID partida,
        BlockPos pos,
        SimpleContainer contenido) {
    /** Casillas del cofre de la tumba: seis filas, de sobra para un inventario completo. */
    public static final int CASILLAS = 54;

    /** Distancia máxima en bloques para seguir viendo el cofre abierto. */
    private static final double ALCANCE = 8;

    public static final Codec<Tumba> CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            UUIDUtil.STRING_CODEC
                                                    .fieldOf("id")
                                                    .forGetter(Tumba::id),
                                            UUIDUtil.STRING_CODEC
                                                    .fieldOf("dueno")
                                                    .forGetter(Tumba::dueno),
                                            Codec.STRING
                                                    .fieldOf("nombreDueno")
                                                    .forGetter(Tumba::nombreDueno),
                                            UUIDUtil.STRING_CODEC
                                                    .fieldOf("partida")
                                                    .forGetter(Tumba::partida),
                                            BlockPos.CODEC.fieldOf("pos").forGetter(Tumba::pos),
                                            ItemStack.CODEC
                                                    .listOf()
                                                    .optionalFieldOf("objetos", List.of())
                                                    .forGetter(Tumba::objetos))
                                    .apply(i, Tumba::nueva));

    /** Crea una tumba con {@code objetos} (como mucho {@value #CASILLAS} pilas). */
    public static Tumba nueva(
            UUID id,
            UUID dueno,
            String nombreDueno,
            UUID partida,
            BlockPos pos,
            List<ItemStack> objetos) {
        SimpleContainer contenido = new ContenidoTumba(pos);
        for (int i = 0; i < objetos.size() && i < CASILLAS; i++) {
            contenido.setItem(i, objetos.get(i));
        }
        return new Tumba(id, dueno, nombreDueno, partida, pos, contenido);
    }

    /** Pilas no vacías que quedan dentro. */
    public List<ItemStack> objetos() {
        List<ItemStack> objetos = new ArrayList<>();
        for (int i = 0; i < contenido.getContainerSize(); i++) {
            if (!contenido.getItem(i).isEmpty()) {
                objetos.add(contenido.getItem(i));
            }
        }
        return objetos;
    }

    /** Llama a {@code alCambiar} cada vez que cambia el contenido (para guardarlo). */
    public void vigilarCambios(Runnable alCambiar) {
        ((ContenidoTumba) contenido).alCambiar = alCambiar;
    }

    /** Vacía la tumba y cierra el cofre a quien lo tenga abierto; devuelve lo que había. */
    public List<ItemStack> retirar() {
        List<ItemStack> objetos = List.copyOf(objetos());
        ((ContenidoTumba) contenido).retirada = true;
        contenido.clearContent();
        return objetos;
    }

    /** Cofre que se cierra solo al alejarse o al retirar la tumba. */
    private static final class ContenidoTumba extends SimpleContainer {
        private final BlockPos pos;
        private boolean retirada;
        private Runnable alCambiar = () -> {};

        ContenidoTumba(BlockPos pos) {
            super(CASILLAS);
            this.pos = pos;
        }

        @Override
        public boolean stillValid(Player jugador) {
            return !retirada && pos.closerToCenterThan(jugador.position(), ALCANCE);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            alCambiar.run();
        }
    }
}
