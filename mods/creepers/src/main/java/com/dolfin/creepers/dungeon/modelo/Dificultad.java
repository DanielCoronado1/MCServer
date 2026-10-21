package com.dolfin.creepers.dungeon.modelo;

import java.util.Locale;
import java.util.Optional;

/** Dificultad elegida al iniciar una partida; su id es el que se escribe en config y comandos. */
public enum Dificultad {
    FACIL,
    MEDIO,
    HARDCORE;

    /** Id en minúsculas ({@code facil}, {@code medio}, {@code hardcore}). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Resuelve un id escrito por un administrador o jugador, sin distinguir mayúsculas. */
    public static Optional<Dificultad> deId(String id) {
        for (Dificultad dificultad : values()) {
            if (dificultad.id().equalsIgnoreCase(id)) {
                return Optional.of(dificultad);
            }
        }
        return Optional.empty();
    }
}
