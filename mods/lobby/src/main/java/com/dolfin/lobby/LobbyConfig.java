package com.dolfin.lobby;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Stable world identity: changing identifiers is an explicit operator migration. */
public record LobbyConfig(int schemaVersion, String lobby, String showcase, long seed) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static LobbyConfig defaults() {
        return new LobbyConfig(1, "dolfin:lobby", "dolfin:showcase", 7340291L);
    }

    public static LobbyConfig load(Path file) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(defaults()) + "\n");
        }
        LobbyConfig config = GSON.fromJson(Files.readString(file), LobbyConfig.class);
        if (config == null || config.schemaVersion != 1) {
            throw new IllegalArgumentException("Versión de configuración de lobby no compatible");
        }
        validateId(config.lobby);
        validateId(config.showcase);
        if (config.lobby.equals(config.showcase)) {
            throw new IllegalArgumentException("Lobby y exhibición necesitan mundos diferentes");
        }
        return config;
    }

    private static void validateId(String id) {
        Identifier parsed = Identifier.parse(id);
        if (!parsed.getNamespace().equals("dolfin")
                || !parsed.getPath().matches("[a-z0-9_-]+")
                || !parsed.toString().equals(id)) {
            throw new IllegalArgumentException(
                    "Los mundos de desarrollo deben usar dolfin:<nombre>");
        }
    }

    public static ResourceKey<Level> key(String id) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
    }
}
