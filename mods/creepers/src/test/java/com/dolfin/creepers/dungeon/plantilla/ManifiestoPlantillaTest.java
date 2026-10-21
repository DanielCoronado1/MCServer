package com.dolfin.creepers.dungeon.plantilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class ManifiestoPlantillaTest {
    private static final Caja ZONA = new Caja(1872, 0, -128, 2127, 255, 127);

    private static ManifiestoPlantilla ejemplo() {
        return new ManifiestoPlantilla(
                ManifiestoPlantilla.VERSION,
                "cripta",
                ZONA,
                Troceado.LADO_MAXIMO,
                2,
                1_700_000_000_000L,
                List.of(
                        new DatosMarcador(3, 1, 4, "minecraft:zombie", 2, ""),
                        new DatosMarcador(
                                10,
                                2,
                                0,
                                "dolfincreepers:rey_creeper",
                                1,
                                "{CustomName:\"Rey\"}")));
    }

    @Test
    void idaYVueltaPorJsonConservaTodo() {
        ManifiestoPlantilla leido = ManifiestoPlantilla.deJson(ejemplo().aJson());
        assertEquals(ejemplo(), leido);
        assertEquals(216, leido.trozos().size());
    }

    @Test
    void encajaSoloEnZonasDelMismoTamano() {
        ManifiestoPlantilla m = ejemplo();
        assertTrue(m.encajaEn(ZONA.desplazada(4000, 0, 0)));
        assertFalse(m.encajaEn(new Caja(0, 0, 0, 255, 254, 255)));
    }

    @Test
    void versionDesconocidaOCamposQueFaltanSeRechazan() {
        String json = ejemplo().aJson();
        assertThrows(
                IllegalArgumentException.class,
                () -> ManifiestoPlantilla.deJson(json.replace("\"version\": 1", "\"version\": 9")));
        assertThrows(
                IllegalArgumentException.class,
                () -> ManifiestoPlantilla.deJson(json.replace("\"lado\"", "\"otro\"")));
        assertThrows(IllegalArgumentException.class, () -> ManifiestoPlantilla.deJson("{"));
    }

    @Test
    void marcadoresInvalidosSeRechazan() {
        assertThrows(
                IllegalArgumentException.class, () -> new DatosMarcador(0, 0, 0, "Zombie", 1, ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DatosMarcador(0, 0, 0, "minecraft:zombie", 0, ""));
        assertEquals(DatosMarcador.CANTIDAD_MAXIMA, DatosMarcador.cantidadValida(1000));
        assertEquals("", new DatosMarcador(0, 0, 0, "minecraft:zombie", 1, null).nbt());
    }
}
