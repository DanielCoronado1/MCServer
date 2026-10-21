package com.dolfin.creepers.dungeon.fila;

import com.dolfin.creepers.dungeon.fila.Filas.Motivo;

import java.util.Optional;

/** Textos de la fila y el ready check sin Minecraft, para poder probarlos. */
final class TextosFila {
    private TextosFila() {}

    /** Por qué el grupo sale de la fila de {@code dungeon}. */
    static String retirada(Motivo motivo, String dungeon, Optional<String> culpable) {
        String quien = culpable.orElse("Alguien");
        return switch (motivo) {
            case RECHAZO -> quien + " no está listo: el grupo sale de la fila de " + dungeon + ".";
            case SIN_RESPUESTA ->
                    "El ready check de "
                            + dungeon
                            + " venció sin respuesta de todos: sales de la fila.";
            case SIN_CONEXION ->
                    "Todo el grupo está desconectado: sale de la fila de " + dungeon + ".";
            case CANCELADA -> quien + " cancela la fila de " + dungeon + ": el grupo sale.";
            case CERRADA -> dungeon + " ha cerrado: sales de la fila.";
        };
    }

    /** Aviso de que el grupo avanzó en la fila. */
    static String avance(String dungeon, int posicion, int total) {
        return posicion == 1
                ? "Eres el siguiente en la fila de " + dungeon + "."
                : "Avanzas en la fila de "
                        + dungeon
                        + ": puesto "
                        + posicion
                        + " de "
                        + total
                        + ".";
    }

    /** Aviso de que un miembro confirmó su ready check. */
    static String listo(String nombre, int faltan) {
        return faltan == 0
                ? nombre + " está listo."
                : nombre + " está listo (faltan " + faltan + ").";
    }

    /** Cabecera del ready check. */
    static String readyCheck(String dungeon, String dificultad, int segundos) {
        return dungeon + " (" + dificultad + ") está libre. Confirma en " + segundos + " s: ";
    }

    /** Puesto al entrar en la fila. */
    static String enFila(String dungeon, int posicion, int total) {
        return "Estás en la fila de " + dungeon + ": puesto " + posicion + " de " + total + ".";
    }
}
