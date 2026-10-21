package com.dolfin.creepers.dungeon.comando;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.comando.DisposicionMenu.Icono;
import com.dolfin.creepers.dungeon.comando.VistaDungeon.EstadoVisible;
import com.dolfin.creepers.dungeon.modelo.Dificultad;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

class DisposicionMenuTest {
    @Test
    void lasFilasSeAjustanEntreTresYSeis() {
        assertEquals(3, DisposicionMenu.filas(0));
        assertEquals(3, DisposicionMenu.filas(27));
        assertEquals(4, DisposicionMenu.filas(28));
        assertEquals(6, DisposicionMenu.filas(54));
        assertEquals(6, DisposicionMenu.filas(500));
    }

    @Test
    void cadaDungeonOcupaSuHuecoEnOrden() {
        assertEquals(OptionalInt.of(0), DisposicionMenu.dungeonEn(0, 3));
        assertEquals(OptionalInt.of(2), DisposicionMenu.dungeonEn(2, 3));
        assertTrue(DisposicionMenu.dungeonEn(3, 3).isEmpty());
        assertTrue(DisposicionMenu.dungeonEn(-1, 3).isEmpty());
        assertTrue(DisposicionMenu.dungeonEn(54, 100).isEmpty());
    }

    @Test
    void lasDificultadesTienenHuecosDistintosYReversibles() {
        Set<Integer> huecos = new HashSet<>();
        for (Dificultad d : Dificultad.values()) {
            int hueco = DisposicionMenu.huecoDificultad(d);
            huecos.add(hueco);
            assertEquals(Optional.of(d), DisposicionMenu.dificultadEn(hueco));
        }
        assertEquals(3, huecos.size());
        assertTrue(DisposicionMenu.dificultadEn(0).isEmpty());
    }

    @Test
    void volverYCabeceraNoPisanLasDificultades() {
        for (int filas = 3; filas <= 6; filas++) {
            int volver = DisposicionMenu.huecoVolver(filas);
            assertTrue(DisposicionMenu.dificultadEn(volver).isEmpty());
            assertTrue(volver < filas * DisposicionMenu.COLUMNAS);
        }
        assertTrue(DisposicionMenu.dificultadEn(DisposicionMenu.huecoCabecera()).isEmpty());
    }

    @Test
    void cadaEstadoTieneSuIcono() {
        assertEquals(Icono.LIBRE, DisposicionMenu.icono(EstadoVisible.Clase.LIBRE));
        assertEquals(Icono.OCUPADA, DisposicionMenu.icono(EstadoVisible.Clase.EN_PARTIDA));
        assertEquals(Icono.EN_FILA, DisposicionMenu.icono(EstadoVisible.Clase.EN_FILA));
        assertEquals(Icono.BLOQUEADA, DisposicionMenu.icono(EstadoVisible.Clase.BLOQUEADA));
        assertEquals(Icono.HARDCORE, DisposicionMenu.icono(Dificultad.HARDCORE));
    }
}
