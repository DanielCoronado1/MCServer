package com.dolfin.creepers.dungeon.muerte;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;

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
}
