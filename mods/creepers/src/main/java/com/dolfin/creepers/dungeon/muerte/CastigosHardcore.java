package com.dolfin.creepers.dungeon.muerte;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.entrada.EntradaSalida;
import com.dolfin.creepers.dungeon.eventos.BusEventos;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorMurio;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorSalio;
import com.dolfin.creepers.dungeon.modelo.CausaMuerte;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.Miembro;
import com.dolfin.creepers.dungeon.modelo.MotivoSalida;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.muerte.ReglasMuerte.Castigo;
import com.dolfin.creepers.dungeon.muerte.ReglasMuerte.OrigenCastigo;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Castigo de hardcore (#16): el caído pierde inventario y experiencia sin soltar nada, vuelve a su
 * posición de salida, recibe el ban temporal ({@link BanHardcore}) y se le desconecta. El ban se
 * escribe en el momento; la desconexión espera al final del tick para no cortar la conexión en
 * mitad del daño o de la salida que la provocó.
 *
 * <p>Orígenes ({@link ReglasMuerte#castigoHardcore}): muerte en combate ({@link #alMorir}), no
 * volver a tiempo ({@code JugadorMurio} con causa {@code DESCONEXION}: se banea sin expulsar y el
 * inventario se vacía al volver), {@code /dungeon leave} y AFK ({@code JugadorSalio}).
 */
public final class CastigosHardcore {
    /** Desconexión pendiente hasta el final del tick. */
    private record Expulsion(UUID jugador, Component mensaje) {}

    private final EntradaSalida entradaSalida;
    private final Supplier<MinecraftServer> server;
    private final List<Expulsion> expulsiones = new ArrayList<>();

    public CastigosHardcore(EntradaSalida entradaSalida, Supplier<MinecraftServer> server) {
        this.entradaSalida = entradaSalida;
        this.server = server;
    }

    /** Castiga la desconexión sin vuelta, el abandono y el AFK en hardcore. */
    public void suscribir(BusEventos bus) {
        bus.suscribir(
                JugadorMurio.class,
                e -> {
                    if (e.causa() == CausaMuerte.DESCONEXION) {
                        castigarSiHardcore(e.partida(), e.jugador(), OrigenCastigo.DESCONEXION);
                    }
                });
        bus.suscribir(
                JugadorSalio.class,
                e -> {
                    if (e.motivo() == MotivoSalida.ABANDONO) {
                        castigarSiHardcore(e.partida(), e.jugador(), OrigenCastigo.ABANDONO);
                    } else if (e.motivo() == MotivoSalida.AFK) {
                        castigarSiHardcore(e.partida(), e.jugador(), OrigenCastigo.AFK);
                    }
                });
    }

    /**
     * Muerte en combate de un miembro conectado en hardcore (ya registrada en el modelo): lo vacía,
     * lo saca a su posición de salida, lo banea y programa su desconexión.
     */
    public void alMorir(ServerPlayer jugador, Partida partida) {
        castigar(jugador.level().getServer(), partida, jugador.getUUID(), OrigenCastigo.MUERTE);
    }

    /** Vacía a quien cayó en hardcore estando desconectado, al volver. */
    public void alConectarse(ServerPlayer jugador) {
        if (DatosMuerte.de(jugador.level().getServer()).quitarVaciado(jugador.getUUID())) {
            Inventarios.vaciar(jugador);
            jugador.sendSystemMessage(
                    Component.literal("Caíste en hardcore al no volver a tiempo: lo pierdes todo")
                            .withStyle(ChatFormatting.RED));
        }
    }

    /** Al final de cada tick: desconecta a los baneados. */
    public void tick(MinecraftServer s) {
        if (expulsiones.isEmpty()) {
            return;
        }
        List<Expulsion> pendientes = List.copyOf(expulsiones);
        expulsiones.clear();
        for (Expulsion expulsion : pendientes) {
            ServerPlayer jugador = s.getPlayerList().getPlayer(expulsion.jugador());
            if (jugador != null) {
                jugador.connection.disconnect(expulsion.mensaje());
            }
        }
    }

    /** Tras la victoria nadie pierde nada ni recibe ban; quien muere solo sale de la partida. */
    private void sinCastigoTrasVictoria(ServerPlayer jugador, OrigenCastigo origen) {
        if (origen == OrigenCastigo.MUERTE && jugador != null) {
            entradaSalida.sacar(jugador, MotivoSalida.MUERTE_HARDCORE);
        }
    }

    private void castigarSiHardcore(Partida partida, UUID jugador, OrigenCastigo origen) {
        MinecraftServer s = server.get();
        if (s != null && partida.dificultad() == Dificultad.HARDCORE) {
            castigar(s, partida, jugador, origen);
        }
    }

    private void castigar(MinecraftServer s, Partida partida, UUID id, OrigenCastigo origen) {
        ServerPlayer jugador = s.getPlayerList().getPlayer(id);
        if (partida.victoriaDeclarada()) {
            sinCastigoTrasVictoria(jugador, origen);
            return;
        }
        Castigo castigo = ReglasMuerte.castigoHardcore(origen, PermisoBypassBan.tiene(id));
        if (castigo.vaciar() && jugador == null) {
            DatosMuerte.de(s).marcarVaciado(id);
        } else if (castigo.vaciar()) {
            Inventarios.vaciar(jugador);
        }
        if (origen == OrigenCastigo.MUERTE && jugador != null) {
            entradaSalida.sacar(jugador, MotivoSalida.MUERTE_HARDCORE);
        }
        String nombre = partida.miembro(id).map(Miembro::nombre).orElse(id.toString());
        Optional<UserBanListEntry> ban =
                castigo.banear()
                        ? BanHardcore.aplicar(
                                s,
                                new NameAndId(id, nombre),
                                partida.dungeon().nombre(),
                                partida.config().partida(),
                                Instant.now())
                        : Optional.empty();
        if (castigo.expulsar() && jugador != null && ban.isPresent()) {
            expulsiones.add(new Expulsion(id, Component.literal(ban.get().getReason())));
        } else if (jugador != null) {
            jugador.sendSystemMessage(
                    Component.literal("Caíste en hardcore: pierdes tus objetos y tu experiencia")
                            .withStyle(ChatFormatting.RED));
        }
        DolfinCreepers.LOGGER.info(
                "event=dungeon_hardcore_penalty player={} origin={} banned={} kicked={} online={}"
                        + " match={}",
                nombre,
                origen,
                ban.isPresent(),
                castigo.expulsar() && ban.isPresent(),
                jugador != null,
                partida.id());
    }
}
