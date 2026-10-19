package com.dolfin.zombies;

import com.google.gson.*;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.player.Player;

import java.nio.file.*;
import java.util.*;

/**
 * Blood moon runtime: drives {@link BloodMoonSchedule} from the overworld clock on the server
 * thread, persists it in overworld SavedData, blocks sleep, warns players and syncs the red sky to
 * clients. Lifecycle logs: {@code event=blood_moon_start|end trigger=... night=...} at INFO.
 */
public final class BloodMoon {
    private BloodMoon() {}

    public enum CommandResult {
        STARTED,
        STOPPED,
        CANCELLED,
        ALREADY_ACTIVE,
        NOT_ACTIVE,
        DISABLED,
        NOT_LOADED
    }

    private static BloodMoonState state;
    private static MinecraftServer server;
    private static long generation = -1;
    private static BloodMoonOptions options;

    /** Last red-sky value delivered per online player; missing = must (re)send. */
    private static final Map<UUID, Boolean> sent = new HashMap<>();

    public static final ThreadLocal<ServerLevel> SPAWNING_LEVEL = new ThreadLocal<>();

    public static BloodMoonOptions options() {
        var s = DolfinZombies.config().current();
        if (s.generation() != generation || options == null) {
            generation = s.generation();
            options = BloodMoonOptions.from(s.general());
            DolfinZombies.LOGGER.info(
                    "event=blood_moon_options generation={} {}", generation, options);
        }
        return options;
    }

    public static boolean active() {
        return state != null && state.schedule.active;
    }

    public static boolean active(ServerLevel level) {
        return active() && SpawnRules.worldAllowed(level);
    }

    public static double multiplier() {
        return active() ? options().multiplier() : 1;
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay()
                .register(BloodMoonPayload.TYPE, BloodMoonPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(
                s -> {
                    server = s;
                    state = s.overworld().getDataStorage().computeIfAbsent(BloodMoonState.TYPE);
                    sent.clear();
                    var b = state.schedule;
                    DolfinZombies.LOGGER.info(
                            "event=blood_moon_state_loaded active={} lastNight={} activeNight={}"
                                    + " scheduledNight={} time={} night={} options=[{}]",
                            b.active,
                            b.lastNight,
                            b.activeNight,
                            b.scheduledNight,
                            clock(),
                            BloodMoonSchedule.night(clock()),
                            options());
                });
        ServerLifecycleEvents.SERVER_STOPPING.register(
                s -> {
                    if (state != null)
                        DolfinZombies.LOGGER.info(
                                "event=blood_moon_state_saved active={} lastNight={} activeNight={}"
                                        + " scheduledNight={} cause=shutdown",
                                state.schedule.active,
                                state.schedule.lastNight,
                                state.schedule.activeNight,
                                state.schedule.scheduledNight);
                    report();
                    sent.clear();
                });
        ServerLifecycleEvents.SERVER_STOPPED.register(
                s -> {
                    state = null;
                    server = null;
                });
        ServerTickEvents.END_SERVER_TICK.register(BloodMoon::tick);
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, s) -> {
                    sent.remove(handler.player.getUUID());
                    if (active(handler.player.level())) {
                        notify(
                                handler.player,
                                "dolfinzombies.bloodmoon.join",
                                "Hay una luna de sangre en curso: no se puede dormir.");
                        DolfinZombies.LOGGER.debug(
                                "event=blood_moon_late_join player={}", handler.player.getUUID());
                    }
                });
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, s) -> sent.remove(handler.player.getUUID()));
        EntitySleepEvents.ALLOW_SLEEPING.register(
                (player, pos) -> {
                    if (state == null || !(player.level() instanceof ServerLevel l)) return null;
                    var o = options();
                    if (!o.enabled()
                            || !o.sleepBlocked()
                            || !SpawnRules.worldAllowed(l)
                            || !state.schedule.sleepBlocked(clock())) return null;
                    boolean pending = state.schedule.pendingTonight(clock());
                    DolfinZombies.LOGGER.debug(
                            "event=blood_moon_sleep_blocked player={} pending={} dimension={}"
                                    + " pos={}",
                            player.getUUID(),
                            pending,
                            l.dimension().identifier(),
                            pos);
                    return new Player.BedSleepingProblem(
                            pending
                                    ? Component.translatableWithFallback(
                                            "dolfinzombies.bloodmoon.sleep_pending",
                                            "Esta noche hay luna de sangre: no puedes dormir.")
                                    : Component.translatableWithFallback(
                                            "dolfinzombies.bloodmoon.sleep_active",
                                            "No puedes dormir durante la luna de sangre."));
                });
    }

    private static long clock() {
        return server.overworld().getOverworldClockTime();
    }

    private static void tick(MinecraftServer s) {
        if (state == null) return;
        var o = options();
        var b = state.schedule;
        var out =
                b.advance(
                        clock(),
                        o.enabled(),
                        o.every(),
                        o.chance(),
                        () -> s.overworld().getRandom().nextDouble());
        if (out.evaluatedNight() > 0) {
            state.setDirty();
            DolfinZombies.LOGGER.info(
                    "event=blood_moon_evaluate night={} enabled={} every={} chance={} roll={}"
                            + " selected={} trigger={} generation={}",
                    out.evaluatedNight(),
                    o.enabled(),
                    o.every(),
                    o.chance(),
                    out.roll(),
                    out.selected(),
                    BloodMoonSchedule.trigger(o.every()),
                    generation);
            if (out.missedNight() > 0)
                DolfinZombies.LOGGER.warn(
                        "event=blood_moon_missed night={} reason=time_skipped_past_night now={}",
                        out.missedNight(),
                        clock());
        }
        switch (out.step()) {
            case START -> started(out.trigger(), out.night());
            case END_DAWN, END_DISABLED -> ended(out.trigger(), out.night());
            case NONE -> {}
        }
        syncSky(s);
        if (s.getTickCount() % 1200 == 0) report();
    }

    /**
     * Command start: moves the overworld clock to dusk when it is day, so spawns and the dawn close
     * behave like a natural night.
     */
    public static CommandResult start(String actor) {
        if (state == null) return CommandResult.NOT_LOADED;
        if (!options().enabled()) return CommandResult.DISABLED;
        if (state.schedule.active) return CommandResult.ALREADY_ACTIVE;
        long time = clock();
        if (!BloodMoonSchedule.isNight(time)) {
            long dusk =
                    Math.floorDiv(time, BloodMoonSchedule.DAY) * BloodMoonSchedule.DAY
                            + BloodMoonSchedule.NIGHT_START;
            server.overworld()
                    .clockManager()
                    .setTotalTicks(
                            server.registryAccess()
                                    .lookupOrThrow(
                                            net.minecraft.core.registries.Registries.WORLD_CLOCK)
                                    .getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD),
                            dusk);
            DolfinZombies.LOGGER.info(
                    "event=blood_moon_force_night actor={} from={} to={}", actor, time, dusk);
            time = dusk;
        }
        state.schedule.forceStart(time);
        DolfinZombies.LOGGER.info("event=blood_moon_command action=start actor={}", actor);
        started("command", state.schedule.activeNight);
        return CommandResult.STARTED;
    }

    public static CommandResult stop(String actor) {
        if (state == null) return CommandResult.NOT_LOADED;
        var b = state.schedule;
        boolean wasActive = b.active;
        long night = wasActive ? b.activeNight : b.scheduledNight;
        if (!b.forceStop()) return CommandResult.NOT_ACTIVE;
        DolfinZombies.LOGGER.info(
                "event=blood_moon_command action=stop actor={} wasActive={} night={}",
                actor,
                wasActive,
                night);
        if (wasActive) {
            ended("command", night);
            return CommandResult.STOPPED;
        }
        save("cancel");
        return CommandResult.CANCELLED;
    }

    private static void save(String cause) {
        state.setDirty();
        server.overworld().getDataStorage().saveAndJoin();
        var b = state.schedule;
        DolfinZombies.LOGGER.info(
                "event=blood_moon_state_saved active={} lastNight={} activeNight={}"
                        + " scheduledNight={} cause={}",
                b.active,
                b.lastNight,
                b.activeNight,
                b.scheduledNight,
                cause);
    }

    private static void started(String trigger, long night) {
        save("start");
        var o = options();
        int players = 0;
        for (var p : server.getPlayerList().getPlayers()) {
            if (!SpawnRules.worldAllowed(p.level())) continue;
            players++;
            if (o.sleepBlocked() && p.isSleeping()) p.stopSleepInBed(true, true);
            notify(
                    p,
                    "dolfinzombies.bloodmoon.start",
                    "¡La luna de sangre ha comenzado! Los zombies son más fuertes y no se puede"
                            + " dormir.");
            if (o.warn()) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                p.connection.send(
                        new ClientboundSetSubtitleTextPacket(
                                Component.translatableWithFallback(
                                        "dolfinzombies.bloodmoon.subtitle",
                                        "Los muertos están inquietos")));
                p.connection.send(
                        new ClientboundSetTitleTextPacket(
                                Component.translatableWithFallback(
                                                "dolfinzombies.bloodmoon.title", "Luna de sangre")
                                        .withStyle(net.minecraft.ChatFormatting.DARK_RED)));
                p.connection.send(
                        new ClientboundSoundPacket(
                                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(
                                        SoundEvents.WITHER_SPAWN),
                                SoundSource.HOSTILE,
                                p.getX(),
                                p.getY(),
                                p.getZ(),
                                1,
                                .6f,
                                p.getRandom().nextLong()));
            }
        }
        DolfinZombies.LOGGER.info(
                "event=blood_moon_start trigger={} night={} players={} warn={} redSky={}"
                        + " multiplier={} onlySpecials={} generation={}",
                trigger,
                night,
                players,
                o.warn(),
                o.redSky(),
                o.multiplier(),
                o.onlySpecials(),
                generation);
        sent.clear();
        report();
    }

    private static void ended(String trigger, long night) {
        save("end");
        int burning = 0, spared = 0;
        if (trigger.equals("dawn"))
            for (var l : server.getAllLevels()) {
                if (!SpawnRules.worldAllowed(l)) continue;
                for (var e : l.getAllEntities())
                    if (e instanceof ConfiguredZombie z && z.isAlive()) {
                        // Non sun-burning kinds (Bombero, Congelado, Explorador...) and
                        // named/persistent zombies are spared.
                        if (z.burnsInSun() && !z.isPersistenceRequired()) {
                            z.igniteForSeconds(BloodMoonOptions.burnSeconds(z.getHealth()));
                            burning++;
                        } else spared++;
                    }
            }
        for (var p : server.getPlayerList().getPlayers())
            if (SpawnRules.worldAllowed(p.level()))
                notify(
                        p,
                        trigger.equals("dawn")
                                ? "dolfinzombies.bloodmoon.end"
                                : "dolfinzombies.bloodmoon.stopped",
                        trigger.equals("dawn")
                                ? "Amanece: la luna de sangre ha terminado."
                                : "La luna de sangre ha sido detenida.");
        DolfinZombies.LOGGER.info(
                "event=blood_moon_end trigger={} night={} burning={} spared={} generation={}",
                trigger,
                night,
                burning,
                spared,
                generation);
        sent.clear();
        report();
    }

    /** Chat when {@code mensajesChat}, otherwise the action bar, so the event is never silent. */
    private static void notify(ServerPlayer p, String key, String fallback) {
        p.sendSystemMessage(
                Component.translatableWithFallback(key, fallback), !options().chatMessages());
    }

    /**
     * Idempotent per player: covers late joiners, dimension changes, reconnects and config toggles
     * of cieloRojo.
     */
    private static void syncSky(MinecraftServer s) {
        boolean redSky = options().redSky();
        for (var p : s.getPlayerList().getPlayers()) {
            boolean red = redSky && active(p.level());
            if (Boolean.valueOf(red).equals(sent.get(p.getUUID()))) continue;
            // Channel registration can arrive a few ticks after join (and never for clients without
            // the mod): retry next tick.
            if (!ServerPlayNetworking.canSend(p, BloodMoonPayload.TYPE)) continue;
            ServerPlayNetworking.send(p, new BloodMoonPayload(red));
            sent.put(p.getUUID(), red);
            DolfinZombies.LOGGER.debug(
                    "event=blood_moon_sky_sync player={} red={} dimension={}",
                    p.getUUID(),
                    red,
                    p.level().dimension().identifier());
        }
    }

    public static String status() {
        var b = state == null ? new BloodMoonSchedule() : state.schedule;
        return "active="
                + b.active
                + " lastNight="
                + b.lastNight
                + " activeNight="
                + b.activeNight
                + " scheduledNight="
                + b.scheduledNight
                + " generation="
                + DolfinZombies.config().current().generation()
                + " multiplier="
                + multiplier();
    }

    public static void report() {
        var b = state == null ? new BloodMoonSchedule() : state.schedule;
        var o = new JsonObject();
        o.addProperty("timestamp", java.time.Instant.now().toString());
        o.addProperty("bloodmoon", status());
        o.addProperty("active", b.active);
        o.addProperty("lastNight", b.lastNight);
        o.addProperty("activeNight", b.activeNight);
        o.addProperty("scheduledNight", b.scheduledNight);
        o.addProperty("generation", DolfinZombies.config().current().generation());
        o.add("spawnCounters", new Gson().toJsonTree(SpawnRules.counters()));
        o.add("recentSpawns", new Gson().toJsonTree(SpawnRules.samples()));
        o.add("bloodmoonOptions", options().raw().deepCopy());
        try {
            Path path = DolfinZombies.config().root().resolve("diagnostics/runtime.json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception e) {
            DolfinZombies.LOGGER.error("event=runtime_report_failed", e);
        }
    }
}
