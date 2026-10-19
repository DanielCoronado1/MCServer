package com.dolfin.zombies;

import com.google.gson.JsonObject;

/** Pure config-to-attribute mapping, also used by entity application and regression tests. */
public record ZombieStats(
        double health, double damage, double movementSpeed, double armor, double scale) {
    public static ZombieStats from(JsonObject settings) {
        return new ZombieStats(
                settings.get("vida").getAsDouble(),
                settings.get("danio").getAsDouble(),
                .23 * settings.get("velocidad").getAsDouble(),
                settings.get("armadura").getAsDouble(),
                settings.get("escala").getAsDouble());
    }
}
