package com.dolfin.creepers.dungeon.estadisticas;

import com.dolfin.creepers.dungeon.modelo.Dificultad;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Consulta de tiempos de partida para {@code /dungeon info} y {@code /dungeon top}. Punto de
 * enganche de #22; mientras no haya otra implementación ({@code Dungeons.usarEstadisticas}) no hay
 * datos.
 */
public interface Estadisticas {
    /** Un tiempo en la clasificación de una dungeon. */
    record EntradaTop(String jugador, long segundos, Dificultad dificultad) {}

    /** Sin datos: ningún jugador tiene tiempos y la clasificación está vacía. */
    Estadisticas SIN_DATOS =
            new Estadisticas() {
                @Override
                public OptionalLong mejorTiempoSegundos(UUID jugador, String dungeonId) {
                    return OptionalLong.empty();
                }

                @Override
                public List<EntradaTop> top(String dungeonId, int limite) {
                    return List.of();
                }
            };

    /** Mejor tiempo de victoria del jugador en la dungeon. */
    OptionalLong mejorTiempoSegundos(UUID jugador, String dungeonId);

    /** Los {@code limite} mejores tiempos de la dungeon, del más rápido al más lento. */
    List<EntradaTop> top(String dungeonId, int limite);
}
