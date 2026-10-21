package com.dolfin.creepers.dungeon.comando;

import com.dolfin.creepers.dungeon.modelo.Zona;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Las dos esquinas que cada administrador marca con la varita de zona; {@code setzone} las
 * convierte en una {@link Zona}. Solo en memoria y en el hilo del servidor.
 */
final class SeleccionesZona {
    /** Un bloque marcado. */
    record Esquina(int x, int y, int z) {}

    private record Par(Optional<Esquina> primera, Optional<Esquina> segunda) {}

    private final Map<UUID, Par> pares = new HashMap<>();

    void marcarPrimera(UUID admin, Esquina esquina) {
        Par actual = pares.getOrDefault(admin, new Par(Optional.empty(), Optional.empty()));
        pares.put(admin, new Par(Optional.of(esquina), actual.segunda()));
    }

    void marcarSegunda(UUID admin, Esquina esquina) {
        Par actual = pares.getOrDefault(admin, new Par(Optional.empty(), Optional.empty()));
        pares.put(admin, new Par(actual.primera(), Optional.of(esquina)));
    }

    /** La caja entre ambas esquinas (inclusivas, en cualquier orden); vacía si falta alguna. */
    Optional<Zona> zona(UUID admin) {
        Par par = pares.get(admin);
        if (par == null || par.primera().isEmpty() || par.segunda().isEmpty()) {
            return Optional.empty();
        }
        Esquina a = par.primera().get();
        Esquina b = par.segunda().get();
        return Optional.of(Zona.entre(a.x(), a.y(), a.z(), b.x(), b.y(), b.z()));
    }

    /** Olvida lo marcado por un administrador, por ejemplo al desconectarse. */
    void olvidar(UUID admin) {
        pares.remove(admin);
    }
}
