package com.dolfin.zombies;

import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.nio.file.*;
import java.util.*;

/**
 * Opt-in inclusive entity-tick wall-time probe. Disabled costs one constant branch per dispatch.
 * Not a spark replacement.
 */
public final class ZombieTickProbe {
    private ZombieTickProbe() {}

    public static final boolean ENABLED = Boolean.getBoolean("dolfinzombies.tickProbe");
    private static final IdentityHashMap<Entity, Long> STARTS = new IdentityHashMap<>();
    private static final Set<Entity> ENTITIES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static long frameStart, entityNanos;
    private static final long[] KIND_NANOS = new long[ZombieKind.values().length];
    private static TickBudgetWindow window = new TickBudgetWindow(100);

    public static void enter(Entity entity) {
        if (ENABLED && entity instanceof ConfiguredZombie) STARTS.put(entity, System.nanoTime());
    }

    public static void exit(Entity entity) {
        if (!ENABLED || !(entity instanceof ConfiguredZombie zombie)) return;
        Long start = STARTS.remove(entity);
        if (start == null) return;
        long elapsed = System.nanoTime() - start;
        entityNanos += elapsed;
        KIND_NANOS[zombie.kind().ordinal()] += elapsed;
        ENTITIES.add(entity);
    }

    public static void init() {
        if (!ENABLED) return;
        DolfinZombies.LOGGER.info(
                "event=zombie_tick_probe_enabled window=100 metric=inclusive_entity_ticks");
        ServerTickEvents.START_SERVER_TICK.register(
                server -> {
                    STARTS.clear();
                    ENTITIES.clear();
                    entityNanos = 0;
                    frameStart = System.nanoTime();
                });
        ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    long elapsed = System.nanoTime() - frameStart;
                    int near = 0;
                    for (Entity entity : ENTITIES)
                        if (entity instanceof ConfiguredZombie zombie
                                && zombie.isAlive()
                                && !zombie.isNoAi()
                                && entity.level() instanceof ServerLevel level
                                && level.getNearestPlayer(
                                                entity.getX(),
                                                entity.getY(),
                                                entity.getZ(),
                                                64,
                                                p ->
                                                        p
                                                                        instanceof
                                                                        net.minecraft.world.entity
                                                                                        .player
                                                                                        .Player
                                                                                player
                                                                && player.isAlive()
                                                                && !player.isSpectator()
                                                                && !player.isCreative())
                                        != null) near++;
                    window.add(entityNanos, elapsed, ENTITIES.size(), near);
                    if (window.full()) {
                        JsonObject out = new JsonObject();
                        out.addProperty("metric", "inclusive_entity_ticks_not_spark_delta");
                        out.addProperty("serverTick", server.getTickCount());
                        out.addProperty("frames", window.size());
                        out.addProperty("meanSpecialMs", window.meanMs());
                        out.addProperty("p95SpecialMs", window.percentileMs(.95));
                        out.addProperty("meanServerMs", window.serverMeanMs());
                        out.addProperty("minEntities", window.minEntities());
                        out.addProperty("minNearPlayer", window.minNear());
                        out.addProperty("entityBudgetPassed", window.withinBudget());
                        JsonObject kinds = new JsonObject();
                        for (var kind : ZombieKind.values())
                            kinds.addProperty(
                                    kind.id(), KIND_NANOS[kind.ordinal()] / 1e6 / window.size());
                        out.add("meanMsByKind", kinds);
                        try {
                            Path path =
                                    DolfinZombies.config()
                                            .root()
                                            .resolve("diagnostics/tick-budget.json");
                            Files.createDirectories(path.getParent());
                            Path temp = path.resolveSibling("tick-budget.tmp");
                            Files.writeString(temp, out.toString());
                            Files.move(
                                    temp,
                                    path,
                                    StandardCopyOption.REPLACE_EXISTING,
                                    StandardCopyOption.ATOMIC_MOVE);
                        } catch (Exception error) {
                            DolfinZombies.LOGGER.warn(
                                    "event=zombie_tick_probe_write_failed", error);
                        }
                        DolfinZombies.LOGGER.info(
                                "event=zombie_tick_budget meanMs={} p95Ms={} minEntities={}"
                                        + " minNear={} passed={}",
                                window.meanMs(),
                                window.percentileMs(.95),
                                window.minEntities(),
                                window.minNear(),
                                window.withinBudget());
                        window = new TickBudgetWindow(100);
                        Arrays.fill(KIND_NANOS, 0);
                    }
                });
        ServerLifecycleEvents.SERVER_STOPPED.register(
                server -> {
                    STARTS.clear();
                    ENTITIES.clear();
                    window = new TickBudgetWindow(100);
                    Arrays.fill(KIND_NANOS, 0);
                });
    }
}
