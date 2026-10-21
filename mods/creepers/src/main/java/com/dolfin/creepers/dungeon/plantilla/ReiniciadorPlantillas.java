package com.dolfin.creepers.dungeon.plantilla;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;
import com.dolfin.creepers.dungeon.mundo.Reiniciador;
import com.dolfin.creepers.dungeon.plantilla.EjecutorPorTicks.Paso;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link Reiniciador} que devuelve la zona a su plantilla repartiendo el trabajo entre ticks con el
 * {@link EjecutorPorTicks}:
 *
 * <ol>
 *   <li>carga los chunks de la zona con sus entidades (sin simularlos);
 *   <li>borra las entidades que no son jugadores (mobs, ítems, flechas…);
 *   <li>coloca cada trozo por capas que caben en el presupuesto del tick: vacía la capa y pone los
 *       bloques de la plantilla, que no guarda el aire;
 *   <li>vuelve a borrar entidades (lo que soltaran los bloques sustituidos) y pone las entidades
 *       marcadas de la plantilla;
 *   <li>suelta los chunks y llama a {@code alTerminar} (la dungeon queda {@code LIBRE}).
 * </ol>
 *
 * Sin plantilla, con una que no encaja en la zona o tras un guardado interrumpido por una caída,
 * solo borra las entidades: nunca pisa los bloques con algo dudoso. Los mobs de los marcadores no
 * aparecen aquí sino al empezar la partida ({@link AparicionMarcadores}).
 */
public final class ReiniciadorPlantillas implements Reiniciador {
    /**
     * Banderas de colocación: avisa a los clientes, sin recalcular formas vecinas (la plantilla ya
     * las trae coherentes), sin soltar objetos y sin vaciar al suelo los cofres sustituidos.
     */
    static final int BANDERAS =
            Block.UPDATE_CLIENTS
                    | Block.UPDATE_KNOWN_SHAPE
                    | Block.UPDATE_SUPPRESS_DROPS
                    | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;

    /** Coste fijo de pasos sin bloques (limpiar, poner entidades). */
    private static final int COSTE_ENTIDADES = 2048;

    private final EjecutorPorTicks ejecutor;
    private final AlmacenPlantillas almacen;
    private final int presupuesto;

    /**
     * @param presupuesto bloques por capa; el mismo que el del ejecutor
     */
    public ReiniciadorPlantillas(
            EjecutorPorTicks ejecutor, AlmacenPlantillas almacen, int presupuesto) {
        this.ejecutor = ejecutor;
        this.almacen = almacen;
        this.presupuesto = presupuesto;
    }

    @Override
    public void reiniciar(MinecraftServer server, DefinicionDungeon dungeon, Runnable alTerminar) {
        String id = dungeon.id();
        Optional<ServerLevel> nivel = DimensionDungeons.nivel(server);
        if (nivel.isEmpty()) {
            DolfinCreepers.LOGGER.error(
                    "event=dungeon_reset_failed dungeon={} reason=no_dimension", id);
            alTerminar.run();
            return;
        }
        if (ejecutor.ocupada(id)) {
            DolfinCreepers.LOGGER.warn("event=dungeon_reset_skipped dungeon={} reason=busy", id);
            return;
        }
        Caja zona = Caja.de(dungeon.zona());
        ZonaCargada carga = new ZonaCargada(nivel.get(), zona);
        List<Paso> pasos = planificar(server, nivel.get(), id, zona, carga);
        long inicio = System.nanoTime();
        carga.cargar();
        ejecutor.encolar(
                id,
                pasos,
                error -> {
                    carga.soltar();
                    registrarFin(id, pasos.size(), inicio, error);
                    alTerminar.run();
                });
    }

    /** Lista de pasos del reinicio; la colocación solo si la plantilla es fiable. */
    private List<Paso> planificar(
            MinecraftServer server, ServerLevel nivel, String id, Caja zona, ZonaCargada carga) {
        List<Paso> pasos = new ArrayList<>();
        pasos.add(Paso.esperar(() -> carga.lista(id)));
        pasos.add(Paso.de(COSTE_ENTIDADES, () -> EntidadesZona.limpiar(nivel, zona)));
        Optional<ManifiestoPlantilla> manifiesto = plantillaFiable(id, zona);
        if (manifiesto.isEmpty()) {
            return pasos;
        }
        CacheTrozo cache = new CacheTrozo(server, nivel, id);
        for (Troceado.Trozo trozo : manifiesto.get().trozos()) {
            Caja absoluto = trozo.caja().desplazada(zona.minX(), zona.minY(), zona.minZ());
            for (Caja capa : Troceado.capas(absoluto, presupuesto)) {
                pasos.add(
                        Paso.de(
                                (int) capa.volumen(),
                                () -> colocar(nivel, cache.de(trozo), absoluto, capa)));
            }
        }
        pasos.add(Paso.de(COSTE_ENTIDADES, () -> EntidadesZona.limpiar(nivel, zona)));
        pasos.add(
                Paso.de(
                        COSTE_ENTIDADES,
                        () ->
                                EntidadesZona.aparecer(
                                        nivel,
                                        zona,
                                        almacen.leerEntidades(id, server.getFixerUpper()))));
        return pasos;
    }

    /**
     * Manifiesto si hay plantilla, encaja en la zona y no quedó un guardado a medias; si no, avisa
     * en el log y devuelve vacío.
     */
    private Optional<ManifiestoPlantilla> plantillaFiable(String id, Caja zona) {
        try {
            if (almacen.guardadoInterrumpido(id)) {
                almacen.descartarInterrumpido(id);
                return omitir(id, "interrupted_save");
            }
            Optional<ManifiestoPlantilla> manifiesto = almacen.manifiesto(id);
            if (manifiesto.isEmpty()) {
                return omitir(id, "no_template");
            }
            if (!manifiesto.get().encajaEn(zona)) {
                return omitir(id, "size_mismatch template=" + manifiesto.get().zona());
            }
            return manifiesto;
        } catch (RuntimeException e) {
            DolfinCreepers.LOGGER.error(
                    "event=dungeon_reset_blocks_skipped dungeon={} reason=bad_template", id, e);
            return Optional.empty();
        }
    }

    private static Optional<ManifiestoPlantilla> omitir(String id, String motivo) {
        DolfinCreepers.LOGGER.warn(
                "event=dungeon_reset_blocks_skipped dungeon={} reason={}", id, motivo);
        return Optional.empty();
    }

    /**
     * Deja {@code capa} como en la plantilla: la vacía (la plantilla no guarda el aire) y coloca la
     * parte del trozo que cae dentro.
     */
    private static void colocar(
            ServerLevel nivel, AlmacenPlantillas.TrozoLeido leido, Caja trozo, Caja capa) {
        vaciar(nivel, leido.ocupacion(), trozo, capa);
        BlockPos origen = new BlockPos(trozo.minX(), trozo.minY(), trozo.minZ());
        StructurePlaceSettings ajustes =
                new StructurePlaceSettings()
                        .setIgnoreEntities(true)
                        .setKnownShape(true)
                        .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING)
                        .setBoundingBox(
                                new BoundingBox(
                                        capa.minX(),
                                        capa.minY(),
                                        capa.minZ(),
                                        capa.maxX(),
                                        capa.maxY(),
                                        capa.maxZ()));
        leido.plantilla()
                .placeInWorld(nivel, origen, origen, ajustes, RandomSource.create(), BANDERAS);
    }

    /**
     * Pone aire donde la plantilla no tiene bloque y el mundo sí; lo que la plantilla pone se
     * sustituye al colocar. Los cofres quitados no sueltan su contenido.
     */
    private static void vaciar(ServerLevel nivel, MapaOcupacion ocupacion, Caja trozo, Caja capa) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState aire = Blocks.AIR.defaultBlockState();
        for (int y = capa.minY(); y <= capa.maxY(); y++) {
            for (int x = capa.minX(); x <= capa.maxX(); x++) {
                for (int z = capa.minZ(); z <= capa.maxZ(); z++) {
                    if (ocupacion.ocupado(x - trozo.minX(), y - trozo.minY(), z - trozo.minZ())) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (!nivel.getBlockState(pos).isAir()) {
                        nivel.setBlock(pos, aire, BANDERAS);
                    }
                }
            }
        }
    }

    private static void registrarFin(
            String id, int pasos, long inicio, Optional<RuntimeException> error) {
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        if (error.isPresent()) {
            DolfinCreepers.LOGGER.error(
                    "event=dungeon_reset_failed dungeon={} steps={} ms={}",
                    id,
                    pasos,
                    ms,
                    error.get());
        } else {
            DolfinCreepers.LOGGER.info(
                    "event=dungeon_reset_done dungeon={} steps={} ms={}", id, pasos, ms);
        }
    }

    /** Guarda en memoria solo el trozo que se está colocando. */
    private final class CacheTrozo {
        private final MinecraftServer server;
        private final ServerLevel nivel;
        private final String id;
        private Troceado.Trozo actual;
        private AlmacenPlantillas.TrozoLeido leido;

        private CacheTrozo(MinecraftServer server, ServerLevel nivel, String id) {
            this.server = server;
            this.nivel = nivel;
            this.id = id;
        }

        AlmacenPlantillas.TrozoLeido de(Troceado.Trozo trozo) {
            if (!trozo.equals(actual)) {
                leido =
                        almacen.leerTrozo(
                                id,
                                trozo,
                                nivel.registryAccess().lookupOrThrow(Registries.BLOCK),
                                server.getFixerUpper());
                actual = trozo;
            }
            return leido;
        }
    }
}
