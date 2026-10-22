package com.dolfin.space.travel;

import com.dolfin.space.*;
import com.dolfin.space.ships.*;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket.RandomizationType;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Persistent, server-authoritative flight sequence. Journal + ship state are saved in the same
 * entity.
 */
public final class FlightController {
    public static java.util.function.BiPredicate<SpaceshipEntity, UUID> riderBlocked =
            (ship, id) -> false;
    public static java.util.function.BiFunction<SpaceshipEntity, ServerLevel, BlockPos>
            demoLanding = (ship, world) -> null;
    private static final Set<String> additionalLaunchWorlds = new HashSet<>();

    public static void allowLaunchWorld(String id) {
        Identifier.parse(id);
        additionalLaunchWorlds.add(id);
    }

    public static boolean launchWorld(Level world) {
        return world.dimension().equals(Level.OVERWORLD)
                || additionalLaunchWorlds.contains(world.dimension().identifier().toString());
    }

    private static ServerLevel homeWorld(SpaceshipEntity ship) {
        return ((ServerLevel) ship.level())
                .getServer()
                .getLevel(
                        ResourceKey.create(
                                Registries.DIMENSION, Identifier.parse(ship.homeDimension)));
    }

    /** Persist a cancellation tombstone before touching entities, including unloaded ships. */
    public static void cancelDemo(MinecraftServer server, UUID shipId) {
        DemoCancellations.get(server).cancel(shipId);
        server.saveEverything(false, true, true);
        var ledger = FlightLedger.get(server);
        var entry = ledger.entries.get(shipId.toString());
        if (entry != null) {
            var world =
                    server.getLevel(
                            ResourceKey.create(
                                    Registries.DIMENSION, Identifier.parse(entry.dimension())));
            if (world != null) {
                world.getChunkAt(BlockPos.of(entry.position()));
                var entity = world.getEntityInAnyDimension(shipId);
                if (entity != null) {
                    entity.ejectPassengers();
                    entity.discard();
                }
            }
            ledger.entries.remove(shipId.toString());
            ledger.setDirty();
        }
    }

    private static boolean cancelled(SpaceshipEntity ship) {
        if (!DemoCancellations.get(((ServerLevel) ship.level()).getServer())
                .contains(ship.getUUID())) return false;
        ship.ejectPassengers();
        ship.discard();
        return true;
    }

    public static FlightJournal journal(SpaceshipEntity ship) {
        return FlightJournal.CODEC.parse(NbtOps.INSTANCE, ship.flight).result().orElse(null);
    }

    private static void save(SpaceshipEntity ship, FlightJournal j) {
        var before = journal(ship);
        ship.flight =
                (CompoundTag) FlightJournal.CODEC.encodeStart(NbtOps.INSTANCE, j).getOrThrow();
        ship.inFlight = true;
        if (before == null || before.phase() != j.phase()) {
            DolfinSpace.LOGGER.info(
                    "event=ship_flight_transition ship={} from={} to={} destination={}",
                    ship.getUUID(),
                    before == null ? "IDLE" : before.phase(),
                    j.phase(),
                    j.destination());
            FlightLedger.get(((ServerLevel) ship.level()).getServer()).track(ship);
            for (var rider : ship.getPassengers())
                if (rider instanceof ServerPlayer p
                        && ServerPlayNetworking.canSend(p, FlightPhasePayload.TYPE))
                    ServerPlayNetworking.send(p, new FlightPhasePayload(j.phase().ordinal()));
        }
    }

    private static void clear(SpaceshipEntity ship, String reason) {
        ship.flight = new CompoundTag();
        ship.inFlight = false;
        ship.setDeltaMovement(Vec3.ZERO);
        FlightLedger.get(((ServerLevel) ship.level()).getServer()).track(ship);
        shake(ship, 0);
        for (var rider : ship.getPassengers())
            if (rider instanceof ServerPlayer p
                    && ServerPlayNetworking.canSend(p, FlightPhasePayload.TYPE))
                ServerPlayNetworking.send(p, new FlightPhasePayload(-1));
        DolfinSpace.LOGGER.info(
                "event=ship_flight_transition ship={} to=IDLE reason={}", ship.getUUID(), reason);
    }

    private static BlockPos spawn(ServerLevel world, ServerPlayer pilot) {
        var config = pilot == null ? null : pilot.getRespawnConfig();
        return config != null && config.respawnData().dimension().equals(world.dimension())
                ? config.respawnData().pos()
                : world.getRespawnData().pos();
    }

    private static BlockPos destination(
            SpaceshipEntity ship, ServerPlayer pilot, ServerLevel world) {
        var demo = demoLanding.apply(ship, world);
        if (demo != null) return demo;
        if (world.dimension().identifier().toString().equals(ship.homeDimension)) {
            if (ship.home != null
                    && PlatformRules.valid(world, ship.home, ship.tier())
                    && SafeLanding.clear(world, ship.home.above())) return ship.home.above();
            DolfinSpace.LOGGER.info(
                    "event=ship_home_fallback ship={} reason=platform_missing_or_blocked",
                    ship.getUUID());
            return SafeLanding.find(world, spawn(world, pilot));
        }
        return SafeLanding.find(
                world, new BlockPos(ship.blockPosition().getX(), 80, ship.blockPosition().getZ()));
    }

    public static boolean launch(SpaceshipEntity ship, ServerPlayer pilot) {
        var check = TravelModule.check(ship, pilot);
        if (ship.inFlight || !check.allowed()) {
            DolfinSpace.LOGGER.info(
                    "event=ship_launch_rejected ship={} pilot={} surface={} fuel={} access={}"
                            + " active={}",
                    ship.getUUID(),
                    pilot.getUUID(),
                    check.surface(),
                    check.fuel(),
                    check.pilot(),
                    check.active());
            return false;
        }
        var world = (ServerLevel) ship.level();
        boolean earth = launchWorld(world);
        var target =
                world.getServer()
                        .getLevel(
                                earth
                                        ? KeplerDimension.LEVEL
                                        : ResourceKey.create(
                                                Registries.DIMENSION,
                                                Identifier.parse(ship.homeDimension)));
        if (target == null) {
            DolfinSpace.LOGGER.warn(
                    "event=ship_launch_rejected ship={} reason=missing_dimension", ship.getUUID());
            return false;
        }
        BlockPos landing;
        try {
            landing = destination(ship, pilot, target);
        } catch (RuntimeException e) {
            DolfinSpace.LOGGER.warn(
                    "event=ship_launch_rejected ship={} reason=landing_unavailable detail={}",
                    ship.getUUID(),
                    e.toString());
            return false;
        }
        if (earth) {
            ship.home = ship.blockPosition().below().immutable();
            ship.homeDimension = world.dimension().identifier().toString();
        }
        var cfg = DolfinSpace.config().current().naves();
        int seconds =
                switch (ship.tier()) {
                    case BASICA -> cfg.segundosCuentaRegresivaBasica();
                    case CARGA -> cfg.segundosCuentaRegresivaCarga();
                    case RAPIDA -> cfg.segundosCuentaRegresivaRapida();
                };
        save(
                ship,
                new FlightJournal(
                        FlightJournal.Phase.COUNTDOWN,
                        seconds * 20,
                        ship.getY(),
                        landing.asLong(),
                        target.dimension().identifier().toString(),
                        ship.getPassengers().stream().map(e -> e.getUUID().toString()).toList()));
        return true;
    }

    public static void dismount(ServerPlayer p) {
        if (!(p.getVehicle() instanceof SpaceshipEntity ship)) return;
        var j = journal(ship);
        if (j == null || FlightJournal.cancelOnDismount(j.phase())) {
            if (j != null) clear(ship, "dismounted");
            p.stopRiding();
            p.closeContainer();
        }
    }

    private static void shake(SpaceshipEntity ship, float intensity) {
        for (var e : ship.getPassengers())
            if (e instanceof ServerPlayer p
                    && ServerPlayNetworking.canSend(p, CameraShakePayload.TYPE))
                ServerPlayNetworking.send(
                        p, new CameraShakePayload(ship.level().getGameTime(), intensity));
    }

    public static void loaded(SpaceshipEntity ship) {
        if (cancelled(ship)) return;
        var j = journal(ship);
        if (j == null) return;
        if (j.phase() == FlightJournal.Phase.COUNTDOWN) {
            clear(ship, "restart_before_liftoff");
            return;
        }
        var ledger = FlightLedger.get(((ServerLevel) ship.level()).getServer());
        var entry = ledger.entries.get(ship.getUUID().toString());
        for (String rider : j.riders())
            if (entry != null
                    && ((ServerLevel) ship.level())
                                    .getServer()
                                    .getPlayerList()
                                    .getPlayer(UUID.fromString(rider))
                            == null) entry = entry.pending(rider);
        if (entry != null) ledger.put(entry);
    }

    public static void safeTick(SpaceshipEntity ship) {
        try {
            tick(ship);
        } catch (RuntimeException e) {
            if (ship.tickCount % 20 == 0)
                DolfinSpace.LOGGER.warn(
                        "event=ship_flight_wait ship={} reason={}", ship.getUUID(), e.toString());
        }
    }

    /** Advances the journal one tick, then publishes phase and readiness to clients. */
    public static void tick(SpaceshipEntity ship) {
        advance(ship);
        if (!ship.isRemoved()) publish(ship);
    }

    /**
     * Mirrors the journal into synced entity data. The launch checklist scans the ascent column, so
     * it is refreshed twice a second and only while a pilot sits in a parked ship.
     */
    private static void publish(SpaceshipEntity ship) {
        var j = journal(ship);
        ship.telemetry(j == null ? -1 : j.phase().ordinal(), j == null ? 0 : j.ticks());
        if (ship.tickCount % 10 != 0) return;
        var pilot = ship.getFirstPassenger() instanceof ServerPlayer p ? p : null;
        int flags =
                pilot == null
                        ? 0
                        : j == null
                                ? TravelModule.check(ship, pilot).flags()
                                : OxygenStatusBridge.status(pilot).equipped()
                                        ? 0
                                        : LaunchRules.Checklist.TANK_WARNING;
        ship.readiness(
                LaunchRules.required(
                        launchWorld(ship.level()), DolfinSpace.config().current().naves()),
                flags);
    }

    private static void advance(SpaceshipEntity ship) {
        if (cancelled(ship)) return;
        var world = (ServerLevel) ship.level();
        var ledger = FlightLedger.get(world.getServer());
        if (ship.tickCount % 20 == 0) ledger.track(ship);
        var j = journal(ship);
        if (j == null) return;
        // Timed tickets expire after arrival; keep flight chunks alive even when all occupants
        // disconnect.
        if (ship.tickCount % 20 == 0)
            world.getChunkSource()
                    .addTicketWithRadius(
                            TicketType.PORTAL,
                            new ChunkPos(
                                    ship.blockPosition().getX() >> 4,
                                    ship.blockPosition().getZ() >> 4),
                            2);
        switch (j.phase()) {
            case COUNTDOWN -> {
                var pilot = ship.getFirstPassenger() instanceof ServerPlayer p ? p : null;
                if (pilot == null
                        || !pilot.getUUID().toString().equals(j.riders().getFirst())
                        || !ship.getPassengers().stream()
                                .map(e -> e.getUUID().toString())
                                .toList()
                                .equals(j.riders())
                        || !TravelModule.check(ship, pilot).allowed()) {
                    clear(ship, "countdown_guard_failed");
                    return;
                }
                // The HUD shows the countdown; beeps rise in pitch over the final seconds.
                if (j.ticks() % 20 == 0)
                    world.playSound(
                            null,
                            ship.blockPosition(),
                            com.dolfin.space.assets.SpaceSounds.BEEP,
                            SoundSource.NEUTRAL,
                            .4f,
                            FlightProfile.beepPitch(j.ticks()));
                if (j.ticks() <= 60 && j.ticks() % 5 == 0)
                    shake(ship, (60 - j.ticks()) / 60f * .45f);
                if (j.action(true) == FlightJournal.Action.LIFTOFF) {
                    int cost =
                            DolfinSpace.config().current().naves().porcentajeCombustiblePorViaje();
                    ship.fuel(LaunchRules.deduct(ship.fuel(), cost));
                    save(ship, j.phase(FlightJournal.Phase.ASCENT, FlightProfile.ASCENT_TICKS));
                    pilot.closeContainer();
                    liftoff(world, ship);
                    DolfinSpace.LOGGER.info(
                            "event=ship_fuel_consumed ship={} cost={} remaining={}",
                            ship.getUUID(),
                            cost,
                            ship.fuel());
                } else save(ship, j.step());
            }
            case ASCENT -> {
                double altitude = DolfinSpace.config().current().naves().alturaAscensoDespegue();
                double y =
                        Math.min(
                                world.getMaxY() - 5,
                                j.startY() + FlightProfile.ascentOffset(altitude, j.ticks()));
                ship.setPos(ship.getX(), y, ship.getZ());
                if (j.ticks() % 4 == 0) {
                    particles(world, ship, ParticleTypes.LARGE_SMOKE);
                    particles(world, ship, ParticleTypes.FLAME);
                    shake(
                            ship,
                            .45f
                                    + .55f
                                            * (FlightProfile.ASCENT_TICKS - j.ticks())
                                            / FlightProfile.ASCENT_TICKS);
                }
                if (j.action(true) == FlightJournal.Action.STARS)
                    save(
                            ship,
                            j.phase(
                                    FlightJournal.Phase.TRANSITION,
                                    FlightProfile.TRANSITION_TICKS));
                else save(ship, j.step());
            }
            case TRANSITION -> {
                if (j.ticks() % 4 == 0) particles(world, ship, ParticleTypes.END_ROD);
                if (j.action(true) == FlightJournal.Action.TRANSFER) transfer(ship, j);
                else save(ship, j.step());
            }
            case DESCENT -> {
                var target = BlockPos.of(j.target());
                double y = target.getY() + FlightProfile.descentOffset(j.ticks());
                ship.setPos(target.getX() + .5, y, target.getZ() + .5);
                if (j.ticks() % 8 == 0) particles(world, ship, ParticleTypes.CLOUD);
                if (j.ticks() % 5 == 0)
                    shake(
                            ship,
                            .15f
                                    + .25f
                                            * FlightProfile.thrust(
                                                    FlightJournal.Phase.DESCENT.ordinal(),
                                                    j.ticks()));
                if (j.action(true) == FlightJournal.Action.LAND) {
                    // Terrain may have changed during descent; re-check and find a nearby safe
                    // surface.
                    if (!SafeLanding.surface(world, target))
                        target =
                                world.dimension().identifier().toString().equals(ship.homeDimension)
                                        ? destination(
                                                ship,
                                                world.getServer()
                                                        .getPlayerList()
                                                        .getPlayer(ship.owner),
                                                world)
                                        : SafeLanding.find(world, target);
                    ship.setPos(target.getX() + .5, target.getY(), target.getZ() + .5);
                    clear(ship, "landed");
                    touchdown(world, ship);
                    DolfinSpace.LOGGER.info(
                            "event=ship_landed ship={} dimension={} pos={} fuel={}",
                            ship.getUUID(),
                            world.dimension().identifier(),
                            target,
                            ship.fuel());
                } else save(ship, j.step());
            }
        }
    }

    /** Engine roar and a smoke ring on the pad at the moment of liftoff. */
    private static void liftoff(ServerLevel world, SpaceshipEntity ship) {
        world.playSound(
                null,
                ship.blockPosition(),
                com.dolfin.space.assets.SpaceSounds.ENGINE,
                SoundSource.NEUTRAL,
                1.2f,
                ship.tier() == ShipTier.RAPIDA ? .9f : .6f);
        world.sendParticles(
                ParticleTypes.LARGE_SMOKE,
                ship.getX(),
                ship.getY() + .2,
                ship.getZ(),
                40,
                1.6,
                .2,
                1.6,
                .08,
                RandomizationType.DEFAULT);
        shake(ship, 1);
    }

    /** Dust burst and a short jolt when the landing legs touch the ground. */
    private static void touchdown(ServerLevel world, SpaceshipEntity ship) {
        world.sendParticles(
                ParticleTypes.CLOUD,
                ship.getX(),
                ship.getY() + .1,
                ship.getZ(),
                24,
                1.4,
                .1,
                1.4,
                .05,
                RandomizationType.DEFAULT);
        world.playSound(
                null, ship.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, .35f, .5f);
        shake(ship, .5f);
    }

    private static void particles(
            ServerLevel world,
            SpaceshipEntity s,
            net.minecraft.core.particles.SimpleParticleType type) {
        world.sendParticles(
                type, s.getX(), s.getY(), s.getZ(), 8, .8, .3, .8, .03, RandomizationType.DEFAULT);
    }

    private static void transfer(SpaceshipEntity ship, FlightJournal j) {
        var server = ((ServerLevel) ship.level()).getServer();
        var target =
                server.getLevel(
                        ResourceKey.create(
                                Registries.DIMENSION, Identifier.parse(j.destination())));
        if (target == null) {
            DolfinSpace.LOGGER.warn(
                    "event=ship_transition_wait ship={} reason=dimension_missing", ship.getUUID());
            return;
        }
        BlockPos landing =
                target.dimension().identifier().toString().equals(ship.homeDimension)
                        ? destination(ship, server.getPlayerList().getPlayer(ship.owner), target)
                        : BlockPos.of(j.target());
        if (!SafeLanding.surface(target, landing)) landing = SafeLanding.find(target, landing);
        var next =
                new FlightJournal(
                        FlightJournal.Phase.DESCENT,
                        FlightProfile.DESCENT_TICKS,
                        j.startY(),
                        landing.asLong(),
                        j.destination(),
                        j.riders());
        save(ship, next);
        var occupants = new ArrayList<ServerPlayer>();
        for (String id : j.riders()) {
            var p = server.getPlayerList().getPlayer(UUID.fromString(id));
            if (p != null && !riderBlocked.test(ship, p.getUUID())) occupants.add(p);
        }
        Vec3 pos =
                new Vec3(
                        landing.getX() + .5,
                        landing.getY() + FlightProfile.DESCENT_DROP,
                        landing.getZ() + .5);
        var arrival = teleportGroup(ship, target, pos, occupants, "ship_travel");
        if (arrival == null) {
            DolfinSpace.LOGGER.error("event=ship_transition_failed ship={}", ship.getUUID());
            save(ship, j);
            return;
        }
        arrival.inFlight = true;
        FlightLedger.get(server).track(arrival);
        shake(arrival, 0);
    }

    private static SpaceshipEntity teleportGroup(
            SpaceshipEntity ship,
            ServerLevel target,
            Vec3 pos,
            List<ServerPlayer> occupants,
            String reason) {
        ship.ejectPassengers();
        Entity moved =
                SpaceTravelAccess.supplyAuthorized(
                        ship,
                        reason,
                        () ->
                                ship.teleport(
                                        new TeleportTransition(
                                                target,
                                                pos,
                                                Vec3.ZERO,
                                                ship.getYRot(),
                                                0,
                                                TeleportTransition.PLACE_PORTAL_TICKET)));
        if (!(moved instanceof SpaceshipEntity arrival)) {
            for (var p : occupants) p.startRiding(ship, true, true);
            return null;
        }
        for (var p : occupants) {
            SpaceTravelAccess.runAuthorized(
                    p,
                    reason,
                    () ->
                            p.teleport(
                                    new TeleportTransition(
                                            target,
                                            pos,
                                            Vec3.ZERO,
                                            p.getYRot(),
                                            0,
                                            TeleportTransition.PLACE_PORTAL_TICKET)));
            p.startRiding(arrival, true, true);
        }
        return arrival;
    }

    public static void disconnected(ServerPlayer player) {
        var server = player.level().getServer();
        var ledger = FlightLedger.get(server);
        for (var entry : List.copyOf(ledger.entries.values())) {
            var entity = player.level().getEntityInAnyDimension(UUID.fromString(entry.ship()));
            if (!(entity instanceof SpaceshipEntity ship)) continue;
            var j = journal(ship);
            if (j == null || !j.riders().contains(player.getUUID().toString())) continue;
            if (FlightJournal.completeOnReconnect(j.phase())) {
                ledger.put(entry.pending(player.getUUID().toString()));
                DolfinSpace.LOGGER.info(
                        "event=ship_disconnect_pending ship={} player={}",
                        ship.getUUID(),
                        player.getUUID());
            } else clear(ship, "disconnect_before_liftoff");
        }
    }

    public static void ownerRespawned(ServerPlayer p) {
        var ledger = FlightLedger.get(p.level().getServer());
        for (var e : List.copyOf(ledger.entries.values()))
            if (e.owner().equals(p.getUUID().toString())
                    && e.dimension().equals(KeplerDimension.ID.toString()))
                ledger.put(e.returning());
    }

    public static void recover(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        var ledger = FlightLedger.get(server);
        for (var entry : List.copyOf(ledger.entries.values())) {
            if (DemoCancellations.get(server).contains(UUID.fromString(entry.ship()))) {
                ledger.entries.remove(entry.ship());
                ledger.setDirty();
                continue;
            }
            if (!entry.active()
                    && !entry.autoReturn()
                    && entry.pending().stream()
                            .noneMatch(
                                    id ->
                                            server.getPlayerList().getPlayer(UUID.fromString(id))
                                                    != null)) continue;
            var world =
                    server.getLevel(
                            ResourceKey.create(
                                    Registries.DIMENSION, Identifier.parse(entry.dimension())));
            if (world == null) continue;
            var pos = BlockPos.of(entry.position());
            world.getChunkSource()
                    .addTicketWithRadius(
                            TicketType.PORTAL, new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4), 2);
            world.getChunkAt(pos);
            var entity = world.getEntityInAnyDimension(UUID.fromString(entry.ship()));
            if (!(entity instanceof SpaceshipEntity ship)) continue;
            try {
                if (entry.autoReturn()) {
                    var target = homeWorld(ship);
                    if (target == null) continue;
                    var owner = server.getPlayerList().getPlayer(UUID.fromString(entry.owner()));
                    var landing = destination(ship, owner, target);
                    var passengers =
                            ship.getPassengers().stream()
                                    .filter(e -> e instanceof ServerPlayer)
                                    .map(e -> (ServerPlayer) e)
                                    .toList();
                    ship.fuel(0);
                    ship.flight = new CompoundTag();
                    ship.inFlight = false;
                    var returned =
                            teleportGroup(
                                    ship,
                                    target,
                                    Vec3.atBottomCenterOf(landing),
                                    passengers,
                                    "ship_auto_return");
                    if (returned != null) {
                        ledger.put(
                                new FlightLedger.Entry(
                                        entry.ship(),
                                        entry.owner(),
                                        target.dimension().identifier().toString(),
                                        returned.blockPosition().asLong(),
                                        false,
                                        false,
                                        entry.pending(),
                                        entry.crew()));
                        DolfinSpace.LOGGER.info(
                                "event=ship_auto_return ship={} owner={} fuel=0 pos={}",
                                entry.ship(),
                                entry.owner(),
                                landing);
                    }
                    continue;
                }
                var j = journal(ship);
                // Reconnect recovery completes the arrival immediately instead of leaving a player
                // at
                // altitude.
                if (j != null
                        && entry.pending().stream()
                                .anyMatch(
                                        id ->
                                                !riderBlocked.test(ship, UUID.fromString(id))
                                                        && server.getPlayerList()
                                                                        .getPlayer(
                                                                                UUID.fromString(id))
                                                                != null)
                        && j.phase() != FlightJournal.Phase.COUNTDOWN
                        && entry.pending().stream()
                                .anyMatch(
                                        id ->
                                                server.getPlayerList()
                                                                .getPlayer(UUID.fromString(id))
                                                        != null)) {
                    if (j.phase() != FlightJournal.Phase.DESCENT) {
                        transfer(ship, j);
                        continue;
                    }
                    var floor = BlockPos.of(j.target());
                    if (!SafeLanding.surface((ServerLevel) ship.level(), floor))
                        floor = SafeLanding.find((ServerLevel) ship.level(), floor);
                    ship.setPos(floor.getX() + .5, floor.getY(), floor.getZ() + .5);
                    clear(ship, "reconnect_arrival");
                }
                if (ship.inFlight) continue;
                var current = ledger.entries.get(entry.ship());
                for (String id : entry.pending()) {
                    var player = server.getPlayerList().getPlayer(UUID.fromString(id));
                    if (player == null || riderBlocked.test(ship, player.getUUID())) continue;
                    SpaceTravelAccess.runAuthorized(
                            player,
                            "ship_reconnect",
                            () ->
                                    player.teleport(
                                            new TeleportTransition(
                                                    (ServerLevel) ship.level(),
                                                    ship.position(),
                                                    Vec3.ZERO,
                                                    player.getYRot(),
                                                    0,
                                                    TeleportTransition.PLACE_PORTAL_TICKET)));
                    var aboard = new ArrayList<ServerPlayer>();
                    for (var passenger : ship.getPassengers())
                        if (passenger instanceof ServerPlayer sp && sp != player) aboard.add(sp);
                    aboard.add(player);
                    aboard.sort(
                            java.util.Comparator.comparingInt(
                                    p -> {
                                        int index = entry.crew().indexOf(p.getUUID().toString());
                                        return index < 0 ? Integer.MAX_VALUE : index;
                                    }));
                    ship.ejectPassengers();
                    for (var passenger : aboard) passenger.startRiding(ship, true, true);
                    if (current != null) current = current.arrived(id);
                    DolfinSpace.LOGGER.info(
                            "event=ship_reconnect_arrival ship={} player={} dimension={}",
                            ship.getUUID(),
                            id,
                            ship.level().dimension().identifier());
                }
                if (current != null) ledger.put(current);
            } catch (RuntimeException e) {
                DolfinSpace.LOGGER.warn(
                        "event=ship_recovery_wait ship={} reason={}", entry.ship(), e.toString());
            }
        }
    }

    private FlightController() {}
}
