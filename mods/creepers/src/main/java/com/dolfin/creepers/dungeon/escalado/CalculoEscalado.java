package com.dolfin.creepers.dungeon.escalado;

import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.plantilla.DatosMarcador;

/** Cálculos puros de escalado para vida, daño y cantidad de criaturas (#18). */
public final class CalculoEscalado {
    private CalculoEscalado() {}

    /** Multiplicador de vida para una criatura normal según la dificultad. */
    public static double multiplicadorVidaMob(Partida partida) {
        return partida.parametros().multVida();
    }

    /** Multiplicador de daño para una criatura según la dificultad. */
    public static double multiplicadorDanioMob(Partida partida) {
        return partida.parametros().multDanio();
    }

    /**
     * Multiplicador de vida para el jefe: combina la dificultad con el escalado por jugadores.
     * Fórmula: multVida * (1 + vidaJefePorJugador * (jugadoresIniciales - 1)).
     */
    public static double multiplicadorVidaJefe(Partida partida) {
        double multDificultad = partida.parametros().multVida();
        double porJugador = partida.config().partida().vidaJefePorJugador();
        int jugadoresExtra = Math.max(0, partida.jugadoresIniciales() - 1);
        return multDificultad * (1.0 + porJugador * jugadoresExtra);
    }

    /**
     * Cantidad de entidades que genera un marcador. En hardcore añade criaturas extra si el
     * marcador lo especifica en sus datos.
     */
    public static int cantidadMobs(Partida partida, DatosMarcador marcador) {
        int base = marcador.cantidad();
        if (partida.dificultad() != Dificultad.HARDCORE) {
            return base;
        }
        int extra = extraerExtraHardcore(marcador.nbt());
        return Math.clamp(base + extra, 1, DatosMarcador.CANTIDAD_MAXIMA);
    }

    /** Extrae el valor numérico de extraHardcore o hardcore_extra del SNBT del marcador. */
    static int extraerExtraHardcore(String nbt) {
        if (nbt == null || nbt.isBlank()) {
            return 0;
        }
        String texto = nbt.toLowerCase();
        for (String clave : new String[] {"extrahardcore:", "hardcore_extra:", "hardcore:"}) {
            int indice = texto.indexOf(clave);
            if (indice != -1) {
                int inicio = indice + clave.length();
                int fin = inicio;
                while (fin < texto.length() && Character.isWhitespace(texto.charAt(fin))) {
                    inicio++;
                    fin++;
                }
                while (fin < texto.length()
                        && (Character.isDigit(texto.charAt(fin)) || texto.charAt(fin) == '-')) {
                    fin++;
                }
                if (fin > inicio) {
                    try {
                        return Math.max(0, Integer.parseInt(texto.substring(inicio, fin)));
                    } catch (NumberFormatException ignored) {
                        return 0;
                    }
                }
            }
        }
        return 0;
    }
}
