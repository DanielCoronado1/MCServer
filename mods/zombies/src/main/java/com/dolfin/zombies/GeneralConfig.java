package com.dolfin.zombies;

import com.dolfin.zombies.kinds.Abilities;
import com.google.gson.JsonObject;

import java.util.List;

/** Typed configuration for general.json settings. */
public record GeneralConfig(
        boolean activo,
        int maxZombiesEspecialesPorJugador,
        boolean respetarClaims,
        List<String> mundosPermitidos,
        boolean mensajesChat,
        BloodMoonOptions lunaDeSangre) {

    public static GeneralConfig from(JsonObject general) {
        var bloodMoon = BloodMoonOptions.from(general);
        List<String> worlds =
                Abilities.stringList(general, "mundosPermitidos", List.of("minecraft:overworld"));
        return new GeneralConfig(
                Abilities.bool(general, "activo", true),
                Abilities.integer(general, "maxZombiesEspecialesPorJugador", 12, 0, 1000),
                Abilities.bool(general, "respetarClaims", true),
                worlds,
                Abilities.bool(general, "mensajesChat", true),
                bloodMoon);
    }
}
