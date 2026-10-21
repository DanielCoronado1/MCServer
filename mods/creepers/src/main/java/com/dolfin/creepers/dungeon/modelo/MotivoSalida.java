package com.dolfin.creepers.dungeon.modelo;

/** Por qué un miembro dejó la partida; los oyentes (#16, #17, #22) deciden según el motivo. */
public enum MotivoSalida {
    /** {@code /dungeon leave}; en hardcore cuenta como muerte (#16). */
    ABANDONO,
    /** Expulsado por un administrador o por {@code /dungeon admin stop}. */
    EXPULSION,
    /** No volvió dentro de {@code reconexionSegundos}. */
    DESCONEXION,
    /** Inactivo más de {@code afkSegundos} (#17). */
    AFK,
    /** Murió en hardcore (#16). */
    MUERTE_HARDCORE,
    /** Dejó la dimensión por un medio ajeno a la dungeon (tp de admin, reaparición fuera). */
    FUERA_DE_DIMENSION,
    /** La partida terminó; ver {@link ResultadoPartida}. */
    FIN_DE_PARTIDA
}
