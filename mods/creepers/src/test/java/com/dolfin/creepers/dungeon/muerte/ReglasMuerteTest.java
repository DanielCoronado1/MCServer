package com.dolfin.creepers.dungeon.muerte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;
import com.dolfin.creepers.dungeon.muerte.ReglasMuerte.Castigo;
import com.dolfin.creepers.dungeon.muerte.ReglasMuerte.OrigenCastigo;

import org.junit.jupiter.api.Test;

class ReglasMuerteTest {
    @Test
    void laTumbaVuelveSalvoConDerrotaOTiempoAgotado() {
        assertTrue(ReglasMuerte.devolverTumba(ResultadoPartida.VICTORIA));
        assertTrue(ReglasMuerte.devolverTumba(ResultadoPartida.CANCELADA));
        assertTrue(ReglasMuerte.devolverTumba(ResultadoPartida.ABANDONADA));
        assertFalse(ReglasMuerte.devolverTumba(ResultadoPartida.DERROTA));
        assertFalse(ReglasMuerte.devolverTumba(ResultadoPartida.TIEMPO_AGOTADO));
    }

    @Test
    void hardcoreSiempreVaciaYBaneaSalvoAfkOBypass() {
        assertEquals(
                new Castigo(true, true, true),
                ReglasMuerte.castigoHardcore(OrigenCastigo.MUERTE, false));
        assertEquals(
                new Castigo(true, true, true),
                ReglasMuerte.castigoHardcore(OrigenCastigo.ABANDONO, false));
        assertEquals(
                new Castigo(true, false, false),
                ReglasMuerte.castigoHardcore(OrigenCastigo.AFK, false));
        assertEquals(
                new Castigo(true, false, false),
                ReglasMuerte.castigoHardcore(OrigenCastigo.MUERTE, true));
    }

    @Test
    void desconectadoSinVolverSeBaneaSinExpulsar() {
        assertEquals(
                new Castigo(true, true, false),
                ReglasMuerte.castigoHardcore(OrigenCastigo.DESCONEXION, false));
        assertEquals(
                new Castigo(true, false, false),
                ReglasMuerte.castigoHardcore(OrigenCastigo.DESCONEXION, true));
    }
}
