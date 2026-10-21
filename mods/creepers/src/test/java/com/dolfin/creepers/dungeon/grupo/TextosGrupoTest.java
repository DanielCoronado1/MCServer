package com.dolfin.creepers.dungeon.grupo;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.dolfin.creepers.dungeon.grupo.Grupos.Error;

import org.junit.jupiter.api.Test;

class TextosGrupoTest {
    @Test
    void todoErrorTieneTexto() {
        for (Error error : Error.values()) {
            assertFalse(TextosGrupo.error(error).isBlank(), error.name());
        }
    }

    @Test
    void todaSalidaTieneTexto() {
        for (Grupos.MotivoSalida motivo : Grupos.MotivoSalida.values()) {
            assertFalse(TextosGrupo.salida("Ana", motivo).isBlank(), motivo.name());
        }
    }
}
