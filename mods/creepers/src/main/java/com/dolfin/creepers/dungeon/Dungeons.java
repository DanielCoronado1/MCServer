package com.dolfin.creepers.dungeon;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.config.DungeonsConfig;
import com.dolfin.creepers.dungeon.entrada.DatosDungeons;
import com.dolfin.creepers.dungeon.entrada.EntradaSalida;
import com.dolfin.creepers.dungeon.eventos.BusEventos;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.DungeonCambioEstado;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;
import com.dolfin.creepers.dungeon.muerte.BloqueTumba;
import com.dolfin.creepers.dungeon.muerte.Tumbas;
import com.dolfin.creepers.dungeon.mundo.CargaChunks;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;
import com.dolfin.creepers.dungeon.mundo.Reiniciador;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

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
        BloqueTumba.registrar();
        tumbas = new Tumbas(registro, () -> server);
        CargaChunks.registrar();
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

    /** Sustituye el reiniciador de zonas; #19 instala aquí el de plantillas. */
    public static void usarReiniciador(Reiniciador nuevo) {
        reiniciador = Objects.requireNonNull(nuevo);
    }

    private static void suscribirModulos() {
        new CargaChunks(() -> server).suscribir(BUS);
        new AvisosPartida(() -> server).suscribir(BUS);
        tumbas.suscribir(BUS);
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
                    tumbas.alConectarse(handler.getPlayer());
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
        ServerTickEvents.END_SERVER_TICK.register(Dungeons::tick);
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, acceso, entorno) -> ComandoDebug.registrar(dispatcher));
    }

    /** Una vez por segundo: plazos de reconexión y partidas vacías. */
    private static void tick(MinecraftServer s) {
        if (s.getTickCount() % RegistroDungeons.TICKS_POR_SEGUNDO != 0) {
            return;
        }
        registro.vencerDesconexiones(s.getTickCount());
        partidas.tick(s);
    }
}
