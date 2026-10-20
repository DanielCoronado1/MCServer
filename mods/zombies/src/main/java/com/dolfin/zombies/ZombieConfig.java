package com.dolfin.zombies;

import com.google.gson.*;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/**
 * Whole snapshots are swapped on the server thread; malformed fields never discard valid siblings.
 */
public final class ZombieConfig {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path root;
    private final Consumer<String> warnings;
    private Snapshot snapshot;
    private long generation;

    public record Problem(String file, String field, String reason) {
        public String toString() {
            return file + " field=" + field + ": " + reason + "; using default";
        }
    }

    public record Report(
            long generation, int files, int created, List<Problem> problems, long millis) {
        public String summary() {
            return "generation="
                    + generation
                    + " files="
                    + files
                    + " created="
                    + created
                    + " errors="
                    + problems.size()
                    + " durationMs="
                    + millis;
        }
    }

    public static final class Snapshot {
        private final JsonObject general;
        private final Map<ZombieKind, JsonObject> zombies;
        private final long generation;
        private final GeneralConfig generalConfig;
        private final Map<ZombieKind, KindConfig> kindConfigs;

        Snapshot(JsonObject general, Map<ZombieKind, JsonObject> zombies, long generation) {
            this.general = general.deepCopy();
            this.zombies = new EnumMap<>(ZombieKind.class);
            zombies.forEach((k, v) -> this.zombies.put(k, v.deepCopy()));
            this.generation = generation;
            this.generalConfig = GeneralConfig.from(this.general);
            this.kindConfigs = new EnumMap<>(ZombieKind.class);
            this.zombies.forEach((k, v) -> this.kindConfigs.put(k, KindConfig.from(v)));
        }

        public JsonObject general() {
            return general.deepCopy();
        }

        public JsonObject zombie(ZombieKind kind) {
            return zombies.get(kind).deepCopy();
        }

        public long generation() {
            return generation;
        }

        public GeneralConfig generalConfig() {
            return generalConfig;
        }

        public KindConfig kindConfig(ZombieKind kind) {
            return kindConfigs.get(kind);
        }

        /**
         * Reads {@code kind}'s settings through a typed parser such as {@code
         * CombatParams.Mimo::from}, without copying the JSON.
         */
        public <T> T params(ZombieKind kind, java.util.function.Function<JsonObject, T> parser) {
            return parser.apply(zombies.get(kind));
        }

        public boolean enabled(ZombieKind kind) {
            return generalConfig.activo() && kindConfigs.get(kind).activo();
        }

        public boolean abilityEnabled(ZombieKind kind, String ability) {
            var a = zombies.get(kind).getAsJsonObject("habilidades");
            return enabled(kind)
                    && a.has(ability)
                    && a.get(ability).isJsonObject()
                    && a.getAsJsonObject(ability).has("activo")
                    && a.getAsJsonObject(ability).get("activo").getAsBoolean();
        }
    }

    public ZombieConfig(Path root, Consumer<String> warnings) {
        this.root = root;
        this.warnings = warnings;
        reload();
    }

    public Snapshot current() {
        return snapshot;
    }

    public Path root() {
        return root;
    }

    public JsonObject validateSaved(ZombieKind kind, String raw) {
        List<Problem> errors = new ArrayList<>();
        try {
            JsonObject result =
                    validate(
                            JsonParser.parseString(raw).getAsJsonObject(),
                            kind.defaults(),
                            "entity:" + kind.id(),
                            "",
                            errors);
            errors.forEach(p -> warnings.accept("event=entity_settings_fallback " + p));
            return result;
        } catch (RuntimeException e) {
            warnings.accept("event=entity_settings_invalid id=" + kind.id() + " reason=" + e);
            return kind.defaults();
        }
    }

    public static JsonObject generalDefaults() {
        return JsonParser.parseString(
                        """
                        {"activo":true,"maxZombiesEspecialesPorJugador":12,"respetarClaims":true,
                        "mundosPermitidos":["minecraft:overworld"],"mensajesChat":true,"lunaDeSangre":{"activo":true,"probabilidadPorNoche":0.10,"cadaNNoches":0,"multiplicadorSpawns":2.5,"soloEspeciales":false,"aviso":true,"cieloRojo":true,"dormirBloqueado":true,"recompensaXpExtra":1.5}}
                        """)
                .getAsJsonObject();
    }

    public Report reload() {
        long start = System.nanoTime();
        List<Problem> errors = new ArrayList<>();
        int[] created = {0};
        JsonObject general = load("general.json", generalDefaults(), errors, created);
        Map<ZombieKind, JsonObject> variants = new EnumMap<>(ZombieKind.class);
        for (var kind : ZombieKind.values())
            variants.put(
                    kind, load("zombies/" + kind.id() + ".json", kind.defaults(), errors, created));
        snapshot = new Snapshot(general, variants, ++generation);
        errors.forEach(
                p -> warnings.accept("event=config_invalid generation=" + generation + " " + p));
        Report report =
                new Report(
                        generation,
                        17,
                        created[0],
                        List.copyOf(errors),
                        (System.nanoTime() - start) / 1_000_000);
        // Diagnostic artifact contains effective values and every fallback, without overwriting
        // administrator input.
        JsonObject artifact = new JsonObject();
        artifact.addProperty("timestamp", Instant.now().toString());
        artifact.addProperty("summary", report.summary());
        artifact.add("problems", JSON.toJsonTree(errors));
        artifact.add("general", general.deepCopy());
        JsonObject effective = new JsonObject();
        variants.forEach((k, v) -> effective.add(k.id(), v));
        artifact.add("zombies", effective);
        try {
            write(root.resolve("diagnostics/last-reload.json"), artifact);
        } catch (IOException e) {
            warnings.accept("event=diagnostics_write_failed path=" + root + " reason=" + e);
        }
        return report;
    }

    private JsonObject load(String file, JsonObject defaults, List<Problem> errors, int[] created) {
        Path path = root.resolve(file);
        try {
            if (!Files.exists(path)) {
                write(path, defaults);
                created[0]++;
                return defaults.deepCopy();
            }
            JsonElement input = JsonParser.parseString(Files.readString(path));
            if (!input.isJsonObject()) throw new IllegalArgumentException("expected JSON object");
            return validate(input.getAsJsonObject(), defaults, file, "", errors);
        } catch (IOException | RuntimeException e) {
            errors.add(new Problem(file, "$", e.toString()));
            return defaults.deepCopy();
        }
    }

    private JsonObject validate(
            JsonObject input,
            JsonObject defaults,
            String file,
            String prefix,
            List<Problem> errors) {
        JsonObject out = input.deepCopy();
        for (var entry : defaults.entrySet()) {
            String key = entry.getKey(), field = prefix + key;
            JsonElement fallback = entry.getValue();
            JsonElement value = input.get(key);
            if (value == null) {
                out.add(key, fallback.deepCopy());
                continue;
            }
            try {
                if (fallback.isJsonObject()) {
                    if (!value.isJsonObject())
                        throw new IllegalArgumentException("expected object");
                    out.add(
                            key,
                            validate(
                                    value.getAsJsonObject(),
                                    fallback.getAsJsonObject(),
                                    file,
                                    field + ".",
                                    errors));
                } else if (fallback.isJsonArray()) validateArray(key, value);
                else if (fallback.getAsJsonPrimitive().isBoolean()) {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                        throw new IllegalArgumentException("expected boolean");
                } else if (fallback.getAsJsonPrimitive().isString()) {
                    if (!value.isJsonPrimitive()
                            || !value.getAsJsonPrimitive().isString()
                            || !identifier(value.getAsString(), false))
                        throw new IllegalArgumentException("expected namespaced identifier");
                } else validateNumber(key, value);
            } catch (RuntimeException e) {
                errors.add(new Problem(file, field, e.getMessage()));
                out.add(key, fallback.deepCopy());
            }
        }
        // Extension abilities always require an explicit boolean switch, even before their
        // mechanics ship.
        if (prefix.equals("habilidades."))
            for (var entry : input.entrySet()) {
                if (defaults.has(entry.getKey())) continue;
                JsonElement a = entry.getValue();
                if (!a.isJsonObject()
                        || !a.getAsJsonObject().has("activo")
                        || !a.getAsJsonObject().get("activo").isJsonPrimitive()
                        || !a.getAsJsonObject().getAsJsonPrimitive("activo").isBoolean()) {
                    errors.add(
                            new Problem(
                                    file,
                                    prefix + entry.getKey(),
                                    "ability requires boolean activo"));
                    JsonObject disabled = new JsonObject();
                    disabled.addProperty("activo", false);
                    out.add(entry.getKey(), disabled);
                }
            }
        return out;
    }

    private static void validateNumber(String key, JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("expected number");
        double n = value.getAsDouble();
        double min =
                switch (key) {
                    case "vida", "velocidad", "escala", "bonusStats" -> .01;
                    default -> 0;
                };
        double max =
                switch (key) {
                    case "multiplicadorSpawns" -> 16;
                    case "vida" -> 1024;
                    case "danio" -> 2048;
                    case "armadura" -> 30;
                    case "velocidad" -> 10;
                    case "escala" -> 16;
                    case "luzMax" -> 15;
                    case "peso" -> 1000000;
                    case "maxZombiesEspecialesPorJugador" -> 1000;
                    case "xp" -> 100000;
                    case "duracionInfeccion" -> 1200000;
                    case "probabilidadPorNoche",
                            "probabilidadRobo",
                            "probabilidadPorPaso",
                            "probabilidadSoltarEscudo",
                            "probabilidadInfeccion",
                            "probabilidadAparecer",
                            "resistenciaEmpuje" ->
                            1;
                    default -> 1000;
                };
        if (!Double.isFinite(n) || n < min || n > max)
            throw new IllegalArgumentException(
                    "expected finite number in [" + min + "," + max + "]");
        if (Set.of("cadaNNoches", "peso", "xp", "luzMax", "maxZombiesEspecialesPorJugador")
                        .contains(key)
                && n != Math.rint(n)) throw new IllegalArgumentException("expected integer");
    }

    private static void validateArray(String key, JsonElement value) {
        if (!value.isJsonArray()) throw new IllegalArgumentException("expected array");
        JsonArray a = value.getAsJsonArray();
        if (key.equals("grupo") || key.equals("alturaY")) {
            int min = key.equals("grupo") ? 1 : -2032, max = key.equals("grupo") ? 64 : 2031;
            if (a.size() != 2) throw new IllegalArgumentException("expected [min,max]");
            for (var e : a) {
                if (!e.isJsonPrimitive()
                        || !e.getAsJsonPrimitive().isNumber()
                        || !Double.isFinite(e.getAsDouble())
                        || e.getAsDouble() != Math.rint(e.getAsDouble())
                        || e.getAsDouble() < min
                        || e.getAsDouble() > max)
                    throw new IllegalArgumentException(
                            "expected integers in [" + min + "," + max + "]");
            }
            if (a.get(0).getAsInt() > a.get(1).getAsInt())
                throw new IllegalArgumentException("min exceeds max");
        } else
            for (var e : a)
                if (!e.isJsonPrimitive()
                        || !e.getAsJsonPrimitive().isString()
                        || !identifier(e.getAsString(), !key.equals("mundosPermitidos")))
                    throw new IllegalArgumentException(
                            "expected namespaced identifiers"
                                    + (key.equals("mundosPermitidos") ? "" : " or #tags"));
    }

    private static boolean identifier(String s, boolean tags) {
        return s.matches((tags ? "#?" : "") + "[a-z0-9_.-]+:[a-z0-9/._-]+");
    }

    public void setActive(ZombieKind kind, boolean active) throws IOException {
        Path path = root.resolve("zombies/" + kind.id() + ".json");
        // Never silently erase malformed user input when toggling a variant.
        JsonObject raw = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        raw.addProperty("activo", active);
        write(path, raw);
        reload();
    }

    private static void write(Path path, JsonObject value) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), ".zombies-", ".tmp");
        try {
            Files.writeString(temp, JSON.toJson(value) + "\n");
            try {
                Files.move(
                        temp,
                        path,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
