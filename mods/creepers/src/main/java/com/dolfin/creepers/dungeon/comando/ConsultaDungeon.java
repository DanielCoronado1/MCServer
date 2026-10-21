package com.dolfin.creepers.dungeon.comando;

import com.dolfin.creepers.dungeon.Dungeons;
import com.dolfin.creepers.dungeon.comando.VistaDungeon.EstadoVisible;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.EstadoDungeon;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.OptionalLong;

/**
 * Cómo ve un jugador cada dungeon (libre, ocupada, en fila, bloqueada); lo usan {@code list} y el
 * menú.
 */
final class ConsultaDungeon {
    private ConsultaDungeon() {}

    /** Estado visible de la dungeon para quien ejecuta el comando. */
    static EstadoVisible estadoVisible(CommandSourceStack origen, DefinicionDungeon d) {
        RegistroDungeons registro = Dungeons.registro();
        EstadoDungeon estado = registro.estado(d.id()).orElse(EstadoDungeon.DESHABILITADA);
        int tick = origen.getServer().getTickCount();
        OptionalLong segundos =
                registro.partida(d.id())
                        .map(
                                p ->
                                        OptionalLong.of(
                                                (tick - p.inicioTick())
                                                        / RegistroDungeons.TICKS_POR_SEGUNDO))
                        .orElse(OptionalLong.empty());
        return VistaDungeon.estado(
                estado, segundos, Dungeons.fila().gruposEnFila(d.id()), estaBloqueada(origen, d));
    }

    /** Sin permiso, o con un requisito previo o cooldown pendiente. */
    static boolean estaBloqueada(CommandSourceStack origen, DefinicionDungeon d) {
        ServerPlayer jugador = origen.getPlayer();
        return !PermisosDungeon.puedeEntrar(origen, d)
                || (jugador != null && Dungeons.fila().bloqueo(jugador.getUUID(), d).isPresent());
    }
}
