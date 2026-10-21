package com.dolfin.creepers.dungeon.plantilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.modelo.Zona;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

class TroceadoTest {
    @Test
    void zonaPequenaEsUnSoloTrozo() {
        List<Troceado.Trozo> trozos = Troceado.trozos(48, 48, 48, Troceado.LADO_MAXIMO);
        assertEquals(1, trozos.size());
        assertEquals(new Caja(0, 0, 0, 47, 47, 47), trozos.get(0).caja());
        assertEquals("0_0_0.nbt", trozos.get(0).archivo());
    }

    @Test
    void zonaMayorSeDivideSinPasarDe48PorLado() {
        List<Troceado.Trozo> trozos = Troceado.trozos(49, 100, 30, Troceado.LADO_MAXIMO);
        assertEquals(2 * 3 * 1, trozos.size());
        for (Troceado.Trozo t : trozos) {
            assertTrue(t.caja().ancho() <= 48);
            assertTrue(t.caja().alto() <= 48);
            assertTrue(t.caja().largo() <= 48);
        }
    }

    @Test
    void losTrozosCubrenLaZonaExactamenteUnaVez() {
        int ancho = 50;
        int alto = 97;
        int largo = 13;
        List<Troceado.Trozo> trozos = Troceado.trozos(ancho, alto, largo, 16);
        long suma = trozos.stream().mapToLong(t -> t.caja().volumen()).sum();
        assertEquals((long) ancho * alto * largo, suma);
        for (int x = 0; x < ancho; x++) {
            for (int y = 0; y < alto; y++) {
                for (int z = 0; z < largo; z++) {
                    int cx = x;
                    int cy = y;
                    int cz = z;
                    assertEquals(
                            1, trozos.stream().filter(t -> t.caja().contiene(cx, cy, cz)).count());
                }
            }
        }
    }

    @Test
    void ranuraPorDefectoDa216TrozosConNombresUnicos() {
        Caja zona = Caja.de(Zona.ranura(3));
        List<Troceado.Trozo> trozos =
                Troceado.trozos(zona.ancho(), zona.alto(), zona.largo(), Troceado.LADO_MAXIMO);
        assertEquals(6 * 6 * 6, trozos.size());
        Set<String> archivos = new HashSet<>();
        trozos.forEach(t -> archivos.add(t.archivo()));
        assertEquals(trozos.size(), archivos.size());
    }

    @Test
    void losTrozosVanDeAbajoArriba() {
        List<Troceado.Trozo> trozos = Troceado.trozos(96, 96, 96, 48);
        int yAnterior = Integer.MIN_VALUE;
        for (Troceado.Trozo t : trozos) {
            assertTrue(t.caja().minY() >= yAnterior);
            yAnterior = t.caja().minY();
        }
    }

    @Test
    void lasCapasCabenEnElPresupuestoYCubrenElTrozo() {
        Caja trozo = new Caja(10, 0, -5, 57, 47, 42);
        List<Caja> capas = Troceado.capas(trozo, 16_384);
        assertEquals(7, capas.size());
        long suma = 0;
        int siguienteY = trozo.minY();
        for (Caja capa : capas) {
            assertTrue(capa.volumen() <= 16_384);
            assertEquals(siguienteY, capa.minY());
            assertEquals(trozo.minX(), capa.minX());
            assertEquals(trozo.maxZ(), capa.maxZ());
            siguienteY = capa.maxY() + 1;
            suma += capa.volumen();
        }
        assertEquals(trozo.maxY() + 1, siguienteY);
        assertEquals(trozo.volumen(), suma);
    }

    @Test
    void unaCapaNuncaBajaDeUnBloqueDeAlto() {
        Caja trozo = new Caja(0, 0, 0, 47, 2, 47);
        List<Caja> capas = Troceado.capas(trozo, 100);
        assertEquals(3, capas.size());
        capas.forEach(c -> assertEquals(1, c.alto()));
    }

    @Test
    void valoresNoPositivosSeRechazan() {
        assertThrows(IllegalArgumentException.class, () -> Troceado.trozos(0, 1, 1, 48));
        assertThrows(IllegalArgumentException.class, () -> Troceado.trozos(1, 1, 1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> Troceado.capas(new Caja(0, 0, 0, 1, 1, 1), 0));
    }
}
