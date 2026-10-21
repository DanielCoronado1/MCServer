package com.dolfin.creepers.dungeon.comando;

import com.dolfin.creepers.DolfinCreepers;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Consulta directa a LuckPerms, por si no responde a la API de permisos de Fabric. Solo se carga si
 * el mod está presente; las firmas no exponen tipos de LuckPerms.
 */
final class PermisosLuckPerms {
    private PermisosLuckPerms() {}

    /** Valor del nodo para el jugador del origen, o {@code null} si no está definido. */
    static Boolean explicito(CommandSourceStack origen, String nodo) {
        ServerPlayer jugador = origen.getPlayer();
        if (jugador == null) {
            return null;
        }
        try {
            User usuario = LuckPermsProvider.get().getUserManager().getUser(jugador.getUUID());
            if (usuario == null) {
                return null;
            }
            Tristate valor = usuario.getCachedData().getPermissionData().checkPermission(nodo);
            return valor == Tristate.UNDEFINED ? null : valor.asBoolean();
        } catch (IllegalStateException e) {
            DolfinCreepers.LOGGER.warn(
                    "event=dungeon_luckperms_unavailable node={} error={}", nodo, e.toString());
            return null;
        }
    }
}
