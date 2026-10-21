package com.dolfin.creepers.dungeon.reglas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.modelo.EstadoMiembro;
import com.dolfin.creepers.dungeon.reglas.DecisionesReglas.UsoObjeto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

class DecisionesReglasTest {
    static final UUID ANA = UUID.randomUUID();
    static final UUID BETO = UUID.randomUUID();
    static final List<String> PROHIBIDOS =
            List.of("minecraft:ender_pearl", "minecraft:chorus_fruit");

    @Test
    void elDanioDeOtroJugadorSeBloquea() {
        assertTrue(DecisionesReglas.bloqueaDanio(ANA, Optional.of(BETO)));
    }

    @Test
    void elDanioPropioOSinJugadorResponsableSePermite() {
        assertFalse(DecisionesReglas.bloqueaDanio(ANA, Optional.of(ANA)));
        assertFalse(DecisionesReglas.bloqueaDanio(ANA, Optional.empty()));
    }

    @Test
    void soloLosEfectosDaninososDeOtroJugadorSeBloquean() {
        assertTrue(DecisionesReglas.bloqueaEfecto(true, ANA, Optional.of(BETO)));
        assertFalse(DecisionesReglas.bloqueaEfecto(false, ANA, Optional.of(BETO)));
        assertFalse(DecisionesReglas.bloqueaEfecto(true, ANA, Optional.of(ANA)));
        assertFalse(DecisionesReglas.bloqueaEfecto(true, ANA, Optional.empty()));
    }

    @Test
    void perlaYFrutaCoralEstanProhibidas() {
        assertEquals(
                UsoObjeto.PROHIBIDO,
                DecisionesReglas.usoEnAire("minecraft:ender_pearl", false, PROHIBIDOS));
        assertEquals(
                UsoObjeto.PROHIBIDO,
                DecisionesReglas.usoEnAire("minecraft:chorus_fruit", true, PROHIBIDOS));
    }

    @Test
    void comidaTotemYPocionesSePermiten() {
        for (String id :
                List.of(
                        "minecraft:bread",
                        "minecraft:totem_of_undying",
                        "minecraft:splash_potion",
                        "minecraft:potion")) {
            assertEquals(UsoObjeto.PERMITIDO, DecisionesReglas.usoEnAire(id, false, PROHIBIDOS));
        }
    }

    @Test
    void laListaProhibidaEsConfigurable() {
        assertEquals(
                UsoObjeto.PERMITIDO,
                DecisionesReglas.usoEnAire("minecraft:ender_pearl", false, List.of()));
        assertEquals(
                UsoObjeto.PROHIBIDO,
                DecisionesReglas.usoEnAire("minecraft:bread", false, List.of("minecraft:bread")));
    }

    @Test
    void elCoheteSoloValeConUnaBallestaEnLaOtraMano() {
        assertEquals(
                UsoObjeto.COHETE_SIN_BALLESTA,
                DecisionesReglas.usoEnAire(DecisionesReglas.COHETE, false, PROHIBIDOS));
        assertEquals(
                UsoObjeto.PERMITIDO,
                DecisionesReglas.usoEnAire(DecisionesReglas.COHETE, true, PROHIBIDOS));
    }

    @Test
    void sobreUnBloqueSoloSeFrenaElCohete() {
        assertEquals(
                UsoObjeto.COHETE_SIN_BALLESTA,
                DecisionesReglas.usoEnBloque(DecisionesReglas.COHETE));
        assertEquals(UsoObjeto.PERMITIDO, DecisionesReglas.usoEnBloque("minecraft:ender_pearl"));
        assertEquals(UsoObjeto.PERMITIDO, DecisionesReglas.usoEnBloque("minecraft:lever"));
    }

    @Test
    void lasReglasNoAplicanFueraDeLaDimensionNiAAdministradoresEnCreativo() {
        assertTrue(DecisionesReglas.sujetoAReglas(true, false));
        assertFalse(DecisionesReglas.sujetoAReglas(false, false));
        assertFalse(DecisionesReglas.sujetoAReglas(true, true));
    }

    @Test
    void soloSeFuerzaAventuraAMiembrosVivosEnSupervivencia() {
        assertTrue(DecisionesReglas.debeForzarAventura(EstadoMiembro.VIVO, true));
        assertFalse(DecisionesReglas.debeForzarAventura(EstadoMiembro.VIVO, false));
        assertFalse(DecisionesReglas.debeForzarAventura(EstadoMiembro.ESPECTADOR, true));
        assertFalse(DecisionesReglas.debeForzarAventura(EstadoMiembro.DESCONECTADO, true));
    }
}
