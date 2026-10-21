package com.dolfin.creepers.dungeon.comando;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class ConfirmacionesTest {
    static final UUID ANA = UUID.randomUUID();
    static final UUID BETO = UUID.randomUUID();

    Confirmaciones confirmaciones = new Confirmaciones();

    @Test
    void laPrimeraVezSoloAvisaYRepetirConfirma() {
        assertFalse(confirmaciones.confirmar(ANA, "leave", 0));
        assertTrue(confirmaciones.confirmar(ANA, "leave", 4_000));
    }

    @Test
    void laConfirmacionSeConsume() {
        confirmaciones.confirmar(ANA, "leave", 0);
        assertTrue(confirmaciones.confirmar(ANA, "leave", 1_000));
        assertFalse(confirmaciones.confirmar(ANA, "leave", 2_000));
    }

    @Test
    void despuesDeLaVentanaVuelveAAvisar() {
        confirmaciones.confirmar(ANA, "leave", 0);
        assertFalse(confirmaciones.confirmar(ANA, "leave", Confirmaciones.VENTANA_MS));
        assertTrue(confirmaciones.confirmar(ANA, "leave", Confirmaciones.VENTANA_MS + 1));
    }

    @Test
    void justoAntesDeVencerSigueValiendo() {
        confirmaciones.confirmar(ANA, "leave", 0);
        assertTrue(confirmaciones.confirmar(ANA, "leave", Confirmaciones.VENTANA_MS - 1));
    }

    @Test
    void otraAccionNoConfirmaLaPendienteYLaSustituye() {
        confirmaciones.confirmar(ANA, "join:cripta", 0);
        assertFalse(confirmaciones.confirmar(ANA, "leave", 1_000));
        assertFalse(confirmaciones.confirmar(ANA, "join:cripta", 2_000));
    }

    @Test
    void cadaJugadorTieneLaSuya() {
        confirmaciones.confirmar(ANA, "leave", 0);
        assertFalse(confirmaciones.confirmar(BETO, "leave", 1_000));
        assertTrue(confirmaciones.confirmar(ANA, "leave", 2_000));
    }

    @Test
    void olvidarCancelaLaPendiente() {
        confirmaciones.confirmar(ANA, "leave", 0);
        confirmaciones.olvidar(ANA);
        assertFalse(confirmaciones.confirmar(ANA, "leave", 1_000));
    }
}
