package com.dolfin.zombies;

import com.google.gson.JsonObject;

/**
 * Typed view of {@code general.json → lunaDeSangre} (already validated by ZombieConfig) plus the
 * pure spawn arithmetic.
 */
public record BloodMoonOptions(
        boolean enabled,
        double chance,
        int every,
        double multiplier,
        boolean onlySpecials,
        boolean warn,
        boolean redSky,
        boolean sleepBlocked,
        double xpMultiplier,
        boolean chatMessages,
        JsonObject raw) {
    public static BloodMoonOptions from(JsonObject general) {
        var m = general.getAsJsonObject("lunaDeSangre");
        return new BloodMoonOptions(
                general.get("activo").getAsBoolean() && m.get("activo").getAsBoolean(),
                m.get("probabilidadPorNoche").getAsDouble(),
                m.get("cadaNNoches").getAsInt(),
                m.get("multiplicadorSpawns").getAsDouble(),
                m.get("soloEspeciales").getAsBoolean(),
                m.get("aviso").getAsBoolean(),
                m.get("cieloRojo").getAsBoolean(),
                m.get("dormirBloqueado").getAsBoolean(),
                m.get("recompensaXpExtra").getAsDouble(),
                general.get("mensajesChat").getAsBoolean(),
                m.deepCopy());
    }

    /** Monster cap (global and per-player) during the blood moon. */
    public static int scaledCap(int base, double multiplier) {
        return (int) Math.ceil(base * Math.max(1, multiplier));
    }

    /**
     * Extra spawn passes per chunk tick so attempt frequency averages {@code multiplier}x; roll in
     * [0,1).
     */
    public static int extraPasses(double multiplier, double roll) {
        double extra = Math.max(0, multiplier - 1);
        int whole = (int) extra;
        return whole + (roll < extra - whole ? 1 : 0);
    }

    /** Fire long enough for 1 dmg/s to finish a leftover special at dawn (water still saves it). */
    public static int burnSeconds(float health) {
        return (int) Math.min(120, Math.max(8, Math.ceil(health) + 2));
    }

    public static int experience(int base, double multiplier, boolean bloodMoon) {
        return (int) Math.round(base * (bloodMoon ? multiplier : 1));
    }

    @Override
    public String toString() {
        return "enabled="
                + enabled
                + " every="
                + every
                + " chance="
                + chance
                + " multiplier="
                + multiplier
                + " onlySpecials="
                + onlySpecials
                + " warn="
                + warn
                + " redSky="
                + redSky
                + " sleepBlocked="
                + sleepBlocked
                + " xp="
                + xpMultiplier;
    }
}
