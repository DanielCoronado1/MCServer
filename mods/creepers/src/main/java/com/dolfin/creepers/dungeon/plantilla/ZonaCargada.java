package com.dolfin.creepers.dungeon.plantilla;

import com.dolfin.creepers.DolfinCreepers;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.function.Consumer;

/**
 * Carga los chunks de una zona (con sus entidades) mientras se guarda o reinicia su plantilla, sin
 * simularlos: los mobs no se mueven ni caen bloques durante el trabajo. El ticket no persiste: una
 * caída a medias no deja nada cargado.
 */
final class ZonaCargada {
    /** Radio 1: el chunk queda accesible y sus entidades se cargan. */
    private static final int RADIO = 1;

    /** Ticks máximos esperando a que carguen las entidades antes de seguir con un aviso. */
    private static final int ESPERA_MAXIMA_TICKS = 30 * 20;

    private final ServerLevel nivel;
    private final Caja zona;
    private long limite = -1;

    ZonaCargada(ServerLevel nivel, Caja zona) {
        this.nivel = nivel;
        this.zona = zona;
    }

    /** Pide la carga de todos los chunks de la zona. */
    void cargar() {
        recorrer(
                pos ->
                        nivel.getChunkSource()
                                .addTicketWithRadius(ContenidoPlantillas.TICKET_ZONA, pos, RADIO));
    }

    /** Suelta los chunks; se descargan cuando el juego lo decida. */
    void soltar() {
        recorrer(
                pos ->
                        nivel.getChunkSource()
                                .removeTicketWithRadius(
                                        ContenidoPlantillas.TICKET_ZONA, pos, RADIO));
    }

    /**
     * {@code true} cuando todos los chunks y sus entidades están cargados, o tras {@link
     * #ESPERA_MAXIMA_TICKS} (con aviso: el trabajo sigue, pero puede no ver alguna entidad).
     */
    boolean lista(String dungeon) {
        long ahora = nivel.getServer().getTickCount();
        if (limite < 0) {
            limite = ahora + ESPERA_MAXIMA_TICKS;
        }
        if (todasCargadas()) {
            return true;
        }
        if (ahora >= limite) {
            DolfinCreepers.LOGGER.warn(
                    "event=dungeon_zone_load_timeout dungeon={} ticks={}",
                    dungeon,
                    ESPERA_MAXIMA_TICKS);
            return true;
        }
        return false;
    }

    private boolean todasCargadas() {
        for (int x = zona.minX() >> 4; x <= zona.maxX() >> 4; x++) {
            for (int z = zona.minZ() >> 4; z <= zona.maxZ() >> 4; z++) {
                if (!nivel.hasChunk(x, z) || !nivel.areEntitiesLoaded(ChunkPos.pack(x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    private void recorrer(Consumer<ChunkPos> accion) {
        for (int x = zona.minX() >> 4; x <= zona.maxX() >> 4; x++) {
            for (int z = zona.minZ() >> 4; z <= zona.maxZ() >> 4; z++) {
                accion.accept(new ChunkPos(x, z));
            }
        }
    }
}
