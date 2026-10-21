package com.dolfin.creepers.dungeon.grupo;

import com.dolfin.creepers.dungeon.grupo.Grupos.Evento;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Mensajes de chat de los cambios de {@link Grupos}: invitaciones con botones y avisos al grupo.
 */
public final class AvisosGrupo {
    private final Supplier<MinecraftServer> server;
    private final Nombres nombres;

    public AvisosGrupo(Supplier<MinecraftServer> server, Nombres nombres) {
        this.server = server;
        this.nombres = nombres;
    }

    /** Escucha los eventos de {@code grupos}. */
    public void suscribir(Grupos grupos) {
        grupos.suscribir(this::avisar);
    }

    private void avisar(Evento evento) {
        switch (evento) {
            case Evento.Creado e ->
                    a(List.of(e.lider()), "Grupo creado. Invita con /party invite <jugador>.");
            case Evento.Invitado e -> invitacion(e);
            case Evento.InvitacionVencida e -> {
                a(
                        List.of(e.invitador()),
                        "La invitación a " + nombres.de(e.objetivo()) + " venció.");
                a(
                        List.of(e.objetivo()),
                        "La invitación de " + nombres.de(e.invitador()) + " venció.");
            }
            case Evento.InvitacionRechazada e ->
                    a(List.of(e.invitador()), nombres.de(e.objetivo()) + " rechazó tu invitación.");
            case Evento.Unido e -> a(e.miembros(), nombres.de(e.jugador()) + " se unió al grupo.");
            case Evento.Salio e -> salida(e);
            case Evento.NuevoLider e ->
                    a(e.miembros(), nombres.de(e.lider()) + " es ahora el líder del grupo.");
            case Evento.Disuelto e -> {}
        }
    }

    private void invitacion(Evento.Invitado e) {
        a(List.of(e.invitador()), "Invitación enviada a " + nombres.de(e.objetivo()) + ".");
        MutableComponent mensaje =
                Chat.texto(
                        nombres.de(e.invitador())
                                + " te invita a su grupo de dungeons ("
                                + Grupos.INVITACION_MS / 1000
                                + " s). ",
                        ChatFormatting.YELLOW);
        mensaje.append(
                        Chat.boton(
                                "Aceptar",
                                ChatFormatting.GREEN,
                                "/party accept",
                                "Unirte al grupo"))
                .append(Component.literal(" "))
                .append(
                        Chat.boton(
                                "Rechazar",
                                ChatFormatting.RED,
                                "/party deny",
                                "Rechazar la invitación"));
        Chat.enviar(server.get(), List.of(e.objetivo()), mensaje);
    }

    private void salida(Evento.Salio e) {
        String nombre = nombres.de(e.jugador());
        a(e.miembros(), TextosGrupo.salida(nombre, e.motivo()));
        switch (e.motivo()) {
            case VOLUNTARIO -> a(List.of(e.jugador()), "Has salido del grupo.");
            case EXPULSADO -> a(List.of(e.jugador()), "Has sido expulsado del grupo.");
            case DESCONEXION -> {}
        }
    }

    private void a(List<UUID> destinatarios, String texto) {
        Chat.enviar(server.get(), destinatarios, Chat.texto(texto, ChatFormatting.YELLOW));
    }
}
