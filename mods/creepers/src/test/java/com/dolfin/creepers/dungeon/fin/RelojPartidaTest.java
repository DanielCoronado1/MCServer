package com.dolfin.creepers.dungeon.fin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.fin.RelojPartida.Avance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

class RelojPartidaTest {
    /** Avanza de segundo en segundo y devuelve los avisos con el segundo en que salieron. */
    static List<String> recorrer(RelojPartida reloj, long hasta) {
        List<String> eventos = new ArrayList<>();
        for (long t = 1; t <= hasta; t++) {
            Avance a = reloj.avanzar(t);
            a.avisoMinutos().ifPresent(m -> eventos.add("aviso" + m));
            if (a.agotado()) {
                eventos.add("agotado");
            }
            if (a.avisoMinutos().isPresent() || a.agotado()) {
                eventos.set(eventos.size() - 1, eventos.getLast() + "@" + t);
            }
        }
        return eventos;
    }

    @Test
    void avisaA10Y5Y1MinutosYAgotaALos45() {
        assertEquals(
                List.of("aviso10@2100", "aviso5@2400", "aviso1@2640", "agotado@2700"),
                recorrer(new RelojPartida(45), 3000));
    }

    @Test
    void unLimiteMenorOIgualQueUnAvisoNoLoEmite() {
        assertEquals(
                List.of("aviso5@300", "aviso1@540", "agotado@600"),
                recorrer(new RelojPartida(10), 700));
        assertEquals(List.of("agotado@60"), recorrer(new RelojPartida(1), 100));
    }

    @Test
    void unSaltoGrandeEmiteSoloElAvisoMasUrgenteUnaVez() {
        RelojPartida reloj = new RelojPartida(45);
        Avance salto = reloj.avanzar(2650);
        assertEquals(OptionalInt.of(1), salto.avisoMinutos());
        assertFalse(salto.agotado());
        assertEquals(OptionalInt.empty(), reloj.avanzar(2651).avisoMinutos());
        assertTrue(reloj.avanzar(9999).agotado());
        assertFalse(reloj.avanzar(10_000).agotado());
    }

    @Test
    void restanteNuncaEsNegativo() {
        RelojPartida reloj = new RelojPartida(2);
        assertEquals(120, reloj.restanteSegundos(0));
        assertEquals(20, reloj.restanteSegundos(100));
        assertEquals(0, reloj.restanteSegundos(500));
    }
}
