package com.dolfin.creepers.dungeon.muerte;

import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;

/** Decisiones puras (sin Minecraft) de la muerte en dungeons (#16). */
public final class ReglasMuerte {
    private ReglasMuerte() {}

    /**
     * Si lo que queda en una tumba vuelve a su dueño al terminar la partida. Solo se pierde con
     * derrota o tiempo agotado; una partida cancelada o abandonada no castiga.
     */
    public static boolean devolverTumba(ResultadoPartida resultado) {
        return switch (resultado) {
            case VICTORIA, ABANDONADA, CANCELADA -> true;
            case DERROTA, TIEMPO_AGOTADO -> false;
        };
    }
}
