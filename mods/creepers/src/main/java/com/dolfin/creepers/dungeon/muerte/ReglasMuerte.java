package com.dolfin.creepers.dungeon.muerte;

import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;

/** Decisiones puras (sin Minecraft) de la muerte en dungeons (#16). */
public final class ReglasMuerte {
    /** Por qué se castiga a un miembro de una partida en hardcore. */
    public enum OrigenCastigo {
        /** Murió en combate estando conectado. */
        MUERTE,
        /** No volvió a tiempo tras desconectarse (decisión P2); está desconectado. */
        DESCONEXION,
        /** {@code /dungeon leave}. */
        ABANDONO,
        /** Expulsado por inactividad (#17). */
        AFK
    }

    /**
     * Castigo de hardcore.
     *
     * @param vaciar pierde inventario y experiencia sin soltar nada (al volver, si está
     *     desconectado)
     * @param banear recibe el ban temporal de {@code hardcoreBanMinutos}
     * @param expulsar se le desconecta con el motivo del ban
     */
    public record Castigo(boolean vaciar, boolean banear, boolean expulsar) {}

    private ReglasMuerte() {}

    /**
     * Castigo de hardcore según su origen. Siempre se pierde todo; el AFK y quien tiene {@code
     * dolfin.dungeon.bypass.ban} (decisión P4) no reciben ban, y a un desconectado no hay a quién
     * expulsar.
     */
    public static Castigo castigoHardcore(OrigenCastigo origen, boolean bypassBan) {
        boolean banear = !bypassBan && origen != OrigenCastigo.AFK;
        return new Castigo(true, banear, banear && origen != OrigenCastigo.DESCONEXION);
    }

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
