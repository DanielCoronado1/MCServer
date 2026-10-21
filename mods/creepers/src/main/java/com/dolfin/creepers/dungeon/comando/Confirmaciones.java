package com.dolfin.creepers.dungeon.comando;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Confirmación por repetición de comandos peligrosos: la primera vez solo se avisa y repetir <em>la
 * misma acción</em> dentro de {@link #VENTANA_MS} la confirma. Cada jugador tiene como mucho una
 * acción pendiente; pedir otra distinta sustituye a la anterior. Se usa solo desde el hilo del
 * servidor y los tiempos los pasa quien llama, así las pruebas controlan el reloj.
 */
final class Confirmaciones {
    /** Tiempo para repetir el comando y confirmarlo. */
    static final long VENTANA_MS = 10_000;

    private record Pendiente(String accion, long venceMs) {}

    private final Map<UUID, Pendiente> pendientes = new HashMap<>();

    /**
     * Registra el intento de {@code accion}.
     *
     * @return {@code true} si ya había una igual sin vencer: queda confirmada y se consume; {@code
     *     false} si es la primera vez (o la anterior venció) y queda pendiente
     */
    boolean confirmar(UUID jugador, String accion, long ahoraMs) {
        pendientes.values().removeIf(p -> p.venceMs() <= ahoraMs);
        Pendiente previa = pendientes.get(jugador);
        if (previa != null && previa.accion().equals(accion)) {
            pendientes.remove(jugador);
            return true;
        }
        pendientes.put(jugador, new Pendiente(accion, ahoraMs + VENTANA_MS));
        return false;
    }

    /** Olvida lo pendiente de un jugador (al desconectarse o cancelar). */
    void olvidar(UUID jugador) {
        pendientes.remove(jugador);
    }
}
