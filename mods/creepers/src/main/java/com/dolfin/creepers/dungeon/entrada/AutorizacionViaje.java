package com.dolfin.creepers.dungeon.entrada;

import com.dolfin.creepers.DolfinCreepers;

import net.minecraft.world.entity.Entity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Autoriza el regreso a dimensiones con control de viajes propio. DolfinSpace bloquea todo
 * teletransporte a {@code dolfinspace:kepler} sin autorización; sin esto, quien entró a una dungeon
 * desde Kepler no podría volver a su sitio. Se usa por reflexión para no depender del mod.
 */
final class AutorizacionViaje {
    private static final String MOTIVO = "dolfincreepers_dungeon_return";
    private static final Optional<Method> SPACE = buscarSpace();

    private AutorizacionViaje() {}

    /** Ejecuta {@code viaje} con la autorización de viaje de Space si está instalado. */
    static boolean conAutorizacion(Entity entidad, Supplier<Boolean> viaje) {
        if (SPACE.isEmpty()) {
            return viaje.get();
        }
        try {
            return Boolean.TRUE.equals(SPACE.get().invoke(null, entidad, MOTIVO, viaje));
        } catch (IllegalAccessException e) {
            DolfinCreepers.LOGGER.warn(
                    "event=dungeon_space_travel_auth_failed reason={}", e.toString());
            return viaje.get();
        } catch (InvocationTargetException e) {
            // El propio viaje falló: quien llama manda al jugador al spawn del mundo.
            DolfinCreepers.LOGGER.error("event=dungeon_return_teleport_failed", e.getCause());
            return false;
        }
    }

    private static Optional<Method> buscarSpace() {
        try {
            Class<?> acceso = Class.forName("com.dolfin.space.SpaceTravelAccess");
            return Optional.of(
                    acceso.getMethod(
                            "supplyAuthorized", Entity.class, String.class, Supplier.class));
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return Optional.empty();
        }
    }
}
