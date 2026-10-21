package com.dolfin.creepers.dungeon.muerte;

import com.dolfin.creepers.dungeon.modelo.CausaMuerte;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.Miembro;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;

/** Decisiones puras (sin Minecraft) de la muerte en dungeons (#16). */
public final class ReglasMuerte {
    /** Adónde va un miembro tras morir. */
    public enum Destino {
        /** Reaparece vivo en su último checkpoint, sin pantalla de muerte. */
        REAPARECE,
        /** Sin vidas: espectador confinado en la zona hasta que termine la partida. */
        ESPECTADOR,
        /** Hardcore: sale de la partida con el castigo de {@link #castigoHardcore}. */
        ELIMINADO
    }

    /**
     * Qué le pasa a un miembro que muere.
     *
     * @param dejaTumba sus objetos van a una tumba (dificultad media); si no, los conserva (fácil)
     *     o los pierde sin soltarlos (hardcore)
     */
    public record Consecuencia(boolean dejaTumba, Destino destino) {}

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
     * Consecuencia de una muerte en combate, ya restada la vida. Fácil conserva inventario y
     * experiencia; medio deja los objetos en una tumba (la experiencia se conserva); con 0 vidas se
     * pasa a espectador. En hardcore toda muerte elimina, tenga las vidas que tenga.
     *
     * @param vidasRestantes vidas tras morir, o {@link Miembro#VIDAS_ILIMITADAS}
     */
    public static Consecuencia consecuencia(Dificultad dificultad, int vidasRestantes) {
        if (dificultad == Dificultad.HARDCORE) {
            return new Consecuencia(false, Destino.ELIMINADO);
        }
        Destino destino = vidasRestantes == 0 ? Destino.ESPECTADOR : Destino.REAPARECE;
        return new Consecuencia(dificultad == Dificultad.MEDIO, destino);
    }

    /** Aviso al grupo: "Pepe murió (2 vidas restantes)", "Pepe ha caído en hardcore"… */
    public static String mensajeMuerte(
            String nombre, Dificultad dificultad, CausaMuerte causa, int vidasRestantes) {
        if (dificultad == Dificultad.HARDCORE) {
            return switch (causa) {
                case DESCONEXION -> nombre + " ha caído en hardcore (no volvió a tiempo)";
                case ABANDONO -> nombre + " abandonó y ha caído en hardcore";
                case COMBATE -> nombre + " ha caído en hardcore";
            };
        }
        return switch (vidasRestantes) {
            case Miembro.VIDAS_ILIMITADAS -> nombre + " murió";
            case 0 -> nombre + " murió sin vidas restantes y pasa a espectador";
            case 1 -> nombre + " murió (1 vida restante)";
            default -> nombre + " murió (" + vidasRestantes + " vidas restantes)";
        };
    }

    /**
     * La partida termina en derrota por bajas (nunca tras declararse la victoria): nadie sigue vivo
     * ni puede volver vivo, y o bien quedan espectadores, o bien alguien cayó en hardcore. Si todos
     * salieron sin morir no es derrota: la partida acaba como abandonada.
     */
    public static boolean derrotaPorBajas(Partida partida) {
        if (!partida.activa() || partida.victoriaDeclarada() || partida.hayVivosOPorVolver()) {
            return false;
        }
        boolean cayoEnHardcore =
                partida.dificultad() == Dificultad.HARDCORE
                        && partida.miembros().stream().anyMatch(m -> m.muertes() > 0);
        return partida.hayParticipantes() || cayoEnHardcore;
    }

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
