package com.dolfin.creepers.dungeon.comando;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PermisosDungeonTest {
    @Test
    void unNodoExplicitoGanaAlRespaldo() {
        assertFalse(PermisosDungeon.resolver(false, true));
        assertTrue(PermisosDungeon.resolver(true, false));
    }

    @Test
    void sinNodoExplicitoValeElRespaldo() {
        assertTrue(PermisosDungeon.resolver(null, true));
        assertFalse(PermisosDungeon.resolver(null, false));
    }

    @Test
    void elNodoSePartePorElUltimoPunto() {
        assertArrayEquals(
                new String[] {"dolfin.dungeon", "use"},
                PermisosDungeon.partirNodo("dolfin.dungeon.use").orElseThrow());
        assertArrayEquals(
                new String[] {"dolfin.dungeon", "cripta_2"},
                PermisosDungeon.partirNodo("dolfin.dungeon.cripta_2").orElseThrow());
    }

    @Test
    void nodosQueNoCabenEnUnaClaveSeIgnoran() {
        assertTrue(PermisosDungeon.partirNodo("sinpunto").isEmpty());
        assertTrue(PermisosDungeon.partirNodo(".use").isEmpty());
        assertTrue(PermisosDungeon.partirNodo("dolfin.").isEmpty());
        assertTrue(PermisosDungeon.partirNodo("Dolfin.Use").isEmpty());
    }
}
