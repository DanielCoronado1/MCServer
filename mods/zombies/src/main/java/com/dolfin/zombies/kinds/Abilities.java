package com.dolfin.zombies.kinds;

import com.dolfin.zombies.ConfiguredZombie;
import com.dolfin.zombies.DolfinZombies;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Defensive readers for an ability's JSON block plus the shared structured-debug helper of issue
 * #5.
 */
public final class Abilities {
    private Abilities() {}

    /** The ability object inside {@code habilidades}, or an empty object when absent. */
    public static JsonObject of(JsonObject settings, String ability) {
        if (settings != null
                && settings.has("habilidades")
                && settings.get("habilidades").isJsonObject()) {
            JsonElement e = settings.getAsJsonObject("habilidades").get(ability);
            if (e != null && e.isJsonObject()) return e.getAsJsonObject();
        }
        return new JsonObject();
    }

    public static double num(JsonObject o, String key, double def, double min, double max) {
        double v = def;
        JsonElement e = o == null ? null : o.get(key);
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber())
            v = e.getAsDouble();
        if (Double.isNaN(v) || Double.isInfinite(v)) v = def;
        return Math.max(min, Math.min(max, v));
    }

    public static int integer(JsonObject o, String key, int def, int min, int max) {
        return (int) Math.round(num(o, key, def, min, max));
    }

    public static boolean bool(JsonObject o, String key, boolean def) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()
                ? e.getAsBoolean()
                : def;
    }

    public static String str(JsonObject o, String key, String def) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()
                ? e.getAsString()
                : def;
    }

    /** DEBUG event with kind/uuid prefilled: {@code event=<name> id=<kind> uuid=<uuid> <fmt>}. */
    public static void debug(ConfiguredZombie z, String event, String fmt, Object... args) {
        var log = DolfinZombies.LOGGER;
        if (!log.isDebugEnabled()) return;
        Object[] all = new Object[args.length + 2];
        all[0] = z.kind().id();
        all[1] = z.getUUID();
        System.arraycopy(args, 0, all, 2, args.length);
        log.debug("event=" + event + " id={} uuid={} " + fmt, all);
    }
}
