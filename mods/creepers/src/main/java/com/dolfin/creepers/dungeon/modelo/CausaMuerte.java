package com.dolfin.creepers.dungeon.modelo;

/** Origen de un {@code JugadorMurio}. */
public enum CausaMuerte {
    /** Muerte real dentro de la dungeon (#16). */
    COMBATE,
    /** Desconexión sin volver a tiempo en hardcore (decisión P2: cuenta como muerte). */
    DESCONEXION,
    /** {@code /dungeon leave} en hardcore (#13). */
    ABANDONO
}
