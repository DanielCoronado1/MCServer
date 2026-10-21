package com.dolfin.creepers.dungeon.entrada;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.eventos.BusEventos;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorRestaurado;
import com.dolfin.creepers.dungeon.modelo.EstadoMiembro;
import com.dolfin.creepers.dungeon.modelo.Miembro;
import com.dolfin.creepers.dungeon.modelo.MotivoSalida;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.Punto;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Entrada y salida a prueba de caídas (#12). Garantiza que todo jugador vuelve a la posición,
 * rotación y modo de juego que tenía al entrar, pase lo que pase:
 *
 * <ul>
 *   <li>{@link #entrar}: guarda el retorno en {@link DatosDungeons} y lo escribe en disco
 *       <em>antes</em> de teletransportar al punto de entrada en modo aventura.
 *   <li>{@link #sacar}: toda salida (victoria, derrota, leave, tiempo, expulsión) termina aquí.
 *   <li>{@link #alConectarse}: un miembro desconectado vuelve a su checkpoint; cualquier otro con
 *       retorno guardado o dentro de la dimensión se restaura (o va al spawn del mundo).
 *   <li>{@link #alDesconectarse}: el miembro queda {@code DESCONECTADO} con plazo para volver; el
 *       retorno se conserva en disco.
 *   <li>{@link #alSalirDeLaDimension}: quien deja la dimensión por otro medio (tp de admin,
 *       reaparecer fuera) sale de la partida y recupera su modo de juego donde esté.
 * </ul>
 *
 * <p>La partida (modelo) solo vive en memoria; el retorno en disco es lo único que hace falta para
 * recuperarse de una caída, porque al conectarse sin partida activa siempre se restaura.
 */
public final class EntradaSalida {
    private final RegistroDungeons registro;
    private final BusEventos bus;
    private boolean deteniendo;

    public EntradaSalida(RegistroDungeons registro, BusEventos bus) {
        this.registro = registro;
        this.bus = bus;
    }

    /** Solo se entra desde supervivencia o aventura: nunca en creativo ni espectador. */
    public static boolean modoPermiteEntrar(ServerPlayer jugador) {
        GameType modo = jugador.gameMode();
        return modo == GameType.SURVIVAL || modo == GameType.ADVENTURE;
    }

    /**
     * Lleva a los miembros de una partida recién iniciada a la entrada, en modo aventura. Primero
     * guarda y escribe en disco el retorno de todos; luego teletransporta.
     *
     * @return jugadores que no se pudieron teletransportar; ya salieron de la partida
     */
    public List<ServerPlayer> entrar(
            MinecraftServer server, Partida partida, List<ServerPlayer> jugadores) {
        DatosDungeons datos = DatosDungeons.de(server);
        for (ServerPlayer jugador : jugadores) {
            datos.guardarRetornoSiFalta(jugador.getUUID(), RetornoGuardado.de(jugador));
        }
        DatosDungeons.guardarYa(server);
        List<ServerPlayer> fallidos = new ArrayList<>();
        for (ServerPlayer jugador : jugadores) {
            if (llevarADungeon(server, jugador, partida.dungeon().entrada(), GameType.ADVENTURE)) {
                DolfinCreepers.LOGGER.info(
                        "event=dungeon_enter player={} dungeon={} difficulty={} match={}",
                        jugador.getName().getString(),
                        partida.dungeon().id(),
                        partida.dificultad().id(),
                        partida.id());
            } else {
                fallidos.add(jugador);
                sacar(jugador, MotivoSalida.EXPULSION);
            }
        }
        return fallidos;
    }

    /**
     * Lleva a un administrador a {@code punto} en espectador, sin partida ({@code /dungeon admin
     * tp}). Guarda su retorno antes de teletransportar, así {@link #restaurar} (con {@code /dungeon
     * leave}, al conectarse o al salir de la dimensión) lo devuelve a su sitio y modo.
     *
     * @return {@code false} si no se pudo teletransportar; no queda retorno nuevo
     */
    public boolean observar(MinecraftServer server, ServerPlayer jugador, Punto punto) {
        DatosDungeons datos = DatosDungeons.de(server);
        boolean teniaRetorno = datos.retorno(jugador.getUUID()).isPresent();
        datos.guardarRetornoSiFalta(jugador.getUUID(), RetornoGuardado.de(jugador));
        DatosDungeons.guardarYa(server);
        if (llevarADungeon(server, jugador, punto, GameType.SPECTATOR)) {
            DolfinCreepers.LOGGER.info(
                    "event=dungeon_observe player={}", jugador.getName().getString());
            return true;
        }
        if (!teniaRetorno) {
            datos.quitarRetorno(jugador.getUUID());
            DatosDungeons.guardarPronto(server);
        }
        return false;
    }

    /** Saca a un jugador de su partida (si está en una) y lo restaura. */
    public void sacar(ServerPlayer jugador, MotivoSalida motivo) {
        registro.sacarMiembro(jugador.getUUID(), motivo);
        restaurar(jugador);
        DolfinCreepers.LOGGER.info(
                "event=dungeon_exit player={} reason={}", jugador.getName().getString(), motivo);
    }

    /**
     * Devuelve al jugador a su retorno guardado y lo borra. Sin retorno, solo actúa si está dentro
     * de la dimensión: lo manda al spawn del mundo. El retorno se borra antes de teletransportar,
     * así {@link #alSalirDeLaDimension} no confunde esta salida con una ajena.
     */
    public void restaurar(ServerPlayer jugador) {
        MinecraftServer server = jugador.level().getServer();
        Optional<RetornoGuardado> retorno =
                DatosDungeons.de(server).quitarRetorno(jugador.getUUID());
        if (retorno.isEmpty() && !DimensionDungeons.es(jugador.level())) {
            return;
        }
        RetornoGuardado destino = retorno.orElseGet(() -> spawnDelMundo(server, jugador));
        if (!teletransportar(server, jugador, destino)) {
            // Destino inaccesible (dimensión quitada, viaje bloqueado por otro mod): spawn del
            // mundo.
            teletransportar(server, jugador, spawnDelMundo(server, jugador));
        }
        jugador.setGameMode(destino.modo());
        DatosDungeons.guardarPronto(server);
        bus.publicar(new JugadorRestaurado(jugador.getUUID(), retorno.isPresent()));
    }

    /**
     * Lleva a un miembro a su punto de reaparición (último checkpoint o entrada) con el modo dado:
     * reaparición tras morir (#16) y confinamiento de espectadores.
     *
     * @return false si no se pudo teletransportar
     */
    public boolean llevarAReaparicion(ServerPlayer jugador, Partida partida, GameType modo) {
        return llevarADungeon(
                jugador.level().getServer(),
                jugador,
                partida.puntoDeReaparicion(jugador.getUUID()),
                modo);
    }

    /**
     * Si el jugador pertenece al sistema de dungeons: miembro de una partida, con un retorno
     * guardado o dentro de la dimensión. Otros mods lo consultan para no mover ni capturar a quien
     * las dungeons aún gobiernan.
     */
    public boolean ocupado(ServerPlayer jugador) {
        return registro.partidaDe(jugador.getUUID()).isPresent()
                || DatosDungeons.de(jugador.level().getServer())
                        .retorno(jugador.getUUID())
                        .isPresent()
                || DimensionDungeons.es(jugador.level());
    }

    /** Reconecta a un miembro desconectado o restaura a quien quedó dentro sin partida. */
    public void alConectarse(ServerPlayer jugador) {
        MinecraftServer server = jugador.level().getServer();
        Optional<Partida> partida = registro.reconectar(jugador.getUUID());
        if (partida.isPresent()) {
            volverAPartida(server, jugador, partida.get());
            return;
        }
        boolean tieneRetorno = DatosDungeons.de(server).retorno(jugador.getUUID()).isPresent();
        if (tieneRetorno || DimensionDungeons.es(jugador.level())) {
            DolfinCreepers.LOGGER.info(
                    "event=dungeon_restore_on_join player={} savedReturn={} inDungeonDimension={}",
                    jugador.getName().getString(),
                    tieneRetorno,
                    DimensionDungeons.es(jugador.level()));
            restaurar(jugador);
        }
    }

    /** Marca al miembro como desconectado; el resto lo resuelve el plazo de reconexión. */
    public void alDesconectarse(ServerPlayer jugador) {
        if (deteniendo) {
            return;
        }
        long tick = jugador.level().getServer().getTickCount();
        registro.desconectar(jugador.getUUID(), tick)
                .ifPresent(
                        p ->
                                DolfinCreepers.LOGGER.info(
                                        "event=dungeon_member_disconnected player={} dungeon={}"
                                                + " graceSeconds={}",
                                        jugador.getName().getString(),
                                        p.dungeon().id(),
                                        p.config().partida().reconexionSegundos()));
    }

    /**
     * Quien deja la dimensión con un retorno pendiente lo hizo por un medio ajeno (tp de admin,
     * reaparición fuera, portal de otro mod): sale de la partida y recupera su modo de juego donde
     * esté, para que nadie quede en aventura fuera de la dungeon.
     */
    public void alSalirDeLaDimension(ServerPlayer jugador) {
        if (DimensionDungeons.es(jugador.level())) {
            return;
        }
        MinecraftServer server = jugador.level().getServer();
        Optional<RetornoGuardado> retorno =
                DatosDungeons.de(server).quitarRetorno(jugador.getUUID());
        if (retorno.isEmpty()) {
            return;
        }
        registro.sacarMiembro(jugador.getUUID(), MotivoSalida.FUERA_DE_DIMENSION);
        jugador.setGameMode(retorno.get().modo());
        DatosDungeons.guardarPronto(server);
        DolfinCreepers.LOGGER.warn(
                "event=dungeon_left_dimension_externally player={} now={} gameMode={}",
                jugador.getName().getString(),
                jugador.level().dimension().identifier(),
                retorno.get().modo().getName());
        bus.publicar(new JugadorRestaurado(jugador.getUUID(), true));
    }

    /**
     * Al parar el servidor nadie pasa a desconectado: los retornos quedan en disco y la próxima
     * conexión restaura, igual que tras una caída.
     */
    public void alDetenerse() {
        deteniendo = true;
    }

    /** Al arrancar un servidor nuevo. */
    public void alArrancar() {
        deteniendo = false;
    }

    private void volverAPartida(MinecraftServer server, ServerPlayer jugador, Partida partida) {
        GameType modo =
                partida.miembro(jugador.getUUID()).map(Miembro::estado).orElse(EstadoMiembro.VIVO)
                                == EstadoMiembro.ESPECTADOR
                        ? GameType.SPECTATOR
                        : GameType.ADVENTURE;
        Punto punto = partida.puntoDeReaparicion(jugador.getUUID());
        if (!llevarADungeon(server, jugador, punto, modo)) {
            sacar(jugador, MotivoSalida.EXPULSION);
            return;
        }
        DolfinCreepers.LOGGER.info(
                "event=dungeon_member_reconnected player={} dungeon={}",
                jugador.getName().getString(),
                partida.dungeon().id());
    }

    private boolean llevarADungeon(
            MinecraftServer server, ServerPlayer jugador, Punto punto, GameType modo) {
        Optional<ServerLevel> nivel = DimensionDungeons.nivel(server);
        if (nivel.isEmpty()) {
            DolfinCreepers.LOGGER.error("event=dungeon_dimension_missing");
            return false;
        }
        boolean llego =
                jugador.teleport(
                                new TeleportTransition(
                                        nivel.get(),
                                        new Vec3(punto.x(), punto.y(), punto.z()),
                                        Vec3.ZERO,
                                        punto.yaw(),
                                        punto.pitch(),
                                        TeleportTransition.DO_NOTHING))
                        != null;
        if (llego) {
            jugador.setGameMode(modo);
        }
        return llego;
    }

    private static boolean teletransportar(
            MinecraftServer server, ServerPlayer jugador, RetornoGuardado destino) {
        ServerLevel nivel = server.getLevel(destino.dimension());
        if (nivel == null) {
            return false;
        }
        TeleportTransition viaje =
                new TeleportTransition(
                        nivel,
                        new Vec3(destino.x(), destino.y(), destino.z()),
                        Vec3.ZERO,
                        destino.yaw(),
                        destino.pitch(),
                        TeleportTransition.DO_NOTHING);
        return AutorizacionViaje.conAutorizacion(jugador, () -> jugador.teleport(viaje) != null);
    }

    /** Spawn del mundo con el modo por defecto si el jugador estaba en aventura; si no, el suyo. */
    private static RetornoGuardado spawnDelMundo(MinecraftServer server, ServerPlayer jugador) {
        LevelData.RespawnData spawn = server.getRespawnData();
        Vec3 pos = Vec3.atBottomCenterOf(spawn.pos());
        GameType modo =
                jugador.gameMode() == GameType.ADVENTURE
                        ? server.getDefaultGameType()
                        : jugador.gameMode();
        return new RetornoGuardado(
                spawn.dimension(), pos.x, pos.y, pos.z, spawn.yaw(), spawn.pitch(), modo);
    }
}
