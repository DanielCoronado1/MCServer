package com.dolfin.creepers.dungeon.fin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.fin.Marca.Integrante;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class TablaRecordsTest {
    static final Integrante ANA = new Integrante(UUID.randomUUID(), "Ana");
    static final Integrante BETO = new Integrante(UUID.randomUUID(), "Beto");

    TablaRecords tabla = new TablaRecords();

    Marca marca(String dungeon, Dificultad dificultad, long segundos, long fecha, Integrante... m) {
        Marca marca = new Marca(dungeon, dificultad, List.of(m), segundos * 1000, fecha);
        tabla.anadir(marca);
        return marca;
    }

    @Test
    void topOrdenaPorTiempoYDesempataPorFecha() {
        Marca lenta = marca("cripta", Dificultad.MEDIO, 900, 1, ANA);
        Marca rapida = marca("cripta", Dificultad.MEDIO, 600, 5, ANA, BETO);
        Marca igualAntigua = marca("cripta", Dificultad.FACIL, 600, 2, BETO);
        marca("torre", Dificultad.MEDIO, 1, 1, ANA);
        assertEquals(
                List.of(igualAntigua, rapida, lenta), tabla.top("cripta", Optional.empty(), 10));
        assertEquals(
                List.of(rapida, lenta), tabla.top("cripta", Optional.of(Dificultad.MEDIO), 10));
        assertEquals(List.of(igualAntigua), tabla.top("cripta", Optional.empty(), 1));
        assertTrue(tabla.top("nada", Optional.empty(), 10).isEmpty());
    }

    @Test
    void topLimitaADiezEntreMuchasMarcas() {
        for (int i = 0; i < 25; i++) {
            marca("cripta", Dificultad.FACIL, 1000 - i, i, ANA);
        }
        List<Marca> top = tabla.top("cripta", Optional.empty(), 10);
        assertEquals(10, top.size());
        assertEquals(976_000, top.getFirst().tiempoMillis());
        assertEquals(985_000, top.getLast().tiempoMillis());
    }

    @Test
    void recordSoloSiMejoraEstrictamenteODificultadNueva() {
        assertTrue(tabla.esRecord("cripta", Dificultad.MEDIO, 999_000));
        marca("cripta", Dificultad.MEDIO, 600, 1, ANA);
        assertTrue(tabla.esRecord("cripta", Dificultad.MEDIO, 599_999));
        assertFalse(tabla.esRecord("cripta", Dificultad.MEDIO, 600_000));
        assertTrue(tabla.esRecord("cripta", Dificultad.HARDCORE, 900_000));
        assertTrue(tabla.esRecord("torre", Dificultad.MEDIO, 900_000));
    }

    @Test
    void primeraVictoriaEsPorJugadorDungeonYDificultad() {
        marca("cripta", Dificultad.MEDIO, 600, 1, ANA);
        assertFalse(tabla.esPrimeraVictoria(ANA.id(), "cripta", Dificultad.MEDIO));
        assertTrue(tabla.esPrimeraVictoria(BETO.id(), "cripta", Dificultad.MEDIO));
        assertTrue(tabla.esPrimeraVictoria(ANA.id(), "cripta", Dificultad.HARDCORE));
        assertTrue(tabla.esPrimeraVictoria(ANA.id(), "torre", Dificultad.MEDIO));
    }

    @Test
    void mejorTiempoPersonalPorDificultad() {
        marca("cripta", Dificultad.MEDIO, 900, 1, ANA);
        marca("cripta", Dificultad.MEDIO, 700, 2, ANA, BETO);
        marca("cripta", Dificultad.HARDCORE, 1200, 3, ANA);
        marca("cripta", Dificultad.FACIL, 300, 4, BETO);
        assertEquals(
                Map.of(Dificultad.MEDIO, 700_000L, Dificultad.HARDCORE, 1_200_000L),
                tabla.mejoresTiempos(ANA.id(), "cripta"));
        assertTrue(tabla.mejoresTiempos(UUID.randomUUID(), "cripta").isEmpty());
    }

    @Test
    void jsonIdaYVueltaConservaLasMarcas() {
        marca("cripta", Dificultad.HARDCORE, 600, 1_700_000_000_000L, ANA, BETO);
        marca("torre", Dificultad.FACIL, 90, 1_700_000_500_000L, ANA);
        List<String> avisos = new ArrayList<>();
        TablaRecords leida = TablaRecords.deJson(tabla.aJson(), avisos::add);
        assertTrue(avisos.isEmpty());
        assertEquals(
                tabla.top("cripta", Optional.empty(), 5), leida.top("cripta", Optional.empty(), 5));
        assertEquals(
                tabla.top("torre", Optional.empty(), 5), leida.top("torre", Optional.empty(), 5));
    }

    @Test
    void jsonDescartaMarcasInvalidasSinPerderLasBuenas() {
        marca("cripta", Dificultad.FACIL, 600, 1, ANA);
        JsonObject raiz = tabla.aJson();
        JsonObject mala = new JsonObject();
        mala.addProperty("dungeon", "cripta");
        mala.addProperty("dificultad", "imposible");
        raiz.getAsJsonArray("marcas").add(mala);
        List<String> avisos = new ArrayList<>();
        TablaRecords leida = TablaRecords.deJson(raiz, avisos::add);
        assertEquals(1, leida.tamano());
        assertEquals(1, avisos.size());
        assertEquals(0, TablaRecords.deJson(new JsonObject(), avisos::add).tamano());
    }
}
