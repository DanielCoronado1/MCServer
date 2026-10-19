package com.dolfin.zombies.kinds;

import static org.junit.jupiter.api.Assertions.*;

import com.dolfin.zombies.*;
import com.google.gson.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

/**
 * Verifies default configuration, ability switches, and parameter validation for issue #7 zombies.
 */
class InventoryZombiesConfigTest {
    @TempDir Path root;
    List<String> warnings;
    ZombieConfig config;

    @BeforeEach
    void setup() {
        warnings = new ArrayList<>();
        config = new ZombieConfig(root, warnings::add);
    }

    @Test
    void ladronDefaultsMatchTicket() {
        JsonObject ladron = config.current().zombie(ZombieKind.LADRON);
        assertEquals(16.0, ladron.get("vida").getAsDouble());
        assertEquals(1.0, ladron.get("danio").getAsDouble());
        assertEquals(1.4, ladron.get("velocidad").getAsDouble());
        assertTrue(config.current().abilityEnabled(ZombieKind.LADRON, "robar"));
        JsonObject robar = ladron.getAsJsonObject("habilidades").getAsJsonObject("robar");
        assertEquals(0.35, robar.get("probabilidadRobo").getAsDouble());
        assertEquals(1.6, robar.get("velocidadHuida").getAsDouble());
        assertEquals(0, robar.getAsJsonArray("listaNegra").size());
        assertTrue(robar.get("soloHotbar").getAsBoolean());
        assertEquals(10, robar.get("minutosParaSoltar").getAsInt());
    }

    @Test
    void contagiadoDefaultsAndNestedAbilitiesMatchTicket() {
        JsonObject contagiado = config.current().zombie(ZombieKind.CONTAGIADO);
        assertTrue(config.current().abilityEnabled(ZombieKind.CONTAGIADO, "convertirAlMorir"));
        assertTrue(config.current().abilityEnabled(ZombieKind.CONTAGIADO, "infeccion"));
        assertTrue(config.current().abilityEnabled(ZombieKind.CONTAGIADO, "cofre"));
        JsonObject conv =
                contagiado.getAsJsonObject("habilidades").getAsJsonObject("convertirAlMorir");
        assertEquals(0.25, conv.get("probabilidadInfeccion").getAsDouble());
        assertEquals(6000, conv.get("duracionInfeccion").getAsInt());
        assertEquals(3, conv.getAsJsonArray("curas").size());
        JsonObject cofre = contagiado.getAsJsonObject("habilidades").getAsJsonObject("cofre");
        assertTrue(cofre.get("activo").getAsBoolean());
        assertEquals(15, cofre.get("minutosProteccion").getAsInt());
        assertEquals(5, cofre.get("radioBusqueda").getAsInt());
    }

    @Test
    void mimoDefaultsNeverNaturalAndMatchTicket() {
        JsonObject mimo = config.current().zombie(ZombieKind.MIMO);
        assertEquals(0, mimo.get("peso").getAsInt());
        assertTrue(config.current().abilityEnabled(ZombieKind.MIMO, "aparicionPorMuerte"));
        JsonObject ap = mimo.getAsJsonObject("habilidades").getAsJsonObject("aparicionPorMuerte");
        assertEquals(0.30, ap.get("probabilidadAparecer").getAsDouble());
        assertEquals(10, ap.get("minutosVentana").getAsInt());
        assertFalse(ap.get("copiarEncantamientos").getAsBoolean());
    }

    @Test
    void liveAbilityTogglesForIssue7KindsWorkIndependently() throws Exception {
        Path path = root.resolve("zombies/contagiado_zombie.json");
        JsonObject raw = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        raw.getAsJsonObject("habilidades").getAsJsonObject("cofre").addProperty("activo", false);
        Files.writeString(path, raw.toString());
        assertTrue(config.reload().problems().isEmpty());
        assertFalse(config.current().abilityEnabled(ZombieKind.CONTAGIADO, "cofre"));
        assertTrue(config.current().abilityEnabled(ZombieKind.CONTAGIADO, "convertirAlMorir"));
    }
}
