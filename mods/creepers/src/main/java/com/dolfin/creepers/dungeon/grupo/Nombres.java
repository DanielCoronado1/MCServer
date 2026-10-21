package com.dolfin.creepers.dungeon.grupo;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nombres de los jugadores vistos desde que arrancó el servidor, para escribir mensajes sobre
 * miembros desconectados. Se rellena al conectarse cada jugador.
 */
public final class Nombres {
    private final Map<UUID, String> nombres = new ConcurrentHashMap<>();

    public void recordar(ServerPlayer jugador) {
        nombres.put(jugador.getUUID(), jugador.getName().getString());
    }

    /** Nombre conocido o, si nunca se conectó, su UUID. */
    public String de(UUID jugador) {
        return nombres.getOrDefault(jugador, jugador.toString());
    }

    /** Jugador conocido con ese nombre, sin distinguir mayúsculas. */
    public Optional<UUID> idDe(String nombre) {
        return nombres.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(nombre))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public void olvidarTodo() {
        nombres.clear();
    }
}
