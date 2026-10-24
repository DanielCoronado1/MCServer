package com.dolfin.lobby;

import com.dolfin.creepers.dungeon.Dungeons;
import com.dolfin.lobby.mixin.PlayerStateAccess;
import com.dolfin.space.KeplerDimension;
import com.dolfin.space.oxygen.*;
import com.dolfin.space.ships.*;
import com.dolfin.space.travel.*;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.*;

/**
 * One escrow per UUID, one arena and ship per session. All entry/exit paths share these methods.
 */
public final class StationSessions {
    public final MinecraftServer server;
    public final LobbyConfig config;
    public final SessionStore store;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Set<UUID> restoring = new HashSet<>();
    private final Set<UUID> loginPending = new HashSet<>();
    private final Set<UUID> freshCapture = new HashSet<>();
    private final Map<UUID, Integer> movedAt = new HashMap<>();
    private final Map<UUID, Runnable> queued = new HashMap<>();

    private boolean defer(ServerPlayer p, Runnable action) {
        if (movedAt.getOrDefault(p.getUUID(), -1) == server.getTickCount()) {
            queued.put(p.getUUID(), action);
            return true;
        }
        movedAt.put(p.getUUID(), server.getTickCount());
        return false;
    }

    public StationSessions(MinecraftServer server, LobbyConfig config) throws IOException {
        this.server = server;
        this.config = config;
        store =
                new SessionStore(
                        server.getWorldPath(LevelResource.ROOT).resolve("dolfin-lobby/sessions"));
        StationLayout.loadGeneration(server);
        FlightController.allowLaunchWorld(config.showcase());
    }

    public boolean recoveryBlocked(com.dolfin.space.ships.SpaceshipEntity ship, UUID id) {
        return isolated(id)
                && (loginPending.contains(id) || !ship.entityTags().contains("dolfin_demo_" + id));
    }

    public boolean isolated(UUID id) {
        return store.contains(id) && !restoring.contains(id);
    }

    public boolean ready() {
        return server.getLevel(LobbyConfig.key(config.lobby())) != null
                && server.getLevel(LobbyConfig.key(config.showcase())) != null
                && StationLayout.ready(server);
    }

    /**
     * Registers the lobby join hook and the dungeon entry veto. The join hook runs in a phase after
     * every default-phase listener, so dungeon restoration, hardcore wipes and tomb or reward
     * deliveries have settled before the survival state is captured.
     */
    static void integrate(java.util.function.Supplier<StationSessions> current) {
        Identifier late = Identifier.fromNamespaceAndPath("dolfinlobby", "after_dungeons");
        ServerPlayConnectionEvents.JOIN.addPhaseOrdering(Event.DEFAULT_PHASE, late);
        ServerPlayConnectionEvents.JOIN.register(
                late,
                (handler, sender, server) -> {
                    var sessions = current.get();
                    if (sessions != null) sessions.login(handler.player);
                });
        Dungeons.partidas()
                .usarVeto(
                        p -> {
                            var sessions = current.get();
                            return sessions != null && sessions.isolated(p.getUUID());
                        });
    }

    private static boolean dungeonBusy(ServerPlayer p) {
        return Dungeons.entradaSalida().ocupado(p);
    }

    public void login(ServerPlayer p) {
        if (!ready()) return;
        if (dungeonBusy(p)) return;
        try {
            // Fabric JOIN runs before vanilla adds the player to its original ServerLevel.
            // Escrow immediately pauses recovery, but dimension changes wait for registration.
            if (!store.contains(p.getUUID())) freshCapture.add(p.getUUID());
            store.capture(p);
            loginPending.add(p.getUUID());
            queued.put(p.getUUID(), () -> lobby(p));
        } catch (Exception error) {
            fail(p, error);
            p.connection.disconnect(
                    Component.literal(
                            "No se pudo proteger tu inventario. Avisa a un administrador."));
        }
    }

    public void lobby(ServerPlayer p) {
        if (!isolated(p.getUUID()) && dungeonBusy(p)) {
            message(p, "Estás en una dungeon: sal con /dungeon leave antes de ir a la estación.");
            return;
        }
        if (defer(p, () -> lobby(p))) return;
        if (!ready()) {
            message(p, "La estación todavía no está preparada.");
            return;
        }
        try {
            // A capture made at join may predate deliveries by other join hooks; refresh it only
            // while the player is still in the world it was taken in.
            if (freshCapture.remove(p.getUUID()) && !StationProtection.station(p.level()))
                store.recapture(p);
            else store.capture(p);
            cleanup(p.getUUID());
            clear(p);
            state(p, "LOBBY");
            loginPending.remove(p.getUUID());
            teleport(p, server.getLevel(LobbyConfig.key(config.lobby())), new Vec3(.5, 129, 20.5));
            message(
                    p,
                    "Estación Dolfin · Portales: Supervivencia / Exhibición · /lobby siempre te"
                            + " devuelve aquí.");
        } catch (Exception e) {
            fail(p, e);
        }
    }

    public void showcase(ServerPlayer p) {
        if (defer(p, () -> showcase(p))) return;
        if (!isolated(p.getUUID())) {
            lobby(p);
            if (!isolated(p.getUUID())) return;
        }
        try {
            cleanup(p.getUUID());
            clear(p);
            state(p, "SHOWCASE");
            teleport(
                    p,
                    server.getLevel(LobbyConfig.key(config.showcase())),
                    new Vec3(.5, 129, 20.5));
            message(
                    p,
                    "Elige una estación y pulsa su botón. Combate voluntario; /exhibicion lista ·"
                            + " /lobby salir. Nada se conserva en supervivencia.");
        } catch (Exception e) {
            fail(p, e);
        }
    }

    public void survival(ServerPlayer p) {
        if (defer(p, () -> survival(p))) return;
        if (!isolated(p.getUUID())) return;
        var saved = store.get(p.getUUID());
        try {
            var world =
                    server.getLevel(
                            LobbyConfig.key(saved.getStringOr("dimension", "minecraft:overworld")));
            if (world == null)
                throw new IOException(
                        "El mundo de origen no está cargado; tu inventario sigue a salvo.");
            cleanup(p.getUUID());
            clear(p);
            restoring.add(p.getUUID());
            try {
                var survival = saved.getCompoundOrEmpty("survival").copy();
                var left = survival.getCompoundOrEmpty("ShoulderEntityLeft").copy();
                var right = survival.getCompoundOrEmpty("ShoulderEntityRight").copy();
                survival.remove("ShoulderEntityLeft");
                survival.remove("ShoulderEntityRight");
                ((PlayerStateAccess) p)
                        .dolfinlobby$restore(
                                TagValueInput.create(
                                        ProblemReporter.DISCARDING, p.registryAccess(), survival));
                IsolatedAttachments.restore(p, survival);
                com.dolfin.space.SpaceTravelAccess.runAuthorized(
                        p,
                        "lobby_survival_restore",
                        () ->
                                teleport(
                                        p,
                                        world,
                                        new Vec3(
                                                saved.getDoubleOr("x", .5),
                                                saved.getDoubleOr("y", 80),
                                                saved.getDoubleOr("z", .5))));
                ((PlayerStateAccess) p).dolfinlobby$left(left);
                ((PlayerStateAccess) p).dolfinlobby$right(right);
                p.setRemainingFireTicks(survival.getIntOr("Fire", 0));
                p.setAirSupply(survival.getIntOr("Air", p.getMaxAirSupply()));
                p.setYRot(saved.getFloatOr("yaw", 0));
                p.setXRot(saved.getFloatOr("pitch", 0));
                p.inventoryMenu.sendAllDataToRemote();
                p.onUpdateAbilities();
                // Player data is committed before removing escrow; a crash before removal replays
                // the same snapshot.
                server.getPlayerList().saveAll();
                store.finish(p.getUUID());
            } finally {
                restoring.remove(p.getUUID());
            }
            message(p, "Supervivencia · inventario y progreso restaurados.");
        } catch (Exception e) {
            fail(p, e);
        }
    }

    public boolean vulnerable(ServerPlayer p) {
        var s = store.get(p.getUUID());
        return s != null && (s.getStringOr("phase", "").equals("SPACE") || combat(p));
    }

    public boolean combat(ServerPlayer p) {
        var s = store.get(p.getUUID());
        return s != null && s.getStringOr("phase", "").equals("COMBAT");
    }

    public int slot(UUID id) {
        var s = store.get(id);
        return s == null ? -1 : s.getIntOr("arena", -1);
    }

    public void demo(ServerPlayer p, int catalog) {
        if (defer(p, () -> demo(p, catalog))) return;
        if (!isolated(p.getUUID())
                || !p.level().dimension().equals(LobbyConfig.key(config.showcase()))) return;
        if (catalog < 0 || catalog >= ExhibitCatalog.ALL.size()) return;
        try {
            cleanup(p.getUUID());
            clear(p);
            var used = new HashSet<Integer>();
            for (UUID id : store.ids())
                if (!id.equals(p.getUUID()) && combatId(id)) used.add(slot(id));
            int slot = 0;
            while (used.contains(slot)) slot++;
            if (slot >= 16) {
                message(p, "Las arenas están ocupadas. Inténtalo en un momento.");
                return;
            }
            var s = store.get(p.getUUID());
            s.putInt("arena", slot);
            s.putString("phase", "COMBAT");
            s.putInt("catalog", catalog);
            store.put(p.getUUID(), s);
            StationLayout.arena(server, p.level(), slot);
            if (catalog == 9) {
                var c = StationLayout.arenaCenter(slot);
                StationProtection.edit(
                        () ->
                                StationLayout.fill(
                                        p.level(),
                                        c.getX() - 11,
                                        129,
                                        1,
                                        c.getX() + 11,
                                        131,
                                        1,
                                        net.minecraft.world.level.block.Blocks.OAK_PLANKS));
            }
            kit(p, false);
            var entry = ExhibitCatalog.ALL.get(catalog);
            var center = StationLayout.arenaCenter(slot);
            teleport(p, p.level(), Vec3.atBottomCenterOf(center.offset(0, 1, 7)));
            ExhibitCatalog.spawn(p.level(), center.offset(0, 1, -5), entry, p);
            message(
                    p,
                    entry.name()
                            + " · "
                            + entry.description()
                            + " · /exhibicion volver o /lobby para cancelar.");
        } catch (Exception e) {
            fail(p, e);
        }
    }

    public boolean combatIdForProtection(UUID id) {
        return combatId(id);
    }

    private boolean combatId(UUID id) {
        return store.get(id).getStringOr("phase", "").equals("COMBAT");
    }

    public void rocket(ServerPlayer p) {
        if (defer(p, () -> rocket(p))) return;
        if (!isolated(p.getUUID())
                || !p.level().dimension().equals(LobbyConfig.key(config.showcase()))) return;
        for (var id : store.ids())
            if (!id.equals(p.getUUID()) && store.get(id).getStringOr("phase", "").equals("SPACE")) {
                message(p, "La nave está ocupada. Usa otra exhibición mientras regresa.");
                return;
            }
        try {
            cleanup(p.getUUID());
            clear(p);
            kit(p, true);
            var ship = new SpaceshipEntity(ShipsModule.SHIP, p.level());
            ship.owner = p.getUUID();
            ship.homeDimension = config.showcase();
            ship.home = new BlockPos(30, 128, -14);
            ship.fuel(100);
            ship.addTag("dolfin_demo_ship");
            ship.addTag("dolfin_demo_" + p.getUUID());
            ship.setPos(30.5, 129, -13.5);
            var s = store.get(p.getUUID());
            s.putString("ship", ship.getUUID().toString());
            s.putString("phase", "SPACE");
            store.put(p.getUUID(), s);
            p.level().addFreshEntity(ship);
            teleport(p, p.level(), new Vec3(30.5, 129, -8.5));
            p.startRiding(ship, true, true);
            TravelModule.open(p);
            message(
                    p,
                    "Space · Cabina: despegar. En Kepler vuelve a despegar para regresar. /lobby"
                            + " cancela el viaje.");
        } catch (Exception e) {
            fail(p, e);
        }
    }

    public void launchOrReturn(ServerPlayer p) {
        if (p.getVehicle() instanceof SpaceshipEntity s
                && isolated(p.getUUID())
                && s.entityTags().contains("dolfin_demo_" + p.getUUID()))
            FlightController.launch(s, p);
    }

    public void cleanup(UUID id) throws IOException {
        var s = store.get(id);
        if (s == null) return;
        String ship = s.getStringOr("ship", "");
        if (!ship.isEmpty()) {
            FlightController.cancelDemo(server, UUID.fromString(ship));
            server.saveEverything(false, true, true);
            s.remove("ship");
        }
        int slot = s.getIntOr("arena", -1);
        if (slot >= 0) StationLayout.resetArena(server, slot);
        s.remove("arena");
        s.remove("catalog");
        s.remove("visitedKepler");
        store.put(id, s);
    }

    private void state(ServerPlayer p, String phase) throws IOException {
        var s = store.get(p.getUUID());
        s.putString("phase", phase);
        store.put(p.getUUID(), s);
        cooldown.put(p.getUUID(), server.getTickCount() + 40L);
    }

    public void tick() {
        var pending = new LinkedHashMap<>(queued);
        queued.clear();
        for (var entry : pending.entrySet())
            if (server.getPlayerList().getPlayer(entry.getKey()) != null) entry.getValue().run();
        if (server.getTickCount() % 5 != 0 || !ready()) return;
        for (var p : List.copyOf(server.getPlayerList().getPlayers())) {
            if (!isolated(p.getUUID())) {
                if (StationProtection.station(p.level())) lobby(p);
                continue;
            }
            if (!p.isAlive()) continue;
            var s = store.get(p.getUUID());
            String phase = s.getStringOr("phase", "LOBBY");
            if (phase.equals("SPACE")) {
                if (KeplerDimension.isKepler(p.level().dimension())
                        && !s.getBooleanOr("visitedKepler", false)) {
                    s.putBoolean("visitedKepler", true);
                    try {
                        store.put(p.getUUID(), s);
                    } catch (IOException error) {
                        fail(p, error);
                    }
                }
                if (s.getBooleanOr("visitedKepler", false)
                        && p.level().dimension().equals(LobbyConfig.key(config.showcase()))
                        && p.getVehicle() instanceof SpaceshipEntity ship
                        && !ship.inFlight) {
                    showcase(p);
                    continue;
                }

                if (KeplerDimension.isKepler(p.level().dimension())
                        && !p.isPassenger()
                        && !StationProtection.outpost(p.blockPosition()))
                    teleport(p, p.level(), StationLayout.OUTPOST_ARRIVAL);
                if (!KeplerDimension.isKepler(p.level().dimension())
                        && !p.level().dimension().equals(LobbyConfig.key(config.showcase())))
                    lobby(p);
                continue;
            }
            if (phase.equals("COMBAT")) {
                var c = StationLayout.arenaCenter(slot(p.getUUID()));
                if (!p.level().dimension().equals(LobbyConfig.key(config.showcase()))
                        || Math.abs(p.getX() - c.getX()) > 12
                        || Math.abs(p.getZ() - c.getZ()) > 12
                        || p.getY() < 122
                        || p.getY() > 145) showcase(p);
                continue;
            }
            if (!StationProtection.station(p.level())) {
                lobby(p);
                continue;
            }
            if (p.getY() < 127) {
                lobby(p);
                continue;
            }
            if (server.getTickCount() < cooldown.getOrDefault(p.getUUID(), 0L)) continue;
            if (p.level().dimension().equals(LobbyConfig.key(config.lobby()))
                    && Math.abs(p.getZ() + 1) < 2
                    && p.getY() < 134) {
                if (Math.abs(p.getX() + 20) < 2.5) survival(p);
                else if (Math.abs(p.getX() - 20) < 2.5) showcase(p);
            } else if (p.level().dimension().equals(LobbyConfig.key(config.showcase()))
                    && p.getZ() > 28
                    && Math.abs(p.getX()) < 3) lobby(p);
        }
    }

    public static void clear(ServerPlayer p) {
        p.closeContainer();
        ((PlayerStateAccess) p).dolfinlobby$left(new CompoundTag());
        ((PlayerStateAccess) p).dolfinlobby$right(new CompoundTag());
        p.getInventory().clearContent();
        p.inventoryMenu.getCraftSlots().clearContent();
        p.inventoryMenu.setCarried(ItemStack.EMPTY);
        ((TankAccess) p).dolfinspace$tank().clearContent();
        IsolatedAttachments.clear(p);
        p.removeAllEffects();
        p.setRemainingFireTicks(0);
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.experienceLevel = 0;
        p.totalExperience = 0;
        p.experienceProgress = 0;
        p.setGameMode(GameType.ADVENTURE);
        p.stopRiding();
        p.inventoryMenu.sendAllDataToRemote();
    }

    private static void kit(ServerPlayer p, boolean space) {
        for (var item :
                List.of(
                        Items.DIAMOND_SWORD,
                        Items.DIAMOND_AXE,
                        Items.SHIELD,
                        Items.COOKED_BEEF,
                        Items.GOLDEN_APPLE))
            p.getInventory().add(new ItemStack(item, item == Items.COOKED_BEEF ? 32 : 1));
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        if (space)
            ((TankAccess) p).dolfinspace$tank().setItem(0, new ItemStack(OxygenModule.LARGE));
    }

    public static void teleport(ServerPlayer p, ServerLevel world, Vec3 pos) {
        p.stopRiding();
        var moved = new ServerPlayer[1];
        StationProtection.route(
                () ->
                        moved[0] =
                                p.teleport(
                                        new TeleportTransition(
                                                world,
                                                pos,
                                                Vec3.ZERO,
                                                0,
                                                0,
                                                TeleportTransition.PLACE_PORTAL_TICKET)));
        if (moved[0] == null)
            throw new IllegalStateException(
                    "No se pudo completar el traslado; se conserva el resguardo.");
    }

    public static void message(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text));
    }

    private static void fail(ServerPlayer p, Exception e) {
        DolfinLobby.LOGGER.error("event=session_failed player={}", p.getUUID(), e);
        message(
                p,
                "La sesión está bloqueada para proteger tus datos. Avisa a un administrador: "
                        + e.getMessage());
    }
}
