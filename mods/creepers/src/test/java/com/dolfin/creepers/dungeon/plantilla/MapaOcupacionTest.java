package com.dolfin.creepers.dungeon.plantilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapaOcupacionTest {
    @Test
    void marcaCadaPosicionSinPisarALasVecinas() {
        MapaOcupacion mapa = new MapaOcupacion(48, 48, 48);
        mapa.marcar(0, 0, 0);
        mapa.marcar(47, 47, 47);
        mapa.marcar(1, 0, 0);
        assertTrue(mapa.ocupado(0, 0, 0));
        assertTrue(mapa.ocupado(47, 47, 47));
        assertTrue(mapa.ocupado(1, 0, 0));
        assertFalse(mapa.ocupado(0, 1, 0));
        assertFalse(mapa.ocupado(0, 0, 1));
        assertEquals(3, mapa.cuenta());
    }

    @Test
    void indicesDistintosEnTrozosNoCubicos() {
        MapaOcupacion mapa = new MapaOcupacion(3, 2, 5);
        for (int y = 0; y < 2; y++) {
            for (int z = 0; z < 5; z++) {
                for (int x = 0; x < 3; x++) {
                    mapa.marcar(x, y, z);
                }
            }
        }
        assertEquals(3 * 2 * 5, mapa.cuenta());
    }

    @Test
    void fueraDelTrozoNoCuenta() {
        MapaOcupacion mapa = new MapaOcupacion(2, 2, 2);
        mapa.marcar(-1, 0, 0);
        mapa.marcar(2, 0, 0);
        mapa.marcar(0, 0, 2);
        assertEquals(0, mapa.cuenta());
        assertFalse(mapa.ocupado(5, 5, 5));
        assertThrows(IllegalArgumentException.class, () -> new MapaOcupacion(0, 1, 1));
    }
}
