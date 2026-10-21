package com.dolfin.creepers.dungeon.grupo;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.UUID;

/** Envío de mensajes a jugadores por UUID y botones clicables de chat. */
public final class Chat {
    private Chat() {}

    /** Envía {@code mensaje} a cada jugador conectado de {@code destinatarios}. */
    public static void enviar(
            MinecraftServer server, Collection<UUID> destinatarios, Component mensaje) {
        if (server == null) {
            return;
        }
        for (UUID id : destinatarios) {
            ServerPlayer jugador = server.getPlayerList().getPlayer(id);
            if (jugador != null) {
                jugador.sendSystemMessage(mensaje);
            }
        }
    }

    /** Texto de color. */
    public static MutableComponent texto(String texto, ChatFormatting color) {
        return Component.literal(texto).withStyle(color);
    }

    /**
     * Botón de chat: al pulsarlo el jugador ejecuta {@code comando} (con la barra inicial).
     *
     * @param ayuda texto al pasar el ratón por encima
     */
    public static MutableComponent boton(
            String etiqueta, ChatFormatting color, String comando, String ayuda) {
        return Component.literal("[" + etiqueta + "]")
                .withStyle(
                        estilo ->
                                estilo.withColor(color)
                                        .withBold(true)
                                        .withClickEvent(new ClickEvent.RunCommand(comando))
                                        .withHoverEvent(
                                                new HoverEvent.ShowText(Component.literal(ayuda))));
    }
}
