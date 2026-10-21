package com.dolfin.creepers.dungeon;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.comando.ComandoDungeon;
import com.dolfin.creepers.dungeon.config.DungeonsConfig;
import com.dolfin.creepers.dungeon.entrada.DatosDungeons;
import com.dolfin.creepers.dungeon.entrada.EntradaSalida;
import com.dolfin.creepers.dungeon.estadisticas.Estadisticas;
import com.dolfin.creepers.dungeon.eventos.BusEventos;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.DungeonCambioEstado;
import com.dolfin.creepers.dungeon.fila.EntradaDirecta;
import com.dolfin.creepers.dungeon.fila.ServicioFila;
import com.dolfin.creepers.dungeon.fin.EstadisticasRecords;
import com.dolfin.creepers.dungeon.fin.FinDePartida;
import com.dolfin.creepers.dungeon.fin.HistorialDungeons;
import com.dolfin.creepers.dungeon.fin.JefeDungeon;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;
import com.dolfin.creepers.dungeon.muerte.BloqueTumba;
import com.dolfin.creepers.dungeon.muerte.CastigosHardcore;
import com.dolfin.creepers.dungeon.muerte.FinPorBajas;
import com.dolfin.creepers.dungeon.muerte.Muertes;
import com.dolfin.creepers.dungeon.muerte.Tumbas;
import com.dolfin.creepers.dungeon.mundo.CargaChunks;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;
import com.dolfin.creepers.dungeon.mundo.Reiniciador;
import com.dolfin.creepers.dungeon.plantilla.ContenidoPlantillas;
import com.dolfin.creepers.dungeon.reglas.ReglasDungeon;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.util.Objects;

/**
 * Punto de entrada del sistema de dungeons: crea los módulos, los conecta al bus y a los eventos de
 * Fabric, y da acceso a ellos al resto de issues. Ver {@code package-info.java} para el mapa.
 */
public final class Dungeons {
    private static final BusEventos BUS =
            new BusEventos(
                    (evento, error) ->
                            DolfinCreepers.LOGGER.error(
                                    "event=dungeon_listener_failed event={}", evento, error));
    private static DungeonsConfig config;
    private static RegistroDungeons registro;
    private static EntradaSalida entradaSalida;
    private static ServicioPartidas partidas;
    private static Tumbas tumbas;
    private static CastigosHardcore castigos;
    private static Muertes muertes;
    private static FinPorBajas finPorBajas;
    private static ServicioFila fila;
    private static Estadisticas estadisticas = Estadisticas.SIN_DATOS;
    private static FinDePartida fin;
    private static HistorialDungeons historial;
    private static Reiniciador reiniciador = Reiniciador.SIN_PLANTILLA;
    private static MinecraftServer server;

    private Dungeons() {}

    /** Llamado desde {@link DolfinCreepers#onInitialize()}. */
    public static void init() {
        config =
                new DungeonsConfig(
                        FabricLoader.getInstance().getConfigDir().resolve(DungeonsConfig.ARCHIVO),
                        DolfinCreepers.LOGGER::warn);
        registro = new RegistroDungeons(BUS, config.actual());
        entradaSalida = new EntradaSalida(registro, BUS);
        partidas = new ServicioPartidas(registro, entradaSalida, () -> reiniciador);
        fila = new EntradaDirecta(partidas, registro);
        BloqueTumba.registrar();
        tumbas = new Tumbas(registro, () -> server);
        castigos = new CastigosHardcore(entradaSalida, () -> server);
        muertes = new Muertes(registro, entradaSalida, tumbas, castigos);
        historial =
                new HistorialDungeons(
                        aviso -> DolfinCreepers.LOGGER.warn("event=dungeon_history {}", aviso));
        estadisticas = new EstadisticasRecords(historial);
        fin = new FinDePartida(registro, partidas, historial, () -> server);
        finPorBajas = new FinPorBajas(registro, fin::derrota);
        CargaChunks.registrar();
        ContenidoPlantillas.registrar();
        suscribirModulos();
        registrarEventosFabric();
        DolfinCreepers.LOGGER.info(
                "event=dungeons_ready dungeons={} configGeneration={}",
                config.actual().dungeons().size(),
                config.actual().generacion());
    }

    /** Config cargada; las partidas en curso usan {@code Partida.config()} en su lugar. */
    public static DungeonsConfig config() {
        return config;
    }

    /** Relee la config y la aplica a las partidas nuevas ({@code /dungeon admin reload}, #13). */
    public static DungeonsConfig.Informe recargarConfig() {
        DungeonsConfig.Informe informe = config.recargar();
        registro.aplicarConfig(config.actual());
        DolfinCreepers.LOGGER.info("event=dungeons_config_reloaded {}", informe.resumen());
        return informe;
    }

    /**
     * Añade o reemplaza una dungeon en el archivo de config y aplica la config resultante al
     * registro (las dungeons con partida o reinicio en curso no cambian de estado). Es lo que usan
     * {@code setzone}, {@code setentry}, {@code enable}… (#13).
     */
    public static DungeonsConfig.Informe guardarDungeon(DefinicionDungeon definicion)
            throws IOException {
        DungeonsConfig.Informe informe = config.guardarDungeon(definicion);
        registro.aplicarConfig(config.actual());
        DolfinCreepers.LOGGER.info(
                "event=dungeons_config_saved dungeon={} {}", definicion.id(), informe.resumen());
        return informe;
    }

    /** Bus de eventos internos para suscribirse a {@code PartidaIniciada}, {@code JugadorMurio}… */
    public static BusEventos eventos() {
        return BUS;
    }

    /** Estado de dungeons, partidas y ocupaciones. */
    public static RegistroDungeons registro() {
        return registro;
    }

    /** Empezar, abandonar, terminar y reiniciar partidas. */
    public static ServicioPartidas partidas() {
        return partidas;
    }

    /** Entrada y salida segura de jugadores. */
    public static EntradaSalida entradaSalida() {
        return entradaSalida;
    }

    /** Tumbas de dificultad media (#16). */
    public static Tumbas tumbas() {
        return tumbas;
    }

    /** Cómo entran los jugadores con {@code /dungeon join}: directo, o grupos y fila (#14). */
    public static ServicioFila fila() {
        return fila;
    }

    /** Sustituye la entrada de {@code /dungeon join}; #14 instala aquí grupos y fila. */
    public static void usarFila(ServicioFila nueva) {
        fila = Objects.requireNonNull(nueva);
    }

    /** Tiempos de partida para {@code /dungeon info} y {@code top}. */
    public static Estadisticas estadisticas() {
        return estadisticas;
    }

    /** Sustituye la fuente de tiempos (por defecto, los récords de #22). */
    public static void usarEstadisticas(Estadisticas nuevas) {
        estadisticas = Objects.requireNonNull(nuevas);
    }

    /**
     * Fin de partida (#17): bossbar, límite de tiempo, AFK y victoria ({@code declararVictoria}).
     */
    public static FinDePartida fin() {
        return fin;
    }

    /**
     * Récords (#22) y progreso por jugador: cooldowns y dungeons completadas, que lee la validación
     * de entrada (#14). Disponible tras arrancar el servidor.
     */
    public static HistorialDungeons historial() {
        return historial;
    }

    /** Sustituye el reiniciador de zonas; #19 instala aquí el de plantillas. */
    public static void usarReiniciador(Reiniciador nuevo) {
        reiniciador = Objects.requireNonNull(nuevo);
    }

    private static void suscribirModulos() {
        new CargaChunks(() -> server).suscribir(BUS);
        new AvisosPartida(() -> server).suscribir(BUS);
        tumbas.suscribir(BUS);
        castigos.suscribir(BUS);
        finPorBajas.suscribir(BUS);
        fin.suscribir(BUS);
        BUS.suscribir(
                DungeonCambioEstado.class,
                e -> {
                    if (server != null) {
                        DatosDungeons.de(server).registrar(e);
                    }
                });
        BUS.suscribir(
                EventoDungeon.class,
                e -> DolfinCreepers.LOGGER.debug("event=dungeon_event detail={}", e));
    }

    private static void registrarEventosFabric() {
        ServerLifecycleEvents.SERVER_STARTING.register(
                s -> {
                    server = s;
                    registro.limpiar(config.actual());
                    historial.cargar(s.getWorldPath(LevelResource.ROOT));
                    entradaSalida.alArrancar();
                });
        ServerLifecycleEvents.SERVER_STARTED.register(
                s -> {
                    tumbas.recuperarTrasArranque(s);
                    partidas.recuperarTrasArranque(s);
                });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> entradaSalida.alDetenerse());
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, s) -> {
                    entradaSalida.alConectarse(handler.getPlayer());
                    castigos.alConectarse(handler.getPlayer());
                    tumbas.alConectarse(handler.getPlayer());
                    fin.alConectarse(handler.getPlayer());
                });
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, s) -> entradaSalida.alDesconectarse(handler.getPlayer()));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
                (jugador, origen, destino) -> {
                    if (DimensionDungeons.es(origen)) {
                        entradaSalida.alSalirDeLaDimension(jugador);
                    }
                });
        ServerPlayerEvents.AFTER_RESPAWN.register(
                (anterior, nuevo, vivo) -> entradaSalida.alSalirDeLaDimension(nuevo));
        ServerLivingEntityEvents.ALLOW_DEATH.register(muertes::permitirMuerte);
        JefeDungeon.registrar(fin);
        ServerTickEvents.END_SERVER_TICK.register(Dungeons::tick);
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, acceso, entorno) -> ComandoDungeon.registrar(dispatcher));
        ComandoDungeon.registrarEventos();
        ReglasDungeon.registrar();
    }

    /**
     * Cada tick: expulsiones de hardcore, checkpoints y espectadores, y derrota por bajas (antes de
     * que {@code partidas.tick} dé por abandonada una partida vacía). Una vez por segundo: plazos
     * de reconexión, fin de partida (tiempo, AFK, cierres), partidas vacías y modo aventura.
     */
    private static void tick(MinecraftServer s) {
        castigos.tick(s);
        muertes.tick(s);
        finPorBajas.tick(s);
        if (s.getTickCount() % RegistroDungeons.TICKS_POR_SEGUNDO != 0) {
            return;
        }
        registro.vencerDesconexiones(s.getTickCount());
        fin.tick(s);
        partidas.tick(s);
        ReglasDungeon.cadaSegundo(s);
    }
}
