package com.dolfin.creepers.dungeon.plantilla;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.eventos.BusEventos;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.PartidaIniciada;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;
import com.dolfin.creepers.dungeon.plantilla.EjecutorPorTicks.Paso;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Convierte los marcadores de mobs de la plantilla en mobs al empezar cada partida. Los marcadores
 * salen del manifiesto (la zona acaba de reiniciarse, así que coinciden con el mundo) y cada uno
 * pasa por el {@link AjusteAparicion} instalado, que es donde #18 aplica la dificultad. Las
 * apariciones esperan a que carguen los chunks de la partida y se reparten entre ticks; si la
 * partida termina antes, no aparece nada más.
 */
public final class AparicionMarcadores {
    /** Etiqueta de los mobs nacidos de un marcador, para contarlos o filtrarlos (#17, #18). */
    public static final String ETIQUETA_MOB = "dolfin_mob_dungeon";

    /** Coste por entidad en el presupuesto del tick. */
    private static final int COSTE_POR_ENTIDAD = 256;

    private final Supplier<MinecraftServer> server;
    private final EjecutorPorTicks ejecutor;
    private final AlmacenPlantillas almacen;
    private final Supplier<AjusteAparicion> ajuste;

    public AparicionMarcadores(
            Supplier<MinecraftServer> server,
            EjecutorPorTicks ejecutor,
            AlmacenPlantillas almacen,
            Supplier<AjusteAparicion> ajuste) {
        this.server = server;
        this.ejecutor = ejecutor;
        this.almacen = almacen;
        this.ajuste = ajuste;
    }

    public void suscribir(BusEventos bus) {
        bus.suscribir(PartidaIniciada.class, e -> programar(e.partida()));
    }

    private void programar(Partida partida) {
        MinecraftServer s = server.get();
        Optional<ServerLevel> nivel = s == null ? Optional.empty() : DimensionDungeons.nivel(s);
        String id = partida.dungeon().id();
        Caja zona = Caja.de(partida.dungeon().zona());
        List<DatosMarcador> marcadores = marcadores(id, zona);
        if (nivel.isEmpty() || marcadores.isEmpty()) {
            return;
        }
        ZonaCargada carga = new ZonaCargada(nivel.get(), zona);
        List<Paso> pasos = new ArrayList<>();
        pasos.add(Paso.esperar(() -> carga.lista(id)));
        for (DatosMarcador marcador : marcadores) {
            pasos.add(
                    Paso.de(
                            COSTE_POR_ENTIDAD * marcador.cantidad(),
                            () -> aparecer(nivel.get(), partida, zona, marcador)));
        }
        boolean encolada =
                ejecutor.encolar(
                        "aparicion:" + id,
                        pasos,
                        error ->
                                error.ifPresent(
                                        e ->
                                                DolfinCreepers.LOGGER.error(
                                                        "event=dungeon_markers_failed dungeon={}",
                                                        id,
                                                        e)));
        DolfinCreepers.LOGGER.info(
                "event=dungeon_markers_scheduled dungeon={} markers={} queued={}",
                id,
                marcadores.size(),
                encolada);
    }

    /** Marcadores de la plantilla; vacío (con aviso) si no hay o no encaja. */
    private List<DatosMarcador> marcadores(String id, Caja zona) {
        try {
            return almacen.manifiesto(id)
                    .filter(m -> m.encajaEn(zona))
                    .map(ManifiestoPlantilla::marcadores)
                    .orElseGet(
                            () -> {
                                DolfinCreepers.LOGGER.warn(
                                        "event=dungeon_markers_skipped dungeon={}"
                                                + " reason=no_matching_template",
                                        id);
                                return List.of();
                            });
        } catch (RuntimeException e) {
            DolfinCreepers.LOGGER.error(
                    "event=dungeon_markers_skipped dungeon={} reason=bad_template", id, e);
            return List.of();
        }
    }

    private void aparecer(ServerLevel nivel, Partida partida, Caja zona, DatosMarcador marcador) {
        if (!partida.activa()) {
            return;
        }
        BlockPos pos =
                new BlockPos(
                        zona.minX() + marcador.x(),
                        zona.minY() + marcador.y(),
                        zona.minZ() + marcador.z());
        int cantidad = ajuste.get().cantidad(partida, marcador);
        for (int i = 0; i < cantidad; i++) {
            crear(nivel, pos, marcador)
                    .ifPresent(
                            entidad -> {
                                ajuste.get().ajustar(partida, marcador, entidad);
                                nivel.tryAddFreshEntityWithPassengers(entidad);
                            });
        }
    }

    /**
     * Crea la entidad del marcador en el centro del bloque, como {@code /summon}: con NBT propio no
     * se llama a {@code finalizeSpawn}. Los mobs no desaparecen por distancia.
     */
    private static Optional<Entity> crear(ServerLevel nivel, BlockPos pos, DatosMarcador marcador) {
        CompoundTag nbt;
        try {
            nbt =
                    marcador.nbt().isEmpty()
                            ? new CompoundTag()
                            : TagParser.parseCompoundFully(marcador.nbt());
        } catch (CommandSyntaxException e) {
            DolfinCreepers.LOGGER.warn(
                    "event=dungeon_marker_invalid pos={} reason=bad_nbt detail={}",
                    pos.toShortString(),
                    e.getMessage());
            return Optional.empty();
        }
        boolean nbtPropio = !nbt.isEmpty();
        nbt.putString("id", marcador.entidad());
        Entity entidad;
        try (var problemas = new ProblemReporter.ScopedCollector(DolfinCreepers.LOGGER)) {
            entidad =
                    EntityType.loadEntityRecursive(
                            TagValueInput.create(problemas, nivel.registryAccess(), nbt),
                            nivel,
                            EntitySpawnReason.SPAWNER,
                            e -> {
                                e.snapTo(
                                        pos.getX() + 0.5,
                                        pos.getY(),
                                        pos.getZ() + 0.5,
                                        nivel.getRandom().nextFloat() * 360f,
                                        0f);
                                return e;
                            });
        }
        if (entidad == null) {
            DolfinCreepers.LOGGER.warn(
                    "event=dungeon_marker_invalid pos={} reason=unknown_entity entity={}",
                    pos.toShortString(),
                    marcador.entidad());
            return Optional.empty();
        }
        entidad.addTag(ETIQUETA_MOB);
        if (entidad instanceof Mob mob) {
            if (!nbtPropio) {
                mob.finalizeSpawn(
                        nivel, nivel.getCurrentDifficultyAt(pos), EntitySpawnReason.SPAWNER, null);
            }
            mob.setPersistenceRequired();
        }
        return Optional.of(entidad);
    }
}
