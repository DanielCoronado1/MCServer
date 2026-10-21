package com.dolfin.creepers.dungeon.estadisticas;

import com.dolfin.creepers.dungeon.modelo.Dificultad;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Consulta de tiempos de partida para {@code /dungeon info} y {@code /dungeon top}. La
 * implementación por defecto de #22 es {@code fin.EstadisticasRecords}, que lee los récords
 * guardados en el mundo; {@code Dungeons.usarEstadisticas} la sustituye.
 */
public interface Estadisticas {
    /** Un tiempo en la clasificación de una dungeon; {@code jugador} lista a todo el grupo. */
    record EntradaTop(String jugador, long segundos, Dificultad dificultad) {}

    /** Sin datos: ningún jugador tiene tiempos y la clasificación está vacía. */
    Estadisticas SIN_DATOS =
            new Estadisticas() {
                @Override
                public Map<Dificultad, Long> mejoresTiemposSegundos(
                        UUID jugador, String dungeonId) {
                    return Map.of();
                }

                @Override
                public List<EntradaTop> top(
                        String dungeonId, Optional<Dificultad> dificultad, int limite) {
                    return List.of();
                }
            };

    /** Mejor tiempo de victoria del jugador en cada dificultad que ha ganado en la dungeon. */
    Map<Dificultad, Long> mejoresTiemposSegundos(UUID jugador, String dungeonId);

    /**
     * Los {@code limite} mejores tiempos de la dungeon, del más rápido al más lento; sin dificultad
     * se mezclan todas.
     */
    List<EntradaTop> top(String dungeonId, Optional<Dificultad> dificultad, int limite);

    /** Mejor tiempo de victoria del jugador en la dungeon, en cualquier dificultad. */
    default OptionalLong mejorTiempoSegundos(UUID jugador, String dungeonId) {
        return mejoresTiemposSegundos(jugador, dungeonId).values().stream()
                .mapToLong(Long::longValue)
                .min();
    }

    /** Los {@code limite} mejores tiempos de la dungeon en cualquier dificultad. */
    default List<EntradaTop> top(String dungeonId, int limite) {
        return top(dungeonId, Optional.empty(), limite);
    }
}
