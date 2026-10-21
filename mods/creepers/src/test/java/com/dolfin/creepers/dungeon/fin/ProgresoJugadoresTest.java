package com.dolfin.creepers.dungeon.fin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class ProgresoJugadoresTest {
    static final UUID ANA = UUID.randomUUID();
    static final UUID BETO = UUID.randomUUID();

    ProgresoJugadores progreso = new ProgresoJugadores();

    @Test
    void cooldownRestanteBajaConElTiempoYNuncaEsNegativo() {
        progreso.iniciarCooldown(ANA, "cripta", 10_000);
        assertEquals(4_000, progreso.cooldownRestanteMillis(ANA, "cripta", 6_000));
        assertEquals(0, progreso.cooldownRestanteMillis(ANA, "cripta", 20_000));
        assertEquals(0, progreso.cooldownRestanteMillis(ANA, "torre", 0));
        assertEquals(0, progreso.cooldownRestanteMillis(BETO, "cripta", 0));
    }

    @Test
    void completadasSonPorJugadorYDungeon() {
        progreso.marcarCompletada(ANA, "cripta");
        assertTrue(progreso.completo(ANA, "cripta"));
        assertFalse(progreso.completo(ANA, "torre"));
        assertFalse(progreso.completo(BETO, "cripta"));
    }

    @Test
    void lasCabezasPendientesSeRecogenUnaVez() {
        progreso.pendienteCabeza(ANA, "cripta");
        assertEquals(Set.of("cripta"), progreso.recogerCabezas(ANA));
        assertTrue(progreso.recogerCabezas(ANA).isEmpty());
    }

    @Test
    void limpiarQuitaSoloLosCooldownsVencidos() {
        progreso.iniciarCooldown(ANA, "cripta", 100);
        progreso.iniciarCooldown(ANA, "torre", 900);
        progreso.limpiarVencidos(500);
        assertEquals(0, progreso.cooldownRestanteMillis(ANA, "cripta", 0));
        assertEquals(900, progreso.cooldownRestanteMillis(ANA, "torre", 0));
    }

    @Test
    void jsonIdaYVuelta() {
        progreso.iniciarCooldown(ANA, "cripta", 5_000);
        progreso.marcarCompletada(BETO, "torre");
        progreso.pendienteCabeza(BETO, "torre");
        List<String> avisos = new ArrayList<>();
        ProgresoJugadores leido = ProgresoJugadores.deJson(progreso.aJson(), avisos::add);
        assertTrue(avisos.isEmpty());
        assertEquals(4_000, leido.cooldownRestanteMillis(ANA, "cripta", 1_000));
        assertTrue(leido.completo(BETO, "torre"));
        assertEquals(Set.of("torre"), leido.recogerCabezas(BETO));
    }

    @Test
    void jsonDescartaClavesQueNoSonUuid() {
        JsonObject raiz = progreso.aJson();
        raiz.getAsJsonObject("cooldowns").add("no-es-uuid", new JsonObject());
        List<String> avisos = new ArrayList<>();
        ProgresoJugadores.deJson(raiz, avisos::add);
        assertEquals(1, avisos.size());
    }
}
