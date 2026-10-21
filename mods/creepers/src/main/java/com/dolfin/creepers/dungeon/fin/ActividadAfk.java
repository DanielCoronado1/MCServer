package com.dolfin.creepers.dungeon.fin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Detector de inactividad (#17), puro. Un jugador está activo mientras su {@link Huella} (posición
 * y orientación) cambie; se considera AFK cuando lleva {@code limite} segundos sin cambiarla. Solo
 * se vigila a quien pasa por {@link #retener}: al volver a estar vigilado (reconexión, vuelve de
 * espectador) la cuenta empieza de cero.
 */
public final class ActividadAfk {
    /** Posición y orientación de un jugador en un instante. */
    public record Huella(double x, double y, double z, float yaw, float pitch) {}

    private record Observacion(Huella huella, long desdeSegundo) {}

    private final Map<UUID, Observacion> observaciones = new HashMap<>();

    /** Anota la huella actual; si cambió desde la última, reinicia la cuenta del jugador. */
    public void observar(UUID jugador, Huella huella, long ahoraSegundos) {
        Observacion previa = observaciones.get(jugador);
        if (previa == null || !previa.huella().equals(huella)) {
            observaciones.put(jugador, new Observacion(huella, ahoraSegundos));
        }
    }

    /** Anota actividad que no mueve al jugador (por ejemplo, atacar), reiniciando su cuenta. */
    public void tocar(UUID jugador, long ahoraSegundos) {
        Observacion previa = observaciones.get(jugador);
        if (previa != null) {
            observaciones.put(jugador, new Observacion(previa.huella(), ahoraSegundos));
        }
    }

    /** Olvida a todos los que no estén en {@code vigilados}. */
    public void retener(Set<UUID> vigilados) {
        observaciones.keySet().retainAll(vigilados);
    }

    /** Jugadores vigilados que llevan al menos {@code limiteSegundos} sin cambiar de huella. */
    public List<UUID> vencidos(long ahoraSegundos, int limiteSegundos) {
        return observaciones.entrySet().stream()
                .filter(e -> ahoraSegundos - e.getValue().desdeSegundo() >= limiteSegundos)
                .map(Map.Entry::getKey)
                .toList();
    }
}
