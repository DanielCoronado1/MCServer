package com.dolfin.lobby;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.isaiah.multiworld.MultiworldMod;
import me.isaiah.multiworld.Utils;
import me.isaiah.multiworld.command.CreateCommand;
import me.isaiah.multiworld.command.SetspawnCommand;

import multiworld.api.WorldFolderMode;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Adds structures only when this invocation created the world. Never rebuilds existing maps. */
public final class WorldProvisioner {
    public static final int FLOOR_Y = 128;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final MinecraftServer server;
    private final LobbyConfig config;
    private final Path manifest;

    public WorldProvisioner(MinecraftServer server, LobbyConfig config) {
        this.server = server;
        this.config = config;
        this.manifest =
                server.getWorldPath(LevelResource.ROOT).resolve("dolfin-lobby/provisioning.json");
    }

    public void provision() throws IOException {
        JsonObject state = readState();
        provisionWorld(config.lobby(), false, state);
        provisionWorld(config.showcase(), true, state);
    }

    private JsonObject readState() throws IOException {
        if (!Files.exists(manifest)) {
            JsonObject state = new JsonObject();
            state.addProperty("schemaVersion", 1);
            state.add("worlds", new JsonObject());
            return state;
        }
        JsonObject state = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        if (state.get("schemaVersion").getAsInt() != 1 || !state.has("worlds")) {
            throw new IOException(
                    "Estado de construcción incompatible; migración manual necesaria");
        }
        return state;
    }

    private void provisionWorld(String id, boolean showcase, JsonObject state) throws IOException {
        ServerLevel level = server.getLevel(LobbyConfig.key(id));
        JsonObject worlds = state.getAsJsonObject("worlds");
        if (level != null) {
            // Even an unrecognized existing map is preserved rather than silently adopted/rebuilt.
            DolfinLobby.LOGGER.info(
                    "event=world_preserved world={} recorded={}", id, worlds.has(id));
            return;
        }
        if (worlds.has(id)) {
            throw new IOException(
                    "Mundo registrado pero no cargado: "
                            + id
                            + "; comprueba Multiworld antes de continuar");
        }
        Identifier identifier = Identifier.parse(id);
        Path existingData =
                server.getWorldPath(LevelResource.ROOT)
                        .resolve("dimensions")
                        .resolve(identifier.getNamespace())
                        .resolve(identifier.getPath());
        if (Files.exists(existingData)) {
            throw new IOException(
                    "Datos existentes de mundo no cargado: " + id + "; no se reemplazan");
        }
        // The pinned release rejects /mw create from console; use its public creation API.
        MultiworldMod.createConfigAndWorld(
                id,
                "NORMAL",
                CreateCommand.get_dim_id("NORMAL"),
                CreateCommand.get_chunk_gen(server, "FLAT"),
                Difficulty.PEACEFUL,
                config.seed(),
                "FLAT",
                WorldFolderMode.VANILLA);
        level = server.getLevel(LobbyConfig.key(id));
        if (level == null) throw new IOException("Multiworld no cargó " + id);
        ServerLevel created = level;
        StationProtection.edit(() -> build(created, id, showcase));
        SetspawnCommand.setSpawn(level, new BlockPos(0, FLOOR_Y + 1, 20));
        var worldConfig = Utils.getConfigOrNull(identifier);
        if (worldConfig == null)
            throw new IOException("Multiworld no guardó la configuración de " + id);
        worldConfig.set("difficulty", "PEACEFUL");
        worldConfig.save();
        MultiworldMod.get_world_creator().set_difficulty(id, Difficulty.PEACEFUL);
        // Flush actual chunk data before marking construction complete.
        server.saveEverything(false, true, true);
        worlds.addProperty(id, 1);
        Files.createDirectories(manifest.getParent());
        Path temporary = manifest.resolveSibling("provisioning.json.tmp");
        Files.writeString(temporary, GSON.toJson(state) + "\n");
        try {
            Files.move(
                    temporary,
                    manifest,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, manifest, StandardCopyOption.REPLACE_EXISTING);
        }
        DolfinLobby.LOGGER.info("event=world_provisioned world={} layoutVersion=1", id);
    }

    private void build(ServerLevel level, String id, boolean showcase) {
        // A suspended, enclosed development deck keeps terrain and survival untouched.
        fill(level, -48, 128, -32, 48, 128, 32, Blocks.SMOOTH_QUARTZ);
        fill(level, -48, 129, -32, 48, 135, -32, Blocks.TINTED_GLASS);
        fill(level, -48, 129, 32, 48, 135, 32, Blocks.TINTED_GLASS);
        fill(level, -48, 129, -32, -48, 135, 32, Blocks.TINTED_GLASS);
        fill(level, 48, 129, -32, 48, 135, 32, Blocks.TINTED_GLASS);
        for (int x : new int[] {-40, -20, 0, 20, 40}) {
            for (int z : new int[] {-24, 0, 24}) {
                level.setBlockAndUpdate(
                        new BlockPos(x, 128, z), Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
        if (showcase) {
            reserve(level, -30, "Creepers", Blocks.CONCRETE.pick(DyeColor.LIME), id);
            reserve(level, 0, "Zombies", Blocks.CONCRETE.pick(DyeColor.GREEN), id);
            reserve(level, 30, "Space", Blocks.CONCRETE.pick(DyeColor.BLUE), id);
            label(id, 0, 132, 25, "Exhibición Dolfin", "Demostraciones cerradas");
        } else {
            frame(level, -20, Blocks.CONCRETE.pick(DyeColor.GREEN));
            frame(level, 20, Blocks.CONCRETE.pick(DyeColor.BLUE));
            label(id, -20, 132, -1, "Supervivencia", "Acceso cerrado durante obras");
            label(id, 20, 132, -1, "Exhibición de mods", "Acceso cerrado durante obras");
            label(id, 0, 132, 24, "Estación Dolfin", "Punto de llegada");
        }
    }

    private void reserve(
            ServerLevel level,
            int x,
            String title,
            net.minecraft.world.level.block.Block accent,
            String id) {
        fill(level, x - 10, 128, -24, x + 10, 128, -4, accent);
        label(id, x, 132, -3, "Ala " + title, "Área reservada · sin actividad");
    }

    private void frame(ServerLevel level, int x, net.minecraft.world.level.block.Block accent) {
        fill(level, x - 3, 129, -3, x + 3, 134, -3, accent);
        fill(level, x - 2, 129, -3, x + 2, 133, -3, Blocks.TINTED_GLASS);
    }

    private void label(String id, int x, int y, int z, String title, String subtitle) {
        // Vanilla displays need no client mod; JSON escaping is delegated to Gson.
        String text = GSON.toJson(title + "\n" + subtitle);
        command(
                "execute in "
                        + id
                        + " run summon minecraft:text_display "
                        + x
                        + " "
                        + y
                        + " "
                        + z
                        + " {Tags:[\"dolfin_station_label\"],billboard:\"center\",text:"
                        + text
                        + ",line_width:280}");
    }

    private static void fill(
            ServerLevel level,
            int x1,
            int y1,
            int z1,
            int x2,
            int y2,
            int z2,
            net.minecraft.world.level.block.Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) {
            level.setBlockAndUpdate(pos, block.defaultBlockState());
        }
    }

    private void command(String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }
}
