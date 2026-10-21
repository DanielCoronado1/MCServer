package com.dolfin.creepers.dungeon.cripta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.plantilla.DatosMarcador;
import com.dolfin.creepers.dungeon.plantilla.ManifiestoPlantilla;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

class CriptaPlantillaTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
    }

    @Test
    void definicionValida() {
        DefinicionDungeon cripta = CriptaPlantilla.definicion();
        assertEquals("cripta", cripta.id());
        assertTrue(cripta.habilitada());
        assertEquals(48, cripta.zona().maxX() - cripta.zona().minX() + 1);
        assertEquals(32, cripta.zona().maxY() - cripta.zona().minY() + 1);
        assertEquals(48, cripta.zona().maxZ() - cripta.zona().minZ() + 1);

        // 2 checkpoints entre pisos
        assertEquals(2, cripta.checkpoints().size());
        assertTrue(cripta.salida().isPresent());

        // Entrada y checkpoints dentro de la zona
        assertTrue(cripta.zona().contiene(cripta.entrada()));
        cripta.checkpoints().forEach(cp -> assertTrue(cripta.zona().contiene(cp)));
        assertTrue(cripta.zona().contiene(cripta.salida().orElseThrow()));
    }

    @Test
    void marcadoresYJefePresentes() {
        List<DatosMarcador> marcadores = CriptaPlantilla.marcadores();
        assertFalse(marcadores.isEmpty());

        // Debe contener al Rey Zombie en el último piso
        boolean tieneJefe =
                marcadores.stream().anyMatch(m -> m.entidad().equals("dolfincreepers:rey_zombie"));
        assertTrue(tieneJefe, "Debe incluir el marcador del Rey Zombie");

        // Todos los marcadores dentro de los límites relativos 0..47
        for (DatosMarcador m : marcadores) {
            assertTrue(m.x() >= 0 && m.x() < 48);
            assertTrue(m.y() >= 0 && m.y() < 32);
            assertTrue(m.z() >= 0 && m.z() < 48);
            assertTrue(m.cantidad() >= 1);
        }
    }

    @Test
    void construccionNbtTrozoValida() {
        CompoundTag nbt = CriptaPlantilla.construirNbtTrozo();
        ListTag size = nbt.getListOrEmpty("size");
        assertEquals(3, size.size());
        assertEquals(48, size.getIntOr(0, 0));
        assertEquals(32, size.getIntOr(1, 0));
        assertEquals(48, size.getIntOr(2, 0));

        ListTag blocks = nbt.getListOrEmpty("blocks");
        assertFalse(blocks.isEmpty(), "La plantilla física debe tener bloques construidos");

        ListTag palette = nbt.getListOrEmpty("palette");
        assertFalse(palette.isEmpty(), "La paleta debe tener bloques registrados");

        ManifiestoPlantilla manifiesto = CriptaPlantilla.manifiesto();
        assertEquals("cripta", manifiesto.dungeon());
        assertEquals(48, manifiesto.lado());
    }
}
