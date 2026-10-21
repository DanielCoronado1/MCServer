package com.dolfin.creepers.dungeon.muerte;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

import java.util.UUID;

/** Única clase que toca la API de LuckPerms; solo se carga si el mod está instalado. */
final class ConsultaLuckPerms {
    private ConsultaLuckPerms() {}

    /** Valor explícito del nodo para un usuario cargado; false si no está cargado o no lo tiene. */
    static boolean permitido(UUID jugador, String nodo) {
        User usuario = LuckPermsProvider.get().getUserManager().getUser(jugador);
        return usuario != null
                && usuario.getCachedData().getPermissionData().checkPermission(nodo).asBoolean();
    }
}
