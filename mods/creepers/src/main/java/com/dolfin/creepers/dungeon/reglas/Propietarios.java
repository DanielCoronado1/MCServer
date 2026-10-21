package com.dolfin.creepers.dungeon.reglas;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Sigue la cadena de dueños de una entidad (flecha → tirador, lobo → amo, TNT → quien lo encendió,
 * nube de poción → lanzador…) hasta el jugador responsable. Es genérico sobre el tipo de entidad
 * para probarse sin Minecraft.
 */
final class Propietarios {
    /** Saltos máximos por cadena; protege de ciclos de dueños. */
    static final int SALTOS_MAX = 8;

    private Propietarios() {}

    /**
     * El primer jugador de la cadena que empieza en {@code origen} (que puede ser él mismo).
     *
     * @param dueno devuelve el dueño directo o {@code null} si no lo tiene
     */
    static <E> Optional<E> jugadorResponsable(
            E origen, Predicate<E> esJugador, Function<E, E> dueno) {
        E actual = origen;
        for (int salto = 0; actual != null && salto <= SALTOS_MAX; salto++) {
            if (esJugador.test(actual)) {
                return Optional.of(actual);
            }
            actual = dueno.apply(actual);
        }
        return Optional.empty();
    }
}
