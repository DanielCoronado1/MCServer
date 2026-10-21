package com.dolfin.lobby;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Synchronous write-ahead survival escrow. A snapshot remains authoritative until player save. */
public final class SessionStore {
    private final Path directory;
    private final Map<UUID, CompoundTag> sessions = new HashMap<>();

    public SessionStore(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".nbt")).toList()) {
                var state = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                if (state.getIntOr("schema", 0) != 1)
                    throw new IOException("Sesión incompatible: " + path);
                sessions.put(
                        UUID.fromString(path.getFileName().toString().replace(".nbt", "")), state);
            }
        }
    }

    public boolean contains(UUID id) {
        return sessions.containsKey(id);
    }

    public CompoundTag get(UUID id) {
        return sessions.get(id);
    }

    public Set<UUID> ids() {
        return Set.copyOf(sessions.keySet());
    }

    public void capture(ServerPlayer player) throws IOException {
        if (contains(player.getUUID())) return;
        // Return cursor and crafting contents to the survival inventory before taking escrow.
        player.closeContainer();
        player.inventoryMenu.removed(player);
        var output =
                TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, player.registryAccess());
        player.saveWithoutId(output);
        var state = new CompoundTag();
        state.putInt("schema", 1);
        state.put("survival", output.buildResult());
        state.putString("dimension", player.level().dimension().identifier().toString());
        state.putDouble("x", player.getX());
        state.putDouble("y", player.getY());
        state.putDouble("z", player.getZ());
        state.putFloat("yaw", player.getYRot());
        state.putFloat("pitch", player.getXRot());
        state.putString("phase", "LOBBY");
        put(player.getUUID(), state);
    }

    public void put(UUID id, CompoundTag state) throws IOException {
        Path temporary = directory.resolve(id + ".tmp");
        NbtIo.writeCompressed(state, temporary);
        try {
            Files.move(
                    temporary,
                    directory.resolve(id + ".nbt"),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(
                    temporary, directory.resolve(id + ".nbt"), StandardCopyOption.REPLACE_EXISTING);
        }
        sessions.put(id, state);
    }

    public void finish(UUID id) throws IOException {
        Files.deleteIfExists(directory.resolve(id + ".nbt"));
        sessions.remove(id);
    }
}
