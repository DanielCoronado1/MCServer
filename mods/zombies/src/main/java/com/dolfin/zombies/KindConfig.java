package com.dolfin.zombies;

import com.dolfin.zombies.kinds.Abilities;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Typed, range-clamped view of the settings every zombie kind shares: stats, spawn rules, blood
 * moon tuning, xp and loot. Ability blocks are read separately through {@code CombatParams}.
 */
public record KindConfig(
        boolean activo,
        double vida,
        double danio,
        double velocidad,
        double armadura,
        double escala,
        int peso,
        int minGrupo,
        int maxGrupo,
        List<String> biomas,
        List<String> biomasExcluidos,
        int minAlturaY,
        int maxAlturaY,
        int luzMax,
        boolean quemaConSol,
        double bloodMoonPesoExtra,
        double bloodMoonBonusStats,
        int xp,
        String loot) {

    public static KindConfig from(JsonObject o) {
        int[] grupo = pair(o, "grupo", 1, 1);
        int[] alturaY = pair(o, "alturaY", -64, 320);
        JsonObject bm = o != null && o.get("lunaDeSangre") instanceof JsonObject b ? b : null;
        return new KindConfig(
                Abilities.bool(o, "activo", true),
                Abilities.num(o, "vida", 20, 0.01, 1024),
                Abilities.num(o, "danio", 3, 0, 2048),
                Abilities.num(o, "velocidad", 1, 0.01, 10),
                Abilities.num(o, "armadura", 2, 0, 30),
                Abilities.num(o, "escala", 1, 0.01, 16),
                Abilities.integer(o, "peso", 10, 0, 1000000),
                grupo[0],
                grupo[1],
                Abilities.stringList(o, "biomas"),
                Abilities.stringList(o, "biomasExcluidos"),
                alturaY[0],
                alturaY[1],
                Abilities.integer(o, "luzMax", 0, 0, 15),
                Abilities.bool(o, "quemaConSol", true),
                Abilities.num(bm, "pesoExtra", 3, 0, 1000),
                Abilities.num(bm, "bonusStats", 1.25, 0.01, 1000),
                Abilities.integer(o, "xp", 5, 0, 100000),
                Abilities.str(o, "loot", "dolfinzombies:entities/zombie"));
    }

    /** Entity attributes derived from these settings; speed is scaled to vanilla units. */
    public ZombieStats stats() {
        return new ZombieStats(vida, danio, .23 * velocidad, armadura, escala);
    }

    /** Copy with health and damage multiplied by the blood moon {@code bonus}. */
    public KindConfig withBloodMoonBonus(double bonus) {
        return new KindConfig(
                activo,
                vida * bonus,
                danio * bonus,
                velocidad,
                armadura,
                escala,
                peso,
                minGrupo,
                maxGrupo,
                biomas,
                biomasExcluidos,
                minAlturaY,
                maxAlturaY,
                luzMax,
                quemaConSol,
                bloodMoonPesoExtra,
                bloodMoonBonusStats,
                xp,
                loot);
    }

    // [min, max] pairs such as "grupo": [1, 3]; malformed arrays keep the defaults.
    private static int[] pair(JsonObject o, String key, int min, int max) {
        if (o != null && o.get(key) instanceof JsonArray a && a.size() >= 2)
            return new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()};
        return new int[] {min, max};
    }
}
