package com.dolfin.creepers.dungeon.fila;

import com.dolfin.creepers.dungeon.fila.Filas.Efecto;
import com.dolfin.creepers.dungeon.grupo.Chat;
import com.dolfin.creepers.dungeon.grupo.Nombres;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Mensajes de chat de la fila: el ready check con sus botones, los avances de puesto y las salidas.
 * No dependen de {@code general.mensajesChat}: son parte del flujo de entrada.
 */
final class AvisosFila {
    private static final String COMANDO_LISTO = "/dungeon ready";
    private static final String COMANDO_SALIR = "/dungeon leave";

    private final Nombres nombres;
    private final Function<String, String> nombreDeDungeon;

    AvisosFila(Nombres nombres, Function<String, String> nombreDeDungeon) {
        this.nombres = nombres;
        this.nombreDeDungeon = nombreDeDungeon;
    }

    /**
     * Avisa al grupo de lo ocurrido en la fila.
     *
     * @param excepto jugador cuyo comando ya muestra el resultado, para no repetírselo
     */
    void avisar(MinecraftServer server, Efecto efecto, int readyCheckSegundos, UUID excepto) {
        switch (efecto) {
            case Efecto.ReadyCheckIniciado e -> readyCheck(server, e, readyCheckSegundos, excepto);
            case Efecto.MiembroListo e ->
                    a(server, e.miembros(), TextosFila.listo(nombres.de(e.jugador()), e.faltan()));
            case Efecto.PosicionCambiada e ->
                    a(
                            server,
                            e.miembros(),
                            TextosFila.avance(
                                    nombreDeDungeon.apply(e.dungeon()), e.posicion(), e.total()));
            case Efecto.Retirada e -> retirada(server, e, excepto);
            case Efecto.Completa e -> {}
        }
    }

    private void readyCheck(
            MinecraftServer server, Efecto.ReadyCheckIniciado e, int segundos, UUID excepto) {
        String dungeon = nombreDeDungeon.apply(e.dungeon());
        MutableComponent mensaje =
                Chat.texto(
                        TextosFila.readyCheck(dungeon, e.entrada().dificultad().id(), segundos),
                        ChatFormatting.YELLOW);
        mensaje.append(
                        Chat.boton(
                                "✔ Listo",
                                ChatFormatting.GREEN,
                                COMANDO_LISTO,
                                "Confirmar que estás listo"))
                .append(Component.literal(" "))
                .append(
                        Chat.boton(
                                "✘ Salir", ChatFormatting.RED, COMANDO_SALIR, "Salir de la fila"));
        Chat.enviar(server, e.pendientes(), mensaje);
        Set<UUID> yaListos = new HashSet<>(e.miembros());
        yaListos.removeAll(e.pendientes());
        yaListos.remove(excepto);
        if (e.pendientes().isEmpty()) {
            return;
        }
        Chat.enviar(
                server,
                yaListos,
                Chat.texto(
                        dungeon + " está libre. Esperando a los demás miembros del grupo.",
                        ChatFormatting.YELLOW));
    }

    private void retirada(MinecraftServer server, Efecto.Retirada e, UUID excepto) {
        Set<UUID> destinatarios = new HashSet<>(e.miembros());
        destinatarios.remove(excepto);
        Optional<String> culpable = e.culpable().map(nombres::de);
        String texto =
                TextosFila.retirada(e.motivo(), nombreDeDungeon.apply(e.dungeon()), culpable);
        Chat.enviar(server, destinatarios, Chat.texto(texto, ChatFormatting.RED));
    }

    private void a(MinecraftServer server, Collection<UUID> destinatarios, String texto) {
        Chat.enviar(server, destinatarios, Chat.texto(texto, ChatFormatting.YELLOW));
    }
}
