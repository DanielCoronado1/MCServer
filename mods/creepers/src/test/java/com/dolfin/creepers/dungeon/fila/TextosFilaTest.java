package com.dolfin.creepers.dungeon.fila;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.fila.Filas.Motivo;

import org.junit.jupiter.api.Test;

import java.util.Optional;

class TextosFilaTest {
    @Test
    void todoMotivoDeSalidaTieneTexto() {
        for (Motivo motivo : Motivo.values()) {
            assertFalse(
                    TextosFila.retirada(motivo, "Cripta", Optional.of("Pepe")).isBlank(),
                    motivo.name());
        }
    }

    @Test
    void elRechazoDiceQuien() {
        assertTrue(
                TextosFila.retirada(Motivo.RECHAZO, "Cripta", Optional.of("Pepe"))
                        .startsWith("Pepe"));
    }

    @Test
    void avanceAlPrimerPuestoDiceQueEsElSiguiente() {
        assertEquals("Eres el siguiente en la fila de Cripta.", TextosFila.avance("Cripta", 1, 3));
        assertEquals(
                "Avanzas en la fila de Cripta: puesto 2 de 3.", TextosFila.avance("Cripta", 2, 3));
    }
}
