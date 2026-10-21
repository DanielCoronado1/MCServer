package com.dolfin.creepers.dungeon.fin;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Única clase de este paquete que toca la API de LuckPerms; solo se carga si el mod existe. */
final class TitulosLuckPerms {
    private TitulosLuckPerms() {}

    /**
     * Añade el nodo al usuario, esté conectado o no. LuckPerms lo hace fuera del hilo del servidor.
     */
    static CompletableFuture<Void> conceder(UUID jugador, String nodo) {
        return LuckPermsProvider.get()
                .getUserManager()
                .modifyUser(jugador, usuario -> usuario.data().add(Node.builder(nodo).build()));
    }
}
