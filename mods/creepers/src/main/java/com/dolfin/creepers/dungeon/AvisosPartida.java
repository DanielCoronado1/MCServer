package com.dolfin.creepers.dungeon;

import com.dolfin.creepers.dungeon.eventos.BusEventos;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorDesconectado;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorReconectado;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorSalio;
import com.dolfin.creepers.dungeon.modelo.Miembro;
import com.dolfin.creepers.dungeon.modelo.MotivoSalida;
import com.dolfin.creepers.dungeon.modelo.Partida;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Supplier;

/** Mensajes al grupo por desconexiones y reconexiones (#12). */
final class AvisosPartida {
    private final Supplier<MinecraftServer> server;

    AvisosPartida(Supplier<MinecraftServer> server) {
        this.server = server;
    }

    void suscribir(BusEventos bus) {
        bus.suscribir(
                JugadorDesconectado.class,
                e ->
                        alGrupo(
                                e.partida(),
                                nombre(e.partida(), e.jugador())
                                        + " se desconectó ("
                                        + e.segundosParaVolver()
                                        + " s para volver)"));
        bus.suscribir(
                JugadorReconectado.class,
                e ->
                        alGrupo(
                                e.partida(),
                                nombre(e.partida(), e.jugador()) + " volvió a la partida"));
        bus.suscribir(
                JugadorSalio.class,
                e -> {
                    if (e.motivo() == MotivoSalida.DESCONEXION) {
                        alGrupo(
                                e.partida(),
                                nombre(e.partida(), e.jugador())
                                        + " no volvió a tiempo y salió de la partida");
                    }
                });
    }

    private void alGrupo(Partida partida, String texto) {
        MinecraftServer s = server.get();
        if (s == null || !partida.config().general().mensajesChat()) {
            return;
        }
        Component mensaje = Component.literal(texto).withStyle(ChatFormatting.YELLOW);
        for (Miembro miembro : partida.miembros()) {
            ServerPlayer jugador = s.getPlayerList().getPlayer(miembro.id());
            if (jugador != null && miembro.estado().participa()) {
                jugador.sendSystemMessage(mensaje);
            }
        }
    }

    private static String nombre(Partida partida, UUID jugador) {
        return partida.miembro(jugador).map(Miembro::nombre).orElse(jugador.toString());
    }
}
