package com.dolfin.creepers.dungeon.botin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Almacén en memoria de los contenedores individuales por jugador en cada cofre de dungeon (#18).
 * El contenido se aísla por partida y por jugador, y se borra al terminar la partida.
 */
public final class AlmacenBotin {
    private final Map<UUID, Map<BlockPos, Map<UUID, SimpleContainer>>> porPartida = new HashMap<>();

    /** Obtiene el contenedor del jugador en esa posición de la partida si ya fue generado. */
    public synchronized Optional<SimpleContainer> obtener(
            UUID partidaId, BlockPos pos, UUID jugadorId) {
        Map<BlockPos, Map<UUID, SimpleContainer>> cofres = porPartida.get(partidaId);
        if (cofres == null) {
            return Optional.empty();
        }
        Map<UUID, SimpleContainer> porJugador = cofres.get(pos);
        if (porJugador == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(porJugador.get(jugadorId));
    }

    /** Guarda el contenedor para el jugador en esa posición y partida. */
    public synchronized void guardar(
            UUID partidaId, BlockPos pos, UUID jugadorId, SimpleContainer contenedor) {
        porPartida
                .computeIfAbsent(partidaId, k -> new HashMap<>())
                .computeIfAbsent(pos, k -> new HashMap<>())
                .put(jugadorId, contenedor);
    }

    /**
     * Verdadero si el jugador ya abrió este cofre antes en la partida y su contenedor está vacío.
     */
    public synchronized boolean yaVaciado(UUID partidaId, BlockPos pos, UUID jugadorId) {
        return obtener(partidaId, pos, jugadorId).map(SimpleContainer::isEmpty).orElse(false);
    }

    /** Borra todos los contenedores de una partida al terminar. */
    public synchronized void limpiarPartida(UUID partidaId) {
        porPartida.remove(partidaId);
    }

    /** Número de partidas con botín activo en memoria. */
    public synchronized int partidasActivas() {
        return porPartida.size();
    }

    /**
     * Normaliza la posición para cofres dobles: siempre devuelve la coordenada mínima entre ambos
     * bloques del cofre para que ambos lados accedan al mismo inventario.
     */
    public static BlockPos normalizarPosicion(BlockState estado, BlockPos pos) {
        if (!estado.hasProperty(ChestBlock.TYPE)) {
            return pos;
        }
        ChestType tipo = estado.getValue(ChestBlock.TYPE);
        if (tipo == ChestType.SINGLE) {
            return pos;
        }
        Direction direccionConectada = ChestBlock.getConnectedDirection(estado);
        BlockPos vecina = pos.relative(direccionConectada);
        if (pos.compareTo(vecina) <= 0) {
            return pos;
        }
        return vecina;
    }

    /** Cantidad de casillas según si el bloque es un cofre doble o individual. */
    public static int capacidad(BlockState estado) {
        if (estado.hasProperty(ChestBlock.TYPE)
                && estado.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return 54;
        }
        return 27;
    }
}
