package com.dolfin.creepers.dungeon.fin;

import com.dolfin.creepers.dungeon.estadisticas.Estadisticas;
import com.dolfin.creepers.dungeon.modelo.Dificultad;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** {@link Estadisticas} sobre los récords guardados en el mundo (#22). */
public final class EstadisticasRecords implements Estadisticas {
    private final HistorialDungeons historial;

    public EstadisticasRecords(HistorialDungeons historial) {
        this.historial = historial;
    }

    @Override
    public Map<Dificultad, Long> mejoresTiemposSegundos(UUID jugador, String dungeonId) {
        Map<Dificultad, Long> segundos = new EnumMap<>(Dificultad.class);
        historial
                .records()
                .mejoresTiempos(jugador, dungeonId)
                .forEach((d, ms) -> segundos.put(d, ms / 1000));
        return segundos;
    }

    @Override
    public List<EntradaTop> top(String dungeonId, Optional<Dificultad> dificultad, int limite) {
        return historial.records().top(dungeonId, dificultad, limite).stream()
                .map(
                        m ->
                                new EntradaTop(
                                        TextosFin.lista(
                                                m.miembros().stream()
                                                        .map(Marca.Integrante::nombre)
                                                        .toList()),
                                        m.tiempoMillis() / 1000,
                                        m.dificultad()))
                .toList();
    }
}
