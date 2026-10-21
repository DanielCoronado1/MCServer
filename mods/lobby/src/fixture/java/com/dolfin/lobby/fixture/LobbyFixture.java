package com.dolfin.lobby.fixture;

import com.dolfin.lobby.*;
import com.dolfin.space.*;
import com.dolfin.space.oxygen.*;
import com.dolfin.space.ships.*;
import com.dolfin.space.travel.*;
import com.mojang.authlib.GameProfile;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

import java.util.*;

/**
 * Headless server-player fixture. Real game state/events; packet delivery/UI are deliberately
 * absent.
 */
public final class LobbyFixture implements ModInitializer {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
            B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final Map<String, SpaceshipEntity> ships = new HashMap<>();
    private static ServerPlayer a, b;
    private static final java.util.Deque<Runnable> tasks = new java.util.ArrayDeque<>();
    private static String currentStep;
    private static MinecraftServer currentServer;

    private static void plan(Runnable... steps) {
        tasks.addAll(java.util.List.of(steps));
    }

    @Override
    public void onInitialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    // Sink connections never send chunk movement; keep the same small player area
                    // loaded that a connected client would maintain. Restart tests have no such
                    // tickets.
                    if (server.getTickCount() % 20 == 0)
                        for (UUID id : java.util.List.of(A, B)) {
                            var p = server.getPlayerList().getPlayer(id);
                            if (p != null)
                                p.level()
                                        .getChunkSource()
                                        .addTicketWithRadius(
                                                TicketType.PORTAL,
                                                new ChunkPos(
                                                        p.blockPosition().getX() >> 4,
                                                        p.blockPosition().getZ() >> 4),
                                                2);
                        }
                    if (server.getTickCount() % 3 != 0 || tasks.isEmpty()) return;
                    try {
                        tasks.removeFirst().run();
                        if (tasks.isEmpty())
                            DolfinLobby.LOGGER.info("FIXTURE_PASS {}", currentStep);
                    } catch (Throwable error) {
                        tasks.clear();
                        DolfinLobby.LOGGER.error("FIXTURE_FAIL " + currentStep, error);
                    }
                });
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, environment) -> {
                    var root =
                            Commands.literal("lobbyfixture")
                                    .requires(source -> source.getEntity() == null);
                    var cases =
                            new java.util.LinkedHashMap<
                                    String, java.util.function.Consumer<MinecraftServer>>();
                    cases.put("layout-seed", server -> layout(server, true));
                    cases.put("layout-verify", server -> layout(server, false));
                    cases.put("setup", LobbyFixture::setup);
                    cases.put("checks", LobbyFixture::checks);
                    cases.put("rocket", LobbyFixture::rocket);
                    cases.put("kepler", LobbyFixture::kepler);
                    cases.put("returned", LobbyFixture::returned);
                    cases.put("survival-rocket", LobbyFixture::survivalRocket);
                    cases.put("survival-kepler", LobbyFixture::survivalKepler);
                    cases.put("survival-returned", LobbyFixture::survivalReturned);
                    cases.put("survival-death", LobbyFixture::survivalDeath);
                    cases.put("recovery-pause", LobbyFixture::recoveryPause);
                    cases.put("phases", LobbyFixture::phases);
                    cases.put("portals", LobbyFixture::portals);
                    cases.put("resume", LobbyFixture::resume);
                    for (var phase : FlightJournal.Phase.values())
                        cases.put(
                                "prepare-" + phase.name(), server -> prepareRestart(server, phase));
                    cases.forEach(
                            (name, action) ->
                                    root.then(
                                            Commands.literal(name)
                                                    .executes(
                                                            context ->
                                                                    run(
                                                                            context.getSource()
                                                                                    .getServer(),
                                                                            name,
                                                                            () ->
                                                                                    action.accept(
                                                                                            context.getSource()
                                                                                                    .getServer())))));
                    dispatcher.register(root);
                });
    }

    private static int run(MinecraftServer server, String step, Runnable action) {
        try {
            action.run();
            currentStep = step;
            currentServer = server;
            if (tasks.isEmpty()) DolfinLobby.LOGGER.info("FIXTURE_PASS {}", step);
            return 1;
        } catch (Throwable e) {
            DolfinLobby.LOGGER.error("FIXTURE_FAIL " + step, e);
            return 0;
        }
    }

    private static void check(boolean result, String name) {
        if (!result) throw new AssertionError(name);
        DolfinLobby.LOGGER.info("FIXTURE_CHECK {}", name);
    }

    private static ServerPlayer player(MinecraftServer server, UUID id, String name) {
        var profile = new GameProfile(id, name);
        var p =
                new ServerPlayer(
                        server, server.overworld(), profile, ClientInformation.createDefault());
        var connection =
                new Connection(PacketFlow.SERVERBOUND) {
                    private net.minecraft.network.PacketListener listener;

                    @Override
                    public <T extends net.minecraft.network.PacketListener>
                            void setupInboundProtocol(
                                    net.minecraft.network.ProtocolInfo<T> protocol, T handler) {
                        listener = handler;
                    }

                    @Override
                    public void setupOutboundProtocol(
                            net.minecraft.network.ProtocolInfo<?> protocol) {}

                    @Override
                    public net.minecraft.network.PacketListener getPacketListener() {
                        return listener;
                    }

                    @Override
                    public void send(Packet<?> packet) {}

                    @Override
                    public void send(
                            Packet<?> packet, io.netty.channel.ChannelFutureListener handler) {}

                    @Override
                    public void send(
                            Packet<?> packet,
                            io.netty.channel.ChannelFutureListener handler,
                            boolean flush) {}

                    @Override
                    public java.net.SocketAddress getRemoteAddress() {
                        return new java.net.InetSocketAddress("127.0.0.1", 12345);
                    }

                    @Override
                    public String getLoggableAddress(boolean log) {
                        return "fixture";
                    }

                    @Override
                    public boolean isMemoryConnection() {
                        return true;
                    }

                    @Override
                    public boolean isConnected() {
                        return true;
                    }

                    @Override
                    public boolean isConnecting() {
                        return false;
                    }

                    @Override
                    public void flushChannel() {}
                };
        server.getPlayerList()
                .placeNewPlayer(connection, p, CommonListenerCookie.createInitial(profile, false));
        return p;
    }

    private static void seed(ServerPlayer p, int count) {
        StationLayout.fill(
                p.level(),
                6,
                200,
                6,
                10,
                200,
                10,
                net.minecraft.world.level.block.Blocks.SMOOTH_STONE);
        p.setPos(8.5, 201, 8.5);
        p.getInventory().clearContent();
        p.getInventory().add(new ItemStack(Items.DIAMOND, count));
        p.getInventory().add(componentBox());
        p.inventoryMenu.setCarried(new ItemStack(Items.IRON_INGOT, 3));
        p.inventoryMenu.getCraftSlots().setItem(0, new ItemStack(Items.COBBLESTONE, 4));
        p.setHealth(18);
        p.addEffect(
                new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.RESISTANCE, 12000, 0));
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        p.experienceLevel = count;
        p.totalExperience = count * 100;
        p.experienceProgress = .25f;
        p.getFoodData().setFoodLevel(13);
        ((TankAccess) p).dolfinspace$tank().setItem(0, new ItemStack(OxygenModule.SMALL));
        p.getEnderChestInventory().setItem(0, new ItemStack(Items.EMERALD, count));
    }

    private static void inventory(ServerPlayer p, int count) {
        check(p.getInventory().countItem(Items.DIAMOND) == count, "inventory exact " + count);
        check(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET), "armor restored");
        check(
                p.experienceLevel == count
                        && p.totalExperience == count * 100
                        && p.experienceProgress == .25f,
                "XP restored");
        check(p.getFoodData().getFoodLevel() == 13, "food restored");
        check(p.getEnderChestInventory().getItem(0).getCount() == count, "ender preserved");
        check(
                ((TankAccess) p).dolfinspace$tank().getItem(0).is(OxygenModule.SMALL),
                "equipped tank restored");
        check(p.gameMode() == GameType.SURVIVAL, "survival gamemode restored");
        check(
                p.getInventory().countItem(Items.IRON_INGOT) == 3
                        && p.getInventory().countItem(Items.COBBLESTONE) == 4,
                "cursor and crafting items preserved once");
        check(
                p.getInventory().getNonEquipmentItems().stream()
                        .anyMatch(item -> ItemStack.isSameItemSameComponents(item, componentBox())),
                "nested item components preserved");
        check(
                p.hasEffect(net.minecraft.world.effect.MobEffects.RESISTANCE)
                        && p.getHealth() == 18,
                "survival health and effects restored");
    }

    private static void setup(MinecraftServer server) {
        a = player(server, A, "Fixture_A");
        b = player(server, B, "Fixture_B");
        var s = DolfinLobby.sessions();
        plan(
                () -> {
                    check(s.ready(), "functional world routes ready");
                    for (String worldId :
                            java.util.List.of(s.config.lobby(), s.config.showcase())) {
                        var world = server.getLevel(LobbyConfig.key(worldId));
                        check(
                                world.getBlockState(new BlockPos(0, 128, 20))
                                        .is(net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ),
                                "real arrival floor " + worldId);
                        check(
                                world.getBlockState(new BlockPos(48, 131, 0))
                                        .is(net.minecraft.world.level.block.Blocks.TINTED_GLASS),
                                "real station perimeter " + worldId);
                    }
                    s.survival(a);
                    s.survival(b);
                },
                () -> {
                    seed(a, 11);
                    seed(b, 22);
                    s.login(a);
                    s.login(b);
                },
                () -> {
                    check(
                            a.level().dimension().equals(LobbyConfig.key(s.config.lobby()))
                                    && b.level() == a.level(),
                            "two logins routed lobby");
                    check(
                            a.getInventory().isEmpty() && b.getInventory().isEmpty(),
                            "survival inventory escrowed");
                    check(
                            !a.permissions()
                                    .hasPermission(
                                            net.minecraft.server.permissions.Permissions
                                                    .COMMANDS_GAMEMASTER),
                            "non-op fixture");
                    s.survival(a);
                    s.survival(b);
                },
                () -> {
                    inventory(a, 11);
                    inventory(b, 22);
                    check(!s.isolated(A) && !s.isolated(B), "escrow released after save");
                    s.lobby(a);
                    s.lobby(b);
                },
                () -> {
                    s.showcase(a);
                    s.showcase(b);
                },
                () -> {
                    check(
                            !a.isRemoved() && !b.isRemoved(),
                            "players remain registered after navigation");
                });
    }

    private static void checks(MinecraftServer server) {
        var s = DolfinLobby.sessions();
        plan(
                () -> {
                    s.demo(a, 0);
                    s.demo(b, 9);
                    check(s.slot(A) != s.slot(B), "two simultaneous independent arenas");
                },
                () -> {
                    check(
                            a.getInventory().countItem(Items.DIAMOND) == 0,
                            "demo contains no survival diamonds");
                    var before = s.store.get(A).getCompoundOrEmpty("survival").copy();
                    a.giveExperiencePoints(300);
                    a.giveExperienceLevels(10);
                    check(a.experienceLevel == 0 && a.totalExperience == 0, "demo XP blocked");
                    check(
                            a.drop(
                                            a.getInventory().getItem(0).copy(),
                                            false,
                                            net.minecraft.util.Prediction.SERVER_ONLY)
                                    == null,
                            "demo item drop blocked");
                    check(
                            !a.level()
                                    .setBlockAndUpdate(
                                            new BlockPos(0, 128, 0),
                                            net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK
                                                    .defaultBlockState()),
                            "station floor immutable");
                    check(
                            s.store.get(A).getCompoundOrEmpty("survival").equals(before),
                            "survival snapshot untouched by combat");
                    server.getCommands()
                            .performPrefixedCommand(
                                    a.createCommandSourceStack(), "give Fixture_A diamond 64");
                    check(
                            a.getInventory().countItem(Items.DIAMOND) == 0,
                            "alternate command blocked");
                    var jump = net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.JUMP);
                    int oldStat = a.getStats().getValue(jump);
                    a.awardStat(jump, 10);
                    check(a.getStats().getValue(jump) == oldStat, "demo statistic blocked");
                    var advancement =
                            server.getAdvancements()
                                    .get(
                                            net.minecraft.resources.Identifier.parse(
                                                    "minecraft:story/root"));
                    check(
                            !a.getAdvancements().award(advancement, "crafting_table"),
                            "demo advancement blocked");
                    var recipeKey =
                            net.minecraft.resources.ResourceKey
                                    .<net.minecraft.world.item.crafting.Recipe<?>>create(
                                            net.minecraft.core.registries.Registries.RECIPE,
                                            net.minecraft.resources.Identifier.parse(
                                                    "minecraft:crafting_table"));
                    boolean known = a.getRecipeBook().contains(recipeKey);
                    a.awardRecipesByKey(java.util.List.of(recipeKey));
                    check(a.getRecipeBook().contains(recipeKey) == known, "demo recipe blocked");
                    a.connection.handleAcceptPlayerLoad(
                            new net.minecraft.network.protocol.game
                                    .ServerboundPlayerLoadedPacket());
                    a.hasChangedDimension();
                    a.setInvulnerableTime(0);
                    a.hurtServer(a.level(), a.damageSources().genericKill(), 100000);
                    check(
                            a.isAlive()
                                    && a.level()
                                            .dimension()
                                            .equals(LobbyConfig.key(s.config.lobby())),
                            "demo lethal damage cancelled to lobby");
                    check(s.combat(b), "other arena survives first player death");
                },
                () -> {
                    s.survival(a);
                    s.showcase(b);
                },
                () -> {
                    inventory(a, 11);
                    s.lobby(b);
                },
                () -> {
                    s.survival(b);
                    s.lobby(a);
                },
                () -> {
                    inventory(b, 22);
                    s.showcase(a);
                });
        for (int i = 0; i < ExhibitCatalog.ALL.size(); i++) {
            final int index = i;
            plan(
                    () -> {
                        s.demo(a, index);
                        var id = ExhibitCatalog.ALL.get(index).id();
                        check(
                                a
                                        .level()
                                        .getEntities(
                                                (Entity) null,
                                                new AABB(StationLayout.arenaCenter(s.slot(A)))
                                                        .inflate(16, 20, 16))
                                        .stream()
                                        .anyMatch(
                                                e ->
                                                        net.minecraft.core.registries
                                                                .BuiltInRegistries.ENTITY_TYPE
                                                                .getKey(e.getType())
                                                                .toString()
                                                                .equals(id)),
                                "catalog spawned " + id);
                        s.showcase(a);
                    });
        }
        plan(() -> s.lobby(a), () -> s.survival(a), () -> inventory(a, 11));
    }

    private static void rocket(MinecraftServer server) {
        var s = DolfinLobby.sessions();
        plan(
                () -> s.lobby(a),
                () -> s.showcase(a),
                () -> s.rocket(a),
                () -> {
                    check(a.getVehicle() instanceof SpaceshipEntity, "demo rocket boarded");
                    var ship = (SpaceshipEntity) a.getVehicle();
                    ships.put("demo", ship);
                    check(
                            FlightController.launch(ship, a),
                            "normal demo launch checklist accepted");
                    check(
                            ship.homeDimension.equals(s.config.showcase()),
                            "demo home dimension recorded");
                });
    }

    private static void kepler(MinecraftServer server) {
        check(
                KeplerDimension.isKepler(a.level().dimension()),
                "normal flight arrived existing Kepler");
        check(a.getVehicle() instanceof SpaceshipEntity, "arrival retains seat");
        var ship = (SpaceshipEntity) a.getVehicle();
        check(!ship.inFlight, "Kepler landing completed");
        check(StationProtection.outpost(ship.blockPosition()), "reserved outpost landing");
        check(FlightController.launch(ship, a), "normal return launch accepted");
    }

    private static void returned(MinecraftServer server) {
        var s = DolfinLobby.sessions();
        check(
                a.level().dimension().equals(LobbyConfig.key(s.config.showcase())),
                "normal rocket returned showcase");
        check(
                !a.isPassenger()
                        && DolfinLobby.sessions()
                                .store
                                .get(A)
                                .getStringOr("phase", "")
                                .equals("SHOWCASE"),
                "return finalizes exact demo ship and kit");
        var unrelated =
                new SpaceshipEntity(ShipsModule.SHIP, server.getLevel(KeplerDimension.LEVEL));
        unrelated.owner = A;
        unrelated.setPos(100010.5, 201, 100010.5);
        ((ServerLevel) unrelated.level()).getChunkAt(unrelated.blockPosition());
        ((ServerLevel) unrelated.level())
                .getChunkSource()
                .addTicketWithRadius(
                        TicketType.PORTAL,
                        new ChunkPos(
                                unrelated.blockPosition().getX() >> 4,
                                unrelated.blockPosition().getZ() >> 4),
                        2);
        check(unrelated.level().addFreshEntity(unrelated), "unrelated survival ship created");
        FlightLedger.get(server).track(unrelated);
        plan(
                () -> s.lobby(a),
                () -> {
                    check(
                            !unrelated.isRemoved()
                                    && FlightLedger.get(server)
                                            .entries
                                            .containsKey(unrelated.getUUID().toString()),
                            "escape preserves unrelated same-owner survival ship");
                    check(!a.isPassenger(), "lobby escape dismounted");
                    s.survival(a);
                },
                () -> inventory(a, 11));
    }

    private static void prepareRestart(MinecraftServer server, FlightJournal.Phase phase) {
        var s = DolfinLobby.sessions();
        plan(
                () -> {
                    s.lobby(a);
                    s.lobby(b);
                },
                () -> {
                    s.showcase(a);
                    s.showcase(b);
                },
                () -> {
                    s.demo(a, 10);
                    s.rocket(b);
                },
                () -> {
                    s.launchOrReturn(b);
                    inject((SpaceshipEntity) b.getVehicle(), phase, b);
                    check(
                            s.isolated(A) && s.isolated(B),
                            "restart setup has two independent escrows");
                    server.saveEverything(false, true, true);
                });
    }

    private static void resume(MinecraftServer server) {
        var s = DolfinLobby.sessions();
        check(s.store.contains(A) && s.store.contains(B), "restart loaded two escrows");
        a = player(server, A, "Fixture_A");
        b = player(server, B, "Fixture_B");
        plan(
                () -> {
                    check(
                            a.level().dimension().equals(LobbyConfig.key(s.config.lobby()))
                                    && b.level() == a.level(),
                            "restart logins lobby");
                    FlightController.recover(server);
                    check(
                            !a.isPassenger() && !b.isPassenger(),
                            "restart escape prevents reboarding");
                    s.survival(a);
                    s.survival(b);
                },
                () -> {
                    inventory(a, 11);
                    inventory(b, 22);
                });
    }

    private static ItemStack componentBox() {
        var item = new ItemStack(Items.SHULKER_BOX);
        item.set(
                net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Recuerdo supervivencia"));
        item.set(
                net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(
                        java.util.List.of(new ItemStack(Items.NETHER_STAR, 7))));
        return item;
    }

    private static void survivalRocket(MinecraftServer server) {
        var old = new CompoundTag();
        old.putString("owner", A.toString());
        old.putInt("tier", 0);
        old.putInt("fuel", 100);
        old.putLong("home", new BlockPos(8, 200, 8).asLong());
        old.putBoolean("hasHome", true);
        old.putBoolean("passengersAllowed", false);
        var state = ShipState.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
        check(
                state.homeDimension().equals("minecraft:overworld"),
                "legacy save defaults recorded home to overworld");
        for (int x = 6; x <= 10; x++)
            for (int z = 6; z <= 10; z++)
                server.overworld()
                        .setBlockAndUpdate(
                                new BlockPos(x, 200, z), ShipsModule.PLATFORM.defaultBlockState());
        var ship = new SpaceshipEntity(ShipsModule.SHIP, server.overworld());
        ship.apply(state);
        ship.setPos(8.5, 201, 8.5);
        server.overworld().addFreshEntity(ship);
        ships.put("survival", ship);
        a.startRiding(ship, true, true);
        check(FlightController.launch(ship, a), "legacy survival ship normal launch accepted");
    }

    private static void survivalKepler(MinecraftServer server) {
        check(
                KeplerDimension.isKepler(a.level().dimension()),
                "legacy survival flight reaches existing Kepler");
        var ship = (SpaceshipEntity) a.getVehicle();
        check(
                !ship.inFlight && !StationProtection.outpost(ship.blockPosition()),
                "survival trip uses original planet landing");
        ships.put("survival", ship);
        var s = DolfinLobby.sessions();
        plan(
                () -> s.lobby(a),
                () -> {
                    check(
                            a.level().dimension().equals(LobbyConfig.key(s.config.lobby())),
                            "survival Kepler login escape lobby");
                    s.survival(a);
                },
                () -> {
                    check(
                            KeplerDimension.isKepler(a.level().dimension()),
                            "authorized escrow restores original survival Kepler");
                    a.startRiding(ship, true, true);
                    check(
                            FlightController.launch(ship, a),
                            "legacy survival return launch accepted");
                });
    }

    private static void survivalReturned(MinecraftServer server) {
        check(
                a.level().dimension().equals(Level.OVERWORLD),
                "legacy survival rocket returns overworld");
        var ship = (SpaceshipEntity) a.getVehicle();
        check(
                !ship.inFlight && ship.blockPosition().equals(new BlockPos(8, 201, 8)),
                "survival rocket retains original home platform");
        check(
                ship.homeDimension.equals("minecraft:overworld"),
                "survival origin unchanged by showcase");
        ships.put("survival", ship);
        a.stopRiding();
        inventory(a, 11);
    }

    private static void inject(SpaceshipEntity ship, FlightJournal.Phase phase, ServerPlayer p) {
        var journal =
                new FlightJournal(
                        phase,
                        200,
                        ship.getY(),
                        StationLayout.OUTPOST_PAD.above().asLong(),
                        KeplerDimension.ID.toString(),
                        java.util.List.of(p.getUUID().toString()));
        ship.flight =
                (CompoundTag)
                        FlightJournal.CODEC.encodeStart(NbtOps.INSTANCE, journal).getOrThrow();
        ship.inFlight = true;
        FlightLedger.get(currentServer).track(ship);
    }

    private static void phases(MinecraftServer server) {
        var s = DolfinLobby.sessions();
        for (var phase : FlightJournal.Phase.values())
            for (int mode = 0; mode < 3; mode++) {
                final int action = mode;
                plan(
                        () -> s.lobby(a),
                        () -> s.showcase(a),
                        () -> s.rocket(a),
                        () -> {
                            var ship = (SpaceshipEntity) a.getVehicle();
                            UUID id = ship.getUUID();
                            inject(ship, phase, a);
                            if (action == 0)
                                server.getCommands()
                                        .performPrefixedCommand(
                                                a.createCommandSourceStack(), "lobby");
                            else if (action == 1) {
                                a.connection.handleAcceptPlayerLoad(
                                        new net.minecraft.network.protocol.game
                                                .ServerboundPlayerLoadedPacket());
                                a.hasChangedDimension();
                                a.setInvulnerableTime(0);
                                a.hurtServer(a.level(), a.damageSources().genericKill(), 100000);
                            } else {
                                FlightController.disconnected(a);
                                s.login(a);
                            }
                            ships.put("cancelled", ship);
                        },
                        () -> {
                            var ship = ships.get("cancelled");
                            check(
                                    a.level().dimension().equals(LobbyConfig.key(s.config.lobby()))
                                            && !a.isPassenger(),
                                    "phase " + phase + " exit " + action + " lobby without seat");
                            check(
                                    DemoCancellations.get(server).contains(ship.getUUID())
                                            && !FlightLedger.get(server)
                                                    .entries
                                                    .containsKey(ship.getUUID().toString()),
                                    "phase " + phase + " exact recovery cancelled " + action);
                        });
            }
        plan(() -> s.survival(a), () -> inventory(a, 11));
    }

    private static void portals(MinecraftServer server) {
        var s = DolfinLobby.sessions();
        plan(
                () -> {
                    server.getCommands()
                            .performPrefixedCommand(a.createCommandSourceStack(), "lobby");
                    s.lobby(b);
                });
        for (int i = 0; i < 16; i++) plan(() -> {});
        plan(
                () -> {
                    a.setPos(20.5, 129, -.5);
                    b.setPos(-19.5, 129, -.5);
                },
                () -> {},
                () -> {
                    check(
                            a.level().dimension().equals(LobbyConfig.key(s.config.showcase())),
                            "ordinary physical showcase portal");
                    check(
                            b.level().dimension().equals(Level.OVERWORLD),
                            "ordinary physical survival portal");
                    inventory(b, 22);
                    check(
                            !server.getCommands()
                                    .getDispatcher()
                                    .getRoot()
                                    .getChild("dolfinlobby")
                                    .canUse(b.createCommandSourceStack()),
                            "non-op cannot provision worlds");
                    server.getCommands()
                            .performPrefixedCommand(
                                    a.createCommandSourceStack(), "exhibicion probar 1");
                },
                () -> check(s.combat(a), "ordinary exhibition command activates combat"),
                () -> s.lobby(a),
                () -> s.survival(a),
                () -> inventory(a, 11));
    }

    private static void survivalDeath(MinecraftServer server) {
        var s = DolfinLobby.sessions();
        check(
                !s.isolated(B) && b.level().dimension().equals(Level.OVERWORLD),
                "survival death starts outside demo session");
        b.connection.handleAcceptPlayerLoad(
                new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        b.hasChangedDimension();
        b.setInvulnerableTime(0);
        b.hurtServer(b.level(), b.damageSources().genericKill(), 100000);
        check(
                !b.isAlive() && !s.isolated(B) && b.level().dimension().equals(Level.OVERWORLD),
                "ordinary survival death is not redirected or cancelled");
        check(
                b.getInventory().countItem(Items.DIAMOND) == 0,
                "ordinary survival inventory drops retained");
        plan(
                () -> {
                    b = server.getPlayerList().respawn(b, false, Entity.RemovalReason.KILLED);
                },
                () -> {
                    check(
                            b.isAlive()
                                    && b.level().dimension().equals(Level.OVERWORLD)
                                    && !s.isolated(B),
                            "ordinary survival respawn stays survival");
                    seed(b, 22);
                });
    }

    private static void recoveryPause(MinecraftServer server) {
        var s = DolfinLobby.sessions();
        var ship = ships.get("survival");
        var current = (SpaceshipEntity) server.overworld().getEntityInAnyDimension(ship.getUUID());
        check(current != null, "survival ship available for pending recovery test");
        var ledger = FlightLedger.get(server);
        ledger.track(current);
        plan(
                () -> {
                    s.lobby(a);
                    ledger.put(
                            ledger.entries.get(current.getUUID().toString()).pending(A.toString()));
                });
        for (int i = 0; i < 10; i++) plan(() -> {});
        plan(
                () -> {
                    check(
                            a.level().dimension().equals(LobbyConfig.key(s.config.lobby()))
                                    && !a.isPassenger(),
                            "survival pending rider held in login lobby");
                    check(
                            ledger.entries
                                    .get(current.getUUID().toString())
                                    .pending()
                                    .contains(A.toString()),
                            "unrelated survival recovery preserved while isolated");
                    s.survival(a);
                });
        for (int i = 0; i < 10; i++) plan(() -> {});
        plan(
                () -> {
                    check(
                            a.getVehicle() instanceof SpaceshipEntity recovered
                                    && recovered.getUUID().equals(current.getUUID()),
                            "intentional survival return resumes original recovery");
                    a.stopRiding();
                    inventory(a, 11);
                });
    }

    private static void layout(MinecraftServer server, boolean seed) {
        var s = DolfinLobby.sessions();
        if (seed)
            StationProtection.edit(
                    () -> {
                        for (String id : java.util.List.of(s.config.lobby(), s.config.showcase()))
                            server.getLevel(LobbyConfig.key(id))
                                    .setBlockAndUpdate(
                                            new BlockPos(1, 128, 1),
                                            net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK
                                                    .defaultBlockState());
                    });
        plan(
                () -> {
                    int labels = 0;
                    for (var world : server.getAllLevels())
                        for (var entity : world.getAllEntities())
                            if (entity.entityTags().contains("dolfin_function_label")) labels++;
                    check(labels == 32, "repeatable layout has exactly 32 owned Spanish labels");
                    for (String id : java.util.List.of(s.config.lobby(), s.config.showcase()))
                        check(
                                server.getLevel(LobbyConfig.key(id))
                                        .getBlockState(new BlockPos(1, 128, 1))
                                        .is(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK),
                                "layout preserves unrelated deck edit " + id);
                });
    }
}
