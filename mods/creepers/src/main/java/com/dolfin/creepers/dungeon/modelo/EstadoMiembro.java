package com.dolfin.creepers.dungeon.modelo;

/** Situación de un jugador dentro de su partida. */
public enum EstadoMiembro {
    /** Jugando con vidas. */
    VIVO,
    /** Sin vidas en dificultad media: mira confinado en la zona (#16). */
    ESPECTADOR,
    /** Desconectado con plazo de {@code reconexionSegundos} para volver. */
    DESCONECTADO,
    /** Ya no participa: salió, fue expulsado, no volvió a tiempo o cayó en hardcore. */
    FUERA;

    /** Sigue ocupando sitio en la partida (no ha salido). */
    public boolean participa() {
        return this != FUERA;
    }
}
