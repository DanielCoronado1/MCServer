package com.dolfin.lobby;

import com.dolfin.space.KeplerDimension;
import com.dolfin.space.ships.ShipsModule;
import com.google.gson.Gson;

import me.isaiah.multiworld.MultiworldMod;
import me.isaiah.multiworld.Utils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

import java.io.IOException;
import java.nio.file.*;

/** Explicit repeatable functional migration. Only named reserved bounds and owned labels reset. */
public final class StationLayout {
    private static String labelGeneration;

    public static void loadGeneration(MinecraftServer server) throws IOException {
        Path path = marker(server).resolveSibling("layout-generation.txt");
        labelGeneration =
                Files.exists(path)
                        ? java.util.UUID.fromString(Files.readString(path).strip()).toString()
                        : null;
    }

    public static boolean obsoleteLabel(Entity entity) {
        return labelGeneration != null
                && (entity.entityTags().contains("dolfin_station_label")
                        || entity.entityTags().contains("dolfin_function_label"))
                && !entity.entityTags().contains("dolfin_layout_" + labelGeneration);
    }

    private static void loadBounds(ServerLevel world, int x1, int z1, int x2, int z2) {
        for (int x = x1 >> 4; x <= x2 >> 4; x++)
            for (int z = z1 >> 4; z <= z2 >> 4; z++) world.getChunk(x, z);
    }

    public static final BlockPos OUTPOST_PAD = new BlockPos(100000, 200, 100000);
    public static final Vec3 OUTPOST_ARRIVAL = new Vec3(100000.5, 201, 100008.5);

    public static BlockPos arenaCenter(int slot) {
        return new BlockPos(128 + slot * 64, 128, 0);
    }

    private static Path marker(MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("dolfin-lobby/layout-2.ready");
    }

    public static boolean ready(MinecraftServer server) {
        return Files.exists(marker(server));
    }

    public static void install(MinecraftServer server, LobbyConfig config) throws IOException {
        var lobby = server.getLevel(LobbyConfig.key(config.lobby()));
        var show = server.getLevel(LobbyConfig.key(config.showcase()));
        var kepler = server.getLevel(KeplerDimension.LEVEL);
        if (lobby == null || show == null || kepler == null)
            throw new IOException("Ejecuta provisionar; Kepler también debe estar cargado.");
        var sessions = DolfinLobby.sessions();
        if (sessions != null)
            for (var id : sessions.store.ids())
                if (sessions.store.get(id).getStringOr("phase", "").equals("COMBAT")
                        || sessions.store.get(id).getStringOr("phase", "").equals("SPACE"))
                    throw new IOException(
                            "Hay demostraciones activas; ciérralas antes de reconstruir.");
        loadBounds(lobby, -48, -32, 48, 32);
        loadBounds(show, -48, -32, 48, 32);
        loadBounds(kepler, 99984, 99984, 100016, 100016);
        // A generation tag also rejects old display entities whose asynchronous chunk load finishes
        // later.
        labelGeneration = java.util.UUID.randomUUID().toString();
        Files.writeString(
                marker(server).resolveSibling("layout-generation.txt"), labelGeneration + "\n");
        StationProtection.edit(
                () -> {
                    for (var level : new ServerLevel[] {lobby, show}) {
                        for (var e : level.getAllEntities())
                            if (e.entityTags().contains("dolfin_station_label")
                                    || e.entityTags().contains("dolfin_function_label"))
                                e.discard();
                    }
                    StationDecor.deck(lobby, false);
                    StationDecor.deck(show, true);
                    fill(lobby, -22, 129, -3, -18, 133, -3, Blocks.AIR);
                    fill(lobby, 18, 129, -3, 22, 133, -3, Blocks.AIR);
                    label(
                            server,
                            lobby,
                            -20,
                            134,
                            0,
                            "SUPERVIVENCIA\n"
                                    + "Cruza el marco verde. Recuperas tu inventario y lugar"
                                    + " anterior.");
                    label(
                            server,
                            lobby,
                            20,
                            134,
                            0,
                            "EXHIBICIÓN\nCruza el marco azul. Equipo de prueba sin recompensas.");
                    label(
                            server,
                            lobby,
                            0,
                            132,
                            23,
                            "ESTACIÓN DOLFIN\n"
                                    + "/lobby: salida segura en cualquier momento\n"
                                    + "Mundos protegidos · Cada conexión comienza aquí");
                    label(
                            server,
                            show,
                            0,
                            132,
                            25,
                            "EXHIBICIÓN DOLFIN\n"
                                    + "Pulsa un botón para iniciar tu combate privado.\n"
                                    + "/exhibicion lista · /exhibicion volver · /lobby");
                    label(
                            server,
                            show,
                            -35,
                            135,
                            1,
                            "01 · CREEPERS\n"
                                    + "9 variantes · Botones verdes\n"
                                    + "/exhibicion volver reinicia");
                    label(
                            server,
                            show,
                            -7,
                            135,
                            1,
                            "02 · ZOMBIES\n"
                                    + "16 variantes · Botones ámbar\n"
                                    + "Sin progreso de supervivencia");
                    label(
                            server,
                            show,
                            30,
                            135,
                            1,
                            "03 · ESPACIO\n"
                                    + "Kepler original · Ida y vuelta\n"
                                    + "Plataforma con franja amarilla");
                    for (int i = 0; i < ExhibitCatalog.ALL.size(); i++) {
                        boolean creeper = i < 9;
                        int j = creeper ? i : i - 9;
                        int x = (creeper ? -44 : -16) + (j % 4) * 6;
                        int z = -25 + (j / 4) * 7;
                        show.setBlockAndUpdate(
                                new BlockPos(x, 129, z),
                                Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                        show.setBlockAndUpdate(
                                new BlockPos(x, 130, z),
                                Blocks.STONE_BUTTON
                                        .defaultBlockState()
                                        .setValue(
                                                net.minecraft.world.level.block.state.properties
                                                        .BlockStateProperties.ATTACH_FACE,
                                                net.minecraft.world.level.block.state.properties
                                                        .AttachFace.FLOOR));
                        var e = ExhibitCatalog.ALL.get(i);
                        label(
                                server,
                                show,
                                x,
                                132,
                                z,
                                e.name()
                                        + "\n"
                                        + (i + 1)
                                        + " · Pulsa para combatir\n"
                                        + e.description());
                    }
                    for (int x = 28; x <= 32; x++)
                        for (int z = -16; z <= -12; z++)
                            show.setBlockAndUpdate(
                                    new BlockPos(x, 128, z),
                                    ShipsModule.PLATFORM.defaultBlockState());
                    show.setBlockAndUpdate(
                            new BlockPos(30, 129, -5), Blocks.SMOOTH_QUARTZ.defaultBlockState());
                    show.setBlockAndUpdate(
                            new BlockPos(30, 130, -5),
                            Blocks.STONE_BUTTON
                                    .defaultBlockState()
                                    .setValue(
                                            net.minecraft.world.level.block.state.properties
                                                    .BlockStateProperties.ATTACH_FACE,
                                            net.minecraft.world.level.block.state.properties
                                                    .AttachFace.FLOOR));
                    label(
                            server,
                            show,
                            30,
                            133,
                            -3,
                            "ESPACIO · VIAJE REAL A KEPLER\n"
                                    + "Pulsa para preparar nave y oxígeno.\n"
                                    + "Cabina: despegar / regresar · /lobby cancelar\n"
                                    + "Una nave de demostración a la vez.");
                    label(server, show, 0, 132, 30, "SALIDA AL LOBBY\nCamina hasta el borde azul");
                    fill(kepler, 99984, 199, 99984, 100016, 199, 100016, Blocks.BEDROCK);
                    fill(kepler, 99984, 200, 99984, 100016, 200, 100016, Blocks.SMOOTH_QUARTZ);
                    fill(kepler, 99984, 201, 99984, 100016, 235, 100016, Blocks.AIR);
                    for (int x = 99984; x <= 100016; x++)
                        for (int z = 99984; z <= 100016; z++)
                            if (x == 99984 || x == 100016 || z == 99984 || z == 100016)
                                fill(kepler, x, 201, z, x, 205, z, Blocks.GLASS);
                    for (int x = 99988; x <= 100012; x += 6)
                        for (int z = 99988; z <= 100012; z += 6)
                            kepler.setBlockAndUpdate(
                                    new BlockPos(x, 200, z),
                                    Blocks.SEA_LANTERN.defaultBlockState());
                    StationDecor.outpost(kepler);
                    for (var e : kepler.getAllEntities())
                        if (e.entityTags().contains("dolfin_outpost_label")) e.discard();
                    label(
                            server,
                            kepler,
                            100000,
                            204,
                            100009,
                            "PUESTO DOLFIN · KEPLER ORIGINAL\n"
                                    + "Gravedad y oxígeno reales. Tu tanque se consume.\n"
                                    + "Vuelve a la nave: cabina → despegar para regresar.\n"
                                    + "/exhibicion despegar · /lobby salida segura");
                });
        var nativeConfig =
                Utils.getConfigOrNull(net.minecraft.resources.Identifier.parse(config.showcase()));
        nativeConfig.set("difficulty", "NORMAL");
        nativeConfig.save();
        MultiworldMod.get_world_creator().set_difficulty(config.showcase(), Difficulty.NORMAL);
        server.saveEverything(false, true, true);
        Files.writeString(marker(server), "layoutVersion=3\n");
    }

    public static int button(BlockPos pos) {
        for (int i = 0; i < ExhibitCatalog.ALL.size(); i++) {
            boolean c = i < 9;
            int j = c ? i : i - 9;
            if (pos.equals(new BlockPos((c ? -44 : -16) + (j % 4) * 6, 130, -25 + (j / 4) * 7)))
                return i;
        }
        return -1;
    }

    public static void arena(MinecraftServer server, ServerLevel world, int slot) {
        clearArena(server, slot);
        var c = arenaCenter(slot);
        StationProtection.edit(
                () -> {
                    fill(world, c.getX() - 12, 127, -12, c.getX() + 12, 127, 12, Blocks.BEDROCK);
                    fill(
                            world,
                            c.getX() - 12,
                            128,
                            -12,
                            c.getX() + 12,
                            128,
                            12,
                            Blocks.SMOOTH_QUARTZ);
                    fill(world, c.getX() - 12, 129, -12, c.getX() + 12, 140, 12, Blocks.AIR);
                    for (int x = c.getX() - 12; x <= c.getX() + 12; x++)
                        for (int z = -12; z <= 12; z++)
                            if (x == c.getX() - 12 || x == c.getX() + 12 || z == -12 || z == 12)
                                fill(world, x, 129, z, x, 140, z, Blocks.BEDROCK);
                    fill(
                            world,
                            c.getX() - 12,
                            141,
                            -12,
                            c.getX() + 12,
                            141,
                            12,
                            Blocks.TINTED_GLASS);
                    fill(world, c.getX() - 3, 128, 1, c.getX() + 3, 128, 5, Blocks.OAK_PLANKS);
                    fill(world, c.getX() + 4, 121, -8, c.getX() + 10, 128, -2, Blocks.BEDROCK);
                    fill(
                            world,
                            c.getX() + 5,
                            122,
                            -7,
                            c.getX() + 9,
                            128,
                            -3,
                            Blocks.WATER); // deep basin on bedrock liner
                    fill(world, c.getX() + 4, 121, -8, c.getX() + 10, 121, -2, Blocks.BEDROCK);
                    for (int x = c.getX() - 8; x <= c.getX() - 4; x++) {
                        world.setBlockAndUpdate(
                                new BlockPos(x, 128, -2), Blocks.FARMLAND.defaultBlockState());
                        world.setBlockAndUpdate(
                                new BlockPos(x, 129, -2),
                                Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
                    }
                    StationDecor.arena(world, c.getX());
                    world.setBlockAndUpdate(
                            c.offset(-7, 0, 7), Blocks.SEA_LANTERN.defaultBlockState());
                });
    }

    public static void resetArena(MinecraftServer server, int slot) {
        var sessions = DolfinLobby.sessions();
        if (sessions == null) return;
        var world = server.getLevel(LobbyConfig.key(sessions.config.showcase()));
        if (world != null) arena(server, world, slot);
    }

    private static void clearArena(MinecraftServer server, int slot) {
        var sessions = DolfinLobby.sessions();
        if (sessions == null) return;
        var level = server.getLevel(LobbyConfig.key(sessions.config.showcase()));
        if (level == null) return;
        var c = arenaCenter(slot);
        loadBounds(level, c.getX() - 16, -16, c.getX() + 16, 16);
        for (var e : level.getEntities((Entity) null, new AABB(c).inflate(18, 30, 18))) {
            if (!(e instanceof net.minecraft.server.level.ServerPlayer)) {
                if (e instanceof net.minecraft.world.entity.monster.Creeper creeper) {
                    creeper.setSwellDir(-1);
                    ((com.dolfin.creepers.mixin.CreeperAccessor) creeper).dolfin$setHinchado(0);
                }
                e.discard();
            }
        }
    }

    public static void fill(ServerLevel w, int x, int y, int z, int x2, int y2, int z2, Block b) {
        for (var p : BlockPos.betweenClosed(x, y, z, x2, y2, z2))
            w.setBlockAndUpdate(p, b.defaultBlockState());
    }

    private static void label(
            MinecraftServer server, ServerLevel world, int x, int y, int z, String text) {
        server.getCommands()
                .performPrefixedCommand(
                        server.createCommandSourceStack(),
                        "execute in "
                                + world.dimension().identifier()
                                + " run summon text_display "
                                + x
                                + " "
                                + y
                                + " "
                                + z
                                + " {Tags:[\"dolfin_function_label\",\"dolfin_layout_"
                                + labelGeneration
                                + "\""
                                + (KeplerDimension.isKepler(world.dimension())
                                        ? ",\"dolfin_outpost_label\""
                                        : "")
                                + "],billboard:\"center\",text:"
                                + new Gson().toJson(text)
                                + ",line_width:180,background:-1442840576,default_background:0b,shadow:1b,brightness:{block:15,sky:15},view_range:0.65f}");
    }
}
