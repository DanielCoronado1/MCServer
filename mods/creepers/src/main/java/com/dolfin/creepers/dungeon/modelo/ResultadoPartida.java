package com.dolfin.creepers.dungeon.modelo;

/** Cómo terminó una partida. Solo {@link #VICTORIA} y {@link #DERROTA} aplican castigos. */
public enum ResultadoPartida {
    VICTORIA,
    DERROTA,
    /** Se agotó el límite de tiempo: derrota sin ban en hardcore (#17). */
    TIEMPO_AGOTADO,
    /** Todos los miembros quedaron fuera. */
    ABANDONADA,
    /** Detenida por un administrador o un reinicio: sin ban ni pérdida. */
    CANCELADA
}
