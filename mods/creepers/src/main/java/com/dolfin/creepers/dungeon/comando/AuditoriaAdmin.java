package com.dolfin.creepers.dungeon.comando;

import com.dolfin.creepers.DolfinCreepers;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** Deja en el log quién ejecutó cada comando de administración y con qué efecto. */
final class AuditoriaAdmin {
    private AuditoriaAdmin() {}

    /** Registra {@code accion} (p. ej. {@code stop}) con su detalle y quién la ejecutó. */
    static void registrar(CommandSourceStack origen, String accion, String detalle) {
        ServerPlayer jugador = origen.getPlayer();
        DolfinCreepers.LOGGER.info(
                "event=dungeon_admin_command executor={} executorId={} action={} detail=\"{}\"",
                origen.getTextName(),
                jugador == null ? "console" : jugador.getUUID(),
                accion,
                detalle);
    }
}
