package com.dolfin.zombies.kinds;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Weighted choice over a {@code {"id": weight}} JSON object (insertion order is preserved). */
public final class WeightedPick {
    private WeightedPick() {}

    public static Map<String, Double> parse(JsonObject o) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (o == null) return out;
        for (var e : o.entrySet()) {
            JsonElement v = e.getValue();
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                double w = v.getAsDouble();
                if (w > 0 && !Double.isInfinite(w)) out.put(e.getKey(), w);
            }
        }
        return out;
    }

    /** {@code roll} in [0,1). Returns {@code null} when no entry has a positive weight. */
    public static String pick(Map<String, Double> weights, double roll) {
        double total = 0;
        for (double w : weights.values()) total += w;
        if (total <= 0) return null;
        double target = Math.max(0, Math.min(0.999999999, roll)) * total, acc = 0;
        String last = null;
        for (var e : weights.entrySet()) {
            acc += e.getValue();
            last = e.getKey();
            if (target < acc) return last;
        }
        return last;
    }
}
