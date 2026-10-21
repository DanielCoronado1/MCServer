package com.dolfin.creepers.dungeon.fin;

import com.dolfin.creepers.dungeon.modelo.Miembro;
import com.dolfin.creepers.dungeon.modelo.Partida;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Mensajes de chat del fin de partida: al grupo (si la config los permite) y a todo el servidor.
 */
final class ChatFin {
    private ChatFin() {}

    /** Envía {@code texto} a los miembros conectados que siguen en la partida. */
    static void alGrupo(
            MinecraftServer server, Partida partida, String texto, ChatFormatting color) {
        if (!partida.config().general().mensajesChat()) {
            return;
        }
        Component mensaje = Component.literal(texto).withStyle(color);
        for (Miembro miembro : partida.miembros()) {
            ServerPlayer jugador = server.getPlayerList().getPlayer(miembro.id());
            if (jugador != null && miembro.estado().participa()) {
                jugador.sendSystemMessage(mensaje);
            }
        }
    }

    /** Anuncio visible para todos los jugadores del servidor. */
    static void atodos(MinecraftServer server, String texto, ChatFormatting color) {
        server.getPlayerList()
                .broadcastSystemMessage(Component.literal(texto).withStyle(color), false);
    }
}
