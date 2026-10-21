package com.dolfin.creepers.dungeon.fin;

import java.util.List;
import java.util.OptionalInt;

/**
 * Cuenta atrás del límite de tiempo de una partida (#17), pura: quien la usa pasa los segundos
 * transcurridos y recibe, como mucho una vez cada uno, el aviso de los {@link #AVISOS_MINUTOS}
 * restantes y el fin del tiempo. Un límite igual o menor que un aviso no lo emite.
 */
public final class RelojPartida {
    /** Minutos restantes en que se avisa al grupo, de mayor a menor. */
    public static final List<Integer> AVISOS_MINUTOS = List.of(10, 5, 1);

    /**
     * Lo que ocurrió desde la última llamada.
     *
     * @param avisoMinutos el menor de los avisos cruzados, si se cruzó alguno
     * @param agotado se cruzó el límite
     */
    public record Avance(OptionalInt avisoMinutos, boolean agotado) {}

    private final long limiteSegundos;
    private long ultimoTranscurrido;

    public RelojPartida(int limiteMinutos) {
        this.limiteSegundos = limiteMinutos * 60L;
    }

    /** Segundos que quedan, sin bajar de 0. */
    public long restanteSegundos(long transcurridoSegundos) {
        return Math.max(0, limiteSegundos - transcurridoSegundos);
    }

    /** Registra el instante actual y devuelve los hitos que se cruzaron desde el anterior. */
    public Avance avanzar(long transcurridoSegundos) {
        long antes = restanteSegundos(ultimoTranscurrido);
        long ahora = restanteSegundos(transcurridoSegundos);
        ultimoTranscurrido = Math.max(ultimoTranscurrido, transcurridoSegundos);
        OptionalInt aviso = OptionalInt.empty();
        for (int minutos : AVISOS_MINUTOS) {
            if (antes > minutos * 60L && ahora <= minutos * 60L) {
                aviso = OptionalInt.of(minutos);
            }
        }
        return new Avance(aviso, antes > 0 && ahora == 0);
    }
}
