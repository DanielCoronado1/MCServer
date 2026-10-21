package com.dolfin.creepers.dungeon;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.entrada.DatosDungeons;
import com.dolfin.creepers.dungeon.entrada.EntradaSalida;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.Miembro;
import com.dolfin.creepers.dungeon.modelo.MotivoSalida;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.Candidato;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.Rechazo;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.ResultadoInicio;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.TipoRechazo;
import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;
import com.dolfin.creepers.dungeon.mundo.Reiniciador;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Fachada de alto nivel para empezar, abandonar, terminar y reiniciar partidas con jugadores
 * reales. Une el modelo ({@link RegistroDungeons}), la entrada/salida segura ({@link
 * EntradaSalida}) y el {@link Reiniciador} de zonas. Es lo que usan los comandos (#13), la fila
 * (#14) y el fin de partida (#17).
 */
public final class ServicioPartidas {
    private final RegistroDungeons registro;
    private final EntradaSalida entradaSalida;
    private final Supplier<Reiniciador> reiniciador;

    ServicioPartidas(
            RegistroDungeons registro,
            EntradaSalida entradaSalida,
            Supplier<Reiniciador> reiniciador) {
        this.registro = registro;
        this.entradaSalida = entradaSalida;
        this.reiniciador = reiniciador;
    }

    /**
     * Empieza una partida y lleva a los jugadores a la entrada. Rechaza con motivos si la dungeon
     * no está libre, la dificultad o el tamaño no valen, o algún jugador está en
     * creativo/espectador o ya ocupado. Los permisos, requisitos y cooldowns (#14) se comprueban
     * antes de llamar.
     */
    public ResultadoInicio iniciar(
            MinecraftServer server,
            String dungeonId,
            Dificultad dificultad,
            List<ServerPlayer> jugadores) {
        if (DimensionDungeons.nivel(server).isEmpty()) {
            DolfinCreepers.LOGGER.error("event=dungeon_dimension_missing dungeon={}", dungeonId);
            return new ResultadoInicio.Rechazada(
                    List.of(new Rechazo(TipoRechazo.SISTEMA_DESACTIVADO, Optional.empty())));
        }
        List<Candidato> candidatos =
                jugadores.stream()
                        .map(
                                j ->
                                        new Candidato(
                                                j.getUUID(),
                                                j.getName().getString(),
                                                EntradaSalida.modoPermiteEntrar(j)))
                        .toList();
        ResultadoInicio resultado =
                registro.iniciarPartida(
                        dungeonId,
                        dificultad,
                        candidatos,
                        server.getTickCount(),
                        System.currentTimeMillis());
        if (resultado instanceof ResultadoInicio.Iniciada(Partida partida)) {
            DolfinCreepers.LOGGER.info(
                    "event=dungeon_match_started dungeon={} difficulty={} players={} match={}",
                    dungeonId,
                    dificultad.id(),
                    candidatos.size(),
                    partida.id());
            entradaSalida.entrar(server, partida, jugadores);
        }
        return resultado;
    }

    /** Saca a un jugador de su partida ({@code /dungeon leave}, AFK, expulsión). */
    public boolean salir(ServerPlayer jugador, MotivoSalida motivo) {
        if (registro.partidaDe(jugador.getUUID()).isEmpty()) {
            return false;
        }
        entradaSalida.sacar(jugador, motivo);
        return true;
    }

    /**
     * Termina la partida de una dungeon: restaura a los miembros conectados (los desconectados se
     * restauran al volver), publica {@code PartidaTerminada} y lanza el reinicio de la zona.
     */
    public Optional<Partida> terminar(
            MinecraftServer server, String dungeonId, ResultadoPartida resultado) {
        Optional<Partida> partida = registro.partida(dungeonId);
        if (partida.isEmpty()) {
            return Optional.empty();
        }
        for (Miembro miembro : partida.get().miembros()) {
            ServerPlayer jugador = server.getPlayerList().getPlayer(miembro.id());
            if (jugador != null && miembro.estado().participa()) {
                entradaSalida.sacar(jugador, MotivoSalida.FIN_DE_PARTIDA);
            }
        }
        registro.terminarPartida(dungeonId, resultado);
        DolfinCreepers.LOGGER.info(
                "event=dungeon_match_ended dungeon={} result={} match={}",
                dungeonId,
                resultado,
                partida.get().id());
        reiniciar(server, dungeonId);
        return partida;
    }

    /**
     * Pone la dungeon en {@code REINICIANDO} y llama al {@link Reiniciador}; al acabar queda {@code
     * LIBRE}. Falla si tiene una partida en curso (terminarla antes) o no existe.
     */
    public boolean reiniciar(MinecraftServer server, String dungeonId) {
        Optional<DefinicionDungeon> definicion = registro.config().dungeon(dungeonId);
        if (definicion.isEmpty() || !registro.marcarReiniciando(dungeonId)) {
            return false;
        }
        server.execute(
                () ->
                        reiniciador
                                .get()
                                .reiniciar(
                                        server,
                                        definicion.get(),
                                        () -> registro.finalizarReinicio(dungeonId)));
        return true;
    }

    /** Reinicia las dungeons que tenían partida o reinicio en curso al caerse el servidor. */
    void recuperarTrasArranque(MinecraftServer server) {
        DatosDungeons datos = DatosDungeons.de(server);
        for (String dungeonId : datos.pendientesDeReinicio()) {
            boolean lanzado = reiniciar(server, dungeonId);
            DolfinCreepers.LOGGER.warn(
                    "event=dungeon_recovered_after_restart dungeon={} resetStarted={}",
                    dungeonId,
                    lanzado);
            if (!lanzado) {
                datos.descartarPendiente(dungeonId);
            }
        }
    }

    /** Red de seguridad: una partida sin participantes termina como abandonada. */
    void tick(MinecraftServer server) {
        for (Partida partida : registro.partidas()) {
            if (!partida.hayParticipantes()) {
                terminar(server, partida.dungeon().id(), ResultadoPartida.ABANDONADA);
            }
        }
    }
}
