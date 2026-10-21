package com.dolfin.creepers.dungeon.fin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.estadisticas.Estadisticas.EntradaTop;
import com.dolfin.creepers.dungeon.fin.Marca.Integrante;
import com.dolfin.creepers.dungeon.modelo.Dificultad;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

class EstadisticasRecordsTest {
    static final Integrante ANA = new Integrante(UUID.randomUUID(), "Ana");
    static final Integrante BETO = new Integrante(UUID.randomUUID(), "Beto");

    @TempDir Path mundo;

    EstadisticasRecords estadisticas() {
        HistorialDungeons historial = new HistorialDungeons(msg -> {});
        historial.cargar(mundo);
        historial.records().anadir(marca(Dificultad.HARDCORE, 754_900, ANA, BETO));
        historial.records().anadir(marca(Dificultad.FACIL, 300_000, ANA));
        return new EstadisticasRecords(historial);
    }

    static Marca marca(Dificultad dificultad, long millis, Integrante... miembros) {
        return new Marca("cripta", dificultad, List.of(miembros), millis, 1);
    }

    @Test
    void topListaAlGrupoYFiltraPorDificultad() {
        EstadisticasRecords e = estadisticas();
        assertEquals(
                List.of(
                        new EntradaTop("Ana", 300, Dificultad.FACIL),
                        new EntradaTop("Ana y Beto", 754, Dificultad.HARDCORE)),
                e.top("cripta", 10));
        assertEquals(
                List.of(new EntradaTop("Ana y Beto", 754, Dificultad.HARDCORE)),
                e.top("cripta", Optional.of(Dificultad.HARDCORE), 10));
    }

    @Test
    void mejorTiempoEsPorDificultadYElMenorEnGeneral() {
        EstadisticasRecords e = estadisticas();
        assertEquals(
                Map.of(Dificultad.FACIL, 300L, Dificultad.HARDCORE, 754L),
                e.mejoresTiemposSegundos(ANA.id(), "cripta"));
        assertEquals(OptionalLong.of(300), e.mejorTiempoSegundos(ANA.id(), "cripta"));
        assertEquals(OptionalLong.of(754), e.mejorTiempoSegundos(BETO.id(), "cripta"));
        assertTrue(e.mejorTiempoSegundos(UUID.randomUUID(), "cripta").isEmpty());
    }
}
