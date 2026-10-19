package com.dolfin.zombies.kinds;

import com.dolfin.zombies.ZombieKind;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Extra {@code habilidades} keys owned by issue #5, merged into {@link ZombieKind#defaults()} for the
 * combat kinds only. Keeping them here avoids touching the other kinds' lines in the shared enum.
 */
public final class CombatDefaults {
    private CombatDefaults() {}

    /** @param abilities the kind's {@code habilidades} object (mutated). */
    public static void extend(ZombieKind kind, JsonObject abilities) {
        switch (kind) {
            case HAMBRIENTO -> ability(abilities, "hambre").addProperty("radioComida", 10);
            case CONGELADO -> ability(abilities, "congelar").addProperty("congelacionTicks", 200);
            case BILIOSO -> {
                JsonObject a = ability(abilities, "nube");
                a.addProperty("nauseaSegundos", 6);
                a.addProperty("venenoSegundos", 2);
                a.addProperty("venenoNivel", 1);
            }
            case ABISMO -> {
                JsonObject a = ability(abilities, "agarre");
                a.addProperty("danioParaSoltar", 4);
                a.addProperty("alcanceAgarre", 1.8);
                a.addProperty("nivelFatiga", 1);
            }
            case JINETE -> ability(abilities, "montura").add("monturas", JsonParser.parseString("{\"zombie_horse\":60,\"spider\":40}"));
            case ENANO -> {
                JsonObject a = ability(abilities, "cuerpoPequeno");
                a.addProperty("anchoHitbox", 0.5);
                a.addProperty("altoHitbox", 0.9);
            }
            default -> { }
        }
    }

    private static JsonObject ability(JsonObject abilities, String id) {
        return abilities.getAsJsonObject(id);
    }
}
