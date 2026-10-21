package com.dolfin.creepers.dungeon.plantilla;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.ServicioPartidas;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.EstadoDungeon;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;
import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Fachada de plantillas para los comandos de admin (#13): {@code /dungeon admin save <id>} ({@link
 * #guardarPlantilla}) y {@code /dungeon admin reset <id>} ({@link #reiniciar}). También avanza el
 * {@link EjecutorPorTicks} compartido cada tick.
 *
 * <p>Mientras se guarda, la dungeon está en {@code REINICIANDO}: no empieza ninguna partida que
 * cambie la zona a mitad de captura. Al terminar vuelve a {@code LIBRE} o {@code DESHABILITADA}.
 */
public final class ServicioPlantillas {
    /**
     * Bloques por tick entre todas las tareas. Colocar 16 384 bloques de una plantilla cuesta unos
     * pocos milisegundos; ajustar si spark muestra TPS &lt; 18 durante un reinicio.
     */
    public static final int PRESUPUESTO_BLOQUES_POR_TICK = 16_384;

    /** Respuesta inmediata a una petición; el resultado del guardado llega después. */
    public enum Respuesta {
        INICIADO,
        DUNGEON_DESCONOCIDA,
        /** Tiene un reinicio o guardado en curso (o una partida, para guardar). */
        OCUPADA,
        SIN_DIMENSION,
        /** No se pudo preparar la carpeta de la plantilla; ver el log. */
        ERROR_ARCHIVOS
    }

    /**
     * Resultado de un guardado terminado.
     *
     * @param error vacío si se guardó; si no, el motivo (la plantilla anterior sigue intacta)
     */
    public record InformeGuardado(
            String dungeon,
            int trozos,
            int marcadores,
            int entidades,
            long milisegundos,
            Optional<String> error) {}

    private final RegistroDungeons registro;
    private final ServicioPartidas partidas;
    private final EjecutorPorTicks ejecutor;
    private final AlmacenPlantillas almacen;
    private final Set<String> guardando = new HashSet<>();
    private long picoNanos;
    private long totalNanos;
    private int ticks;

    public ServicioPlantillas(
            RegistroDungeons registro,
            ServicioPartidas partidas,
            EjecutorPorTicks ejecutor,
            AlmacenPlantillas almacen) {
        this.registro = registro;
        this.partidas = partidas;
        this.ejecutor = ejecutor;
        this.almacen = almacen;
    }

    /**
     * Guarda la zona de la dungeon como su plantilla: bloques, contenido de bloques, marcadores de
     * mobs y entidades con la etiqueta {@link EntidadesZona#ETIQUETA}. Solo con la dungeon {@code
     * LIBRE} o {@code DESHABILITADA}. {@code alTerminar} recibe el informe en el hilo del servidor.
     */
    public Respuesta guardarPlantilla(
            MinecraftServer server, String dungeonId, Consumer<InformeGuardado> alTerminar) {
        Optional<DefinicionDungeon> definicion = registro.config().dungeon(dungeonId);
        if (definicion.isEmpty()) {
            return Respuesta.DUNGEON_DESCONOCIDA;
        }
        Optional<ServerLevel> nivel = DimensionDungeons.nivel(server);
        if (nivel.isEmpty()) {
            return Respuesta.SIN_DIMENSION;
        }
        if (!enReposo(dungeonId) || ejecutor.ocupada(dungeonId)) {
            return Respuesta.OCUPADA;
        }
        AlmacenPlantillas.Escritura escritura;
        try {
            escritura = almacen.empezarEscritura(dungeonId);
        } catch (UncheckedIOException e) {
            DolfinCreepers.LOGGER.error(
                    "event=dungeon_template_save_failed dungeon={}", dungeonId, e);
            return Respuesta.ERROR_ARCHIVOS;
        }
        registro.marcarReiniciando(dungeonId);
        guardando.add(dungeonId);
        encolarGuardado(nivel.get(), definicion.get(), escritura, alTerminar);
        DolfinCreepers.LOGGER.info("event=dungeon_template_save_started dungeon={}", dungeonId);
        return Respuesta.INICIADO;
    }

    /** Encola los pasos del guardado; al acabar devuelve la dungeon al reposo. */
    private void encolarGuardado(
            ServerLevel nivel,
            DefinicionDungeon definicion,
            AlmacenPlantillas.Escritura escritura,
            Consumer<InformeGuardado> alTerminar) {
        String dungeonId = definicion.id();
        Caja zona = Caja.de(definicion.zona());
        ZonaCargada carga = new ZonaCargada(nivel, zona);
        GuardadoPlantilla guardado = new GuardadoPlantilla(nivel, dungeonId, zona, escritura);
        long inicio = System.nanoTime();
        carga.cargar();
        ejecutor.encolar(
                dungeonId,
                guardado.pasos(carga),
                error -> {
                    carga.soltar();
                    guardando.remove(dungeonId);
                    registro.finalizarReinicio(dungeonId);
                    error.ifPresent(e -> descartar(escritura, dungeonId));
                    alTerminar.accept(informe(dungeonId, guardado, inicio, error));
                });
    }

    /**
     * Reinicio de admin: si hay partida, la cancela (saca a los jugadores sin ban ni pérdida,
     * {@code CANCELADA}) y eso lanza el reinicio; si no, reinicia la zona directamente.
     */
    public Respuesta reiniciar(MinecraftServer server, String dungeonId) {
        if (registro.config().dungeon(dungeonId).isEmpty()) {
            return Respuesta.DUNGEON_DESCONOCIDA;
        }
        if (registro.estado(dungeonId).orElse(null) == EstadoDungeon.REINICIANDO
                || ejecutor.ocupada(dungeonId)) {
            return Respuesta.OCUPADA;
        }
        boolean lanzado =
                registro.partida(dungeonId).isPresent()
                        ? partidas.terminar(server, dungeonId, ResultadoPartida.CANCELADA)
                                .isPresent()
                        : partidas.reiniciar(server, dungeonId);
        return lanzado ? Respuesta.INICIADO : Respuesta.OCUPADA;
    }

    /**
     * Avanza las tareas de plantillas; se llama al final de cada tick del servidor. Al quedar sin
     * trabajo registra el tick más caro, para ajustar {@link #PRESUPUESTO_BLOQUES_POR_TICK}.
     */
    public void tick() {
        if (ejecutor.vacio()) {
            return;
        }
        long inicio = System.nanoTime();
        ejecutor.tick();
        long duracion = System.nanoTime() - inicio;
        picoNanos = Math.max(picoNanos, duracion);
        totalNanos += duracion;
        ticks++;
        if (ejecutor.vacio()) {
            DolfinCreepers.LOGGER.info(
                    "event=dungeon_template_work_idle ticks={} avgTickMs={} peakTickMs={}",
                    ticks,
                    totalNanos / 1_000_000.0 / ticks,
                    picoNanos / 1_000_000.0);
            picoNanos = 0;
            totalNanos = 0;
            ticks = 0;
        }
    }

    /**
     * El servidor se detiene: descarta las tareas. Los reinicios a medias siguen pendientes y se
     * repiten al arrancar; los guardados a medias se descartan y su dungeon vuelve al reposo, así
     * el arranque no pisa la obra sin guardar con la plantilla anterior.
     */
    public void alDetenerse() {
        ejecutor.cancelarTodo();
        for (String dungeonId : Set.copyOf(guardando)) {
            registro.finalizarReinicio(dungeonId);
            try {
                almacen.descartarInterrumpido(dungeonId);
            } catch (UncheckedIOException e) {
                DolfinCreepers.LOGGER.warn(
                        "event=dungeon_template_discard_failed dungeon={}", dungeonId, e);
            }
            DolfinCreepers.LOGGER.warn(
                    "event=dungeon_template_save_cancelled dungeon={} reason=server_stopping",
                    dungeonId);
        }
        guardando.clear();
    }

    private static void descartar(AlmacenPlantillas.Escritura escritura, String dungeonId) {
        try {
            escritura.descartar();
        } catch (UncheckedIOException e) {
            DolfinCreepers.LOGGER.warn(
                    "event=dungeon_template_discard_failed dungeon={}", dungeonId, e);
        }
    }

    private boolean enReposo(String dungeonId) {
        Optional<EstadoDungeon> estado = registro.estado(dungeonId);
        return estado.isPresent()
                && (estado.get() == EstadoDungeon.LIBRE
                        || estado.get() == EstadoDungeon.DESHABILITADA);
    }

    private static InformeGuardado informe(
            String dungeonId,
            GuardadoPlantilla guardado,
            long inicio,
            Optional<RuntimeException> error) {
        InformeGuardado informe =
                new InformeGuardado(
                        dungeonId,
                        guardado.trozos(),
                        guardado.marcadores(),
                        guardado.entidades(),
                        (System.nanoTime() - inicio) / 1_000_000,
                        error.map(e -> String.valueOf(e.getMessage())));
        if (error.isPresent()) {
            DolfinCreepers.LOGGER.error(
                    "event=dungeon_template_save_failed dungeon={}", dungeonId, error.get());
        } else {
            DolfinCreepers.LOGGER.info(
                    "event=dungeon_template_saved dungeon={} chunks={} markers={} entities={}"
                            + " ms={}",
                    dungeonId,
                    informe.trozos(),
                    informe.marcadores(),
                    informe.entidades(),
                    informe.milisegundos());
        }
        return informe;
    }
}
