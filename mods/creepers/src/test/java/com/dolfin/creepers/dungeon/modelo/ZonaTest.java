package com.dolfin.creepers.dungeon.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZonaTest {
    @Test
    void lasRanurasEstanSeparadasYNoSeSolapan() {
        Zona cero = Zona.ranura(0);
        Zona uno = Zona.ranura(1);
        assertEquals(2000, (uno.minX() + uno.maxX() + 1) / 2);
        assertEquals(256, cero.maxX() - cero.minX() + 1);
        assertFalse(cero.seSolapaCon(uno));
        assertTrue(cero.seSolapaCon(Zona.entre(127, 0, 0, 500, 10, 10)));
    }

    @Test
    void contieneUsaEsquinasInclusivasDeBloque() {
        Zona zona = Zona.entre(10, 0, 10, 0, 5, 0);
        assertTrue(zona.contiene(10.9, 5.9, 0));
        assertFalse(zona.contiene(11, 0, 0));
        assertFalse(zona.contiene(-0.1, 0, 0));
        assertTrue(zona.contiene(zona.centroInferior()));
    }

    @Test
    void chunksCubrenCoordenadasNegativas() {
        Zona zona = Zona.entre(-17, 0, -1, 16, 0, 15);
        assertEquals(-2, zona.chunkMinX());
        assertEquals(1, zona.chunkMaxX());
        assertEquals(-1, zona.chunkMinZ());
        assertEquals(0, zona.chunkMaxZ());
    }

    @Test
    void esquinasInvertidasSeRechazan() {
        assertThrows(IllegalArgumentException.class, () -> new Zona(1, 0, 0, 0, 0, 0));
    }
}
