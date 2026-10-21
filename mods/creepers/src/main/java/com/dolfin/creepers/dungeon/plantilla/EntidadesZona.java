package com.dolfin.creepers.dungeon.plantilla;

import com.dolfin.creepers.DolfinCreepers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Entidades de una zona: borrar todo lo que no es jugador, capturar las marcadas para la plantilla
 * y volver a ponerlas. Las posiciones se guardan relativas a la esquina mínima de la zona.
 *
 * <p>Las entidades colgadas (cuadros, marcos) guardan además su bloque de apoyo en absoluto: si se
 * mueve la zona, hay que volver a guardar la plantilla.
 */
public final class EntidadesZona {
    /**
     * Etiqueta ({@code /tag <entidad> add dolfin_plantilla}) que mete una entidad en la plantilla:
     * aldeanos, soportes de armadura, marcos, mobs fijos…
     */
    public static final String ETIQUETA = "dolfin_plantilla";

    private EntidadesZona() {}

    /**
     * Borra las entidades de la zona salvo jugadores (mobs, ítems, flechas, orbes, vehículos…).
     * Devuelve cuántas borró.
     */
    public static int limpiar(ServerLevel nivel, Caja zona) {
        List<Entity> entidades = nivel.getEntities((Entity) null, caja(zona), e -> !esJugador(e));
        for (Entity entidad : entidades) {
            entidad.discard();
        }
        return entidades.size();
    }

    /** NBT de las entidades marcadas con {@link #ETIQUETA}, con sus pasajeros y sin UUID. */
    public static List<CompoundTag> capturarMarcadas(ServerLevel nivel, Caja zona) {
        List<CompoundTag> capturadas = new ArrayList<>();
        List<Entity> marcadas =
                nivel.getEntities(
                        (Entity) null,
                        caja(zona),
                        e -> !e.isPassenger() && e.entityTags().contains(ETIQUETA));
        try (var problemas = new ProblemReporter.ScopedCollector(DolfinCreepers.LOGGER)) {
            for (Entity entidad : marcadas) {
                TagValueOutput salida =
                        TagValueOutput.createWithContext(problemas, nivel.registryAccess());
                if (!entidad.save(salida)) {
                    continue;
                }
                CompoundTag nbt = salida.buildResult();
                quitarUuids(nbt);
                desplazar(nbt, -zona.minX(), -zona.minY(), -zona.minZ());
                capturadas.add(nbt);
            }
        }
        return capturadas;
    }

    /** Pone en la zona las entidades capturadas. Devuelve cuántas aparecieron. */
    public static int aparecer(ServerLevel nivel, Caja zona, List<CompoundTag> guardadas) {
        int puestas = 0;
        try (var problemas = new ProblemReporter.ScopedCollector(DolfinCreepers.LOGGER)) {
            for (CompoundTag guardada : guardadas) {
                CompoundTag nbt = guardada.copy();
                desplazar(nbt, zona.minX(), zona.minY(), zona.minZ());
                Entity entidad =
                        EntityType.loadEntityRecursive(
                                TagValueInput.create(problemas, nivel.registryAccess(), nbt),
                                nivel,
                                EntitySpawnReason.STRUCTURE,
                                e -> e);
                if (entidad != null && nivel.tryAddFreshEntityWithPassengers(entidad)) {
                    puestas++;
                }
            }
        }
        return puestas;
    }

    private static boolean esJugador(Entity entidad) {
        return entidad instanceof Player;
    }

    private static AABB caja(Caja zona) {
        return new AABB(
                zona.minX(),
                zona.minY(),
                zona.minZ(),
                zona.maxX() + 1,
                zona.maxY() + 1,
                zona.maxZ() + 1);
    }

    /** Sin UUID cada reinicio crea entidades nuevas y no choca con las anteriores. */
    private static void quitarUuids(CompoundTag nbt) {
        nbt.remove("UUID");
        for (Tag pasajero : nbt.getListOrEmpty("Passengers")) {
            if (pasajero instanceof CompoundTag c) {
                quitarUuids(c);
            }
        }
    }

    private static void desplazar(CompoundTag nbt, double dx, double dy, double dz) {
        ListTag pos = nbt.getListOrEmpty("Pos");
        if (pos.size() != 3) {
            return;
        }
        ListTag nueva = new ListTag();
        nueva.add(DoubleTag.valueOf(pos.getDoubleOr(0, 0) + dx));
        nueva.add(DoubleTag.valueOf(pos.getDoubleOr(1, 0) + dy));
        nueva.add(DoubleTag.valueOf(pos.getDoubleOr(2, 0) + dz));
        nbt.put("Pos", nueva);
    }
}
