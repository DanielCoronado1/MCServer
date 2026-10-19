package com.dolfin.zombies.kinds;

import static org.junit.jupiter.api.Assertions.*;

import com.dolfin.zombies.ZombieKind;

import org.junit.jupiter.api.Test;

class KindDefaultsTest {
    @Test
    void mineroBlacklistsContainersBedsAndPortals() {
        var a =
                ZombieKind.MINERO
                        .defaults()
                        .getAsJsonObject("habilidades")
                        .getAsJsonObject("romperBloques");
        assertTrue(a.get("activo").getAsBoolean());
        assertEquals(3, a.get("durezaMax").getAsDouble());
        var list = a.getAsJsonArray("listaNegra").toString();
        for (String s :
                new String[] {
                    "minecraft:chest",
                    "#minecraft:beds",
                    "minecraft:barrel",
                    "#minecraft:shulker_boxes",
                    "minecraft:furnace",
                    "minecraft:crafting_table",
                    "minecraft:nether_portal"
                }) assertTrue(list.contains("\"" + s + "\""), s);
    }

    @Test
    void granjeroDestructiveAbilitiesDefaultOff() {
        var h = ZombieKind.GRANJERO.defaults().getAsJsonObject("habilidades");
        assertFalse(h.getAsJsonObject("destruirCultivos").get("activo").getAsBoolean());
        assertFalse(h.getAsJsonObject("atacarAnimales").get("activo").getAsBoolean());
        assertEquals(24, h.getAsJsonObject("destruirCultivos").get("radioBusqueda").getAsInt());
        assertEquals(4, h.getAsJsonObject("atacarAnimales").getAsJsonArray("animales").size());
    }

    @Test
    void bomberoBurnsByDefault() {
        var a =
                ZombieKind.BOMBERO
                        .defaults()
                        .getAsJsonObject("habilidades")
                        .getAsJsonObject("quemarBloques");
        assertTrue(a.get("activo").getAsBoolean());
        assertEquals(.08, a.get("probabilidadPorPaso").getAsDouble());
        assertFalse(ZombieKind.BOMBERO.defaults().get("quemaConSol").getAsBoolean());
    }
}
