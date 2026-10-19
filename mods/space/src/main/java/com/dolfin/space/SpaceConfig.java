package com.dolfin.space;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Loads, validates, and persists {@code config/dolfin-space.json} with one immutable record per
 * epic section. Invalid or out-of-range fields emit {@code event=config_invalid} warnings and fall
 * back to defaults independently without discarding valid sibling fields.
 */
public final class SpaceConfig {
    public static final String FILE_NAME = "dolfin-space.json";
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> KNOWN_SECTIONS =
            Set.of("general", "dimension", "maquinas", "naves", "oxigeno", "mobs", "cliente");

    /** Describes a single configuration validation fallback. */
    public record Problem(String field, String reason) {
        @Override
        public String toString() {
            return "file="
                    + FILE_NAME
                    + " field="
                    + field
                    + " reason=\""
                    + reason
                    + "\" fallback=default";
        }
    }

    /** Summarizes a configuration load or reload pass. */
    public record Report(long generation, boolean created, List<Problem> problems, long millis) {
        public String summary() {
            return "generation="
                    + generation
                    + " file="
                    + FILE_NAME
                    + " created="
                    + created
                    + " errors="
                    + problems.size()
                    + " durationMs="
                    + millis;
        }
    }

    /** Global switches and handshake settings (#25, #33). */
    public record GeneralSection(
            boolean activo,
            boolean exigirModEnCliente,
            String mensajeSinMod,
            boolean respetarClaims,
            boolean mensajesChat) {
        public static GeneralSection defaults() {
            return new GeneralSection(
                    true,
                    true,
                    "Necesitas instalar el modpack de DolfinServer (mod dolfinspace) para entrar al"
                            + " servidor.",
                    true,
                    true);
        }
    }

    /** Planet Kepler physics, environment, and travel rules (#25, #28, #30). */
    public record DimensionSection(
            String idDimension,
            double multiplicadorGravedad,
            double multiplicadorDanioCaida,
            double multiplicadorDistanciaSeguraCaida,
            boolean sinLluvia,
            boolean sinNubes,
            boolean sinMobsVanilla,
            boolean camasExplotan,
            boolean anclaReaparicionActiva,
            boolean bloquearPortales,
            boolean bloquearTpDirecto,
            boolean permitirExecuteAdmin) {
        public static DimensionSection defaults() {
            return new DimensionSection(
                    "dolfinspace:kepler",
                    0.40,
                    0.40,
                    2.50,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true);
        }
    }

    /** Processing durations and rates for the machine chain (#26, #29). */
    public record MachinesSection(
            int ticksCompresor,
            int ticksFabricadorCircuitos,
            int ticksRefineria,
            int ticksCompresorOxigeno,
            int segundosRecargaPorCicloOxigeno) {
        public static MachinesSection defaults() {
            return new MachinesSection(200, 200, 400, 100, 60);
        }
    }

    /** Ship ownership, fuel consumption, countdowns, and cargo capacity (#27, #28). */
    public record ShipsSection(
            boolean soloDuenioYAliadosPilotan,
            boolean invulnerables,
            boolean retornoAutomaticoAlReaparecerFuera,
            int porcentajeCombustiblePorViaje,
            int porcentajeCombustibleMinimoDespegueTierra,
            int segundosCuentaRegresivaBasica,
            int segundosCuentaRegresivaCarga,
            int segundosCuentaRegresivaRapida,
            int ranurasInventarioCarga,
            int alturaAscensoDespegue) {
        public static ShipsSection defaults() {
            return new ShipsSection(true, true, true, 50, 100, 10, 10, 5, 27, 100);
        }
    }

    /** Oxygen tank capacities, depletion rules, and suffocation damage (#29). */
    public record OxygenSection(
            int segundosTanqueChico,
            int segundosTanqueMediano,
            int segundosTanqueGrande,
            double danioSinOxigenoPorSegundo,
            double porcentajeAlertaOxigeno,
            boolean consumirEnNave,
            boolean consumirFueraDelPlaneta) {
        public static OxygenSection defaults() {
            return new OxygenSection(300, 600, 1200, 2.0, 0.20, false, false);
        }
    }

    /** Hostile planet fauna stats and abilities (#31). */
    public record MobsSection(
            double parasitoVida,
            double parasitoDanio,
            int parasitoSegundosLevitacion,
            double acechadorVida,
            double acechadorDanio,
            double gusanoVida,
            double gusanoDanio,
            double centinelaVida,
            double centinelaDanioProyectil) {
        public static MobsSection defaults() {
            return new MobsSection(6.0, 1.0, 5, 16.0, 4.0, 24.0, 6.0, 20.0, 5.0);
        }
    }

    /** Client sky and camera shake accessibility preferences (#25, #28). */
    public record ClientSection(
            double intensidadVibracionCamara,
            boolean mostrarTierraEnCielo,
            double brilloEstrellasKepler) {
        public static ClientSection defaults() {
            return new ClientSection(1.0, true, 0.90);
        }
    }

    /** Immutable snapshot of all configuration sections at a given reload generation. */
    public record Snapshot(
            GeneralSection general,
            DimensionSection dimension,
            MachinesSection maquinas,
            ShipsSection naves,
            OxygenSection oxigeno,
            MobsSection mobs,
            ClientSection cliente,
            long generation) {
        public static Snapshot defaults(long generation) {
            return new Snapshot(
                    GeneralSection.defaults(),
                    DimensionSection.defaults(),
                    MachinesSection.defaults(),
                    ShipsSection.defaults(),
                    OxygenSection.defaults(),
                    MobsSection.defaults(),
                    ClientSection.defaults(),
                    generation);
        }
    }

    /**
     * Reads and validates typed configuration fields for a single section, recording known keys and
     * emitting {@link Problem}s for missing or invalid values.
     */
    public static final class SectionReader {
        private final String section;
        private JsonObject obj;
        private final List<Problem> problems;
        private final Set<String> knownKeys = new HashSet<>();

        public SectionReader(String section, JsonObject obj, List<Problem> problems) {
            this.section = section;
            this.obj = obj != null ? obj : new JsonObject();
            this.problems = problems;
        }

        public static SectionReader from(JsonObject root, String section, List<Problem> problems) {
            if (!root.has(section)) {
                return new SectionReader(section, new JsonObject(), problems);
            }
            JsonElement el = root.get(section);
            if (!el.isJsonObject()) {
                problems.add(new Problem(section, "expected object section"));
                return new SectionReader(section, new JsonObject(), problems);
            }
            return new SectionReader(section, el.getAsJsonObject(), problems);
        }

        /** Accepts a legacy spelling; when present it takes precedence over the canonical key. */
        public void alias(String aliasKey, String canonicalKey) {
            knownKeys.add(aliasKey);
            if (obj.has(aliasKey)) {
                obj = obj.deepCopy();
                obj.add(canonicalKey, obj.get(aliasKey));
            }
        }

        public boolean bool(String key, boolean def) {
            knownKeys.add(key);
            if (!obj.has(key)) {
                return def;
            }
            JsonElement el = obj.get(key);
            if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isBoolean()) {
                problems.add(new Problem(section + "." + key, "expected boolean"));
                return def;
            }
            return el.getAsBoolean();
        }

        public int integer(String key, int def, int min, int max) {
            knownKeys.add(key);
            if (!obj.has(key)) {
                return def;
            }
            JsonElement el = obj.get(key);
            if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
                problems.add(
                        new Problem(
                                section + "." + key,
                                "expected integer in [" + min + "," + max + "]"));
                return def;
            }
            double value = el.getAsDouble();
            if (!Double.isFinite(value)
                    || value != Math.rint(value)
                    || value < min
                    || value > max) {
                problems.add(
                        new Problem(
                                section + "." + key,
                                "value " + el + " out of integer range [" + min + "," + max + "]"));
                return def;
            }
            return (int) value;
        }

        public double decimal(String key, double def, double min, double max) {
            knownKeys.add(key);
            if (!obj.has(key)) {
                return def;
            }
            JsonElement el = obj.get(key);
            if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
                problems.add(
                        new Problem(
                                section + "." + key,
                                "expected number in [" + min + "," + max + "]"));
                return def;
            }
            double value = el.getAsDouble();
            if (!Double.isFinite(value) || value < min || value > max) {
                problems.add(
                        new Problem(
                                section + "." + key,
                                "value " + el + " out of range [" + min + "," + max + "]"));
                return def;
            }
            return value;
        }

        public String string(String key, String def) {
            knownKeys.add(key);
            if (!obj.has(key)) {
                return def;
            }
            JsonElement el = obj.get(key);
            if (!el.isJsonPrimitive()
                    || !el.getAsJsonPrimitive().isString()
                    || el.getAsString().isBlank()) {
                problems.add(new Problem(section + "." + key, "expected non-blank string"));
                return def;
            }
            return el.getAsString();
        }

        public String identifier(String key, String def) {
            knownKeys.add(key);
            if (!obj.has(key)) {
                return def;
            }
            JsonElement el = obj.get(key);
            if (!el.isJsonPrimitive()
                    || !el.getAsJsonPrimitive().isString()
                    || !el.getAsString().matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
                problems.add(new Problem(section + "." + key, "expected namespaced identifier"));
                return def;
            }
            return el.getAsString();
        }

        /** Emits problems for any keys present in the JSON object that were not recognized. */
        public void finish() {
            for (String key : obj.keySet()) {
                if (!knownKeys.contains(key)) {
                    problems.add(new Problem(section + "." + key, "unknown configuration key"));
                }
            }
        }
    }

    private final Path file;
    private final Consumer<String> warnings;
    private volatile Snapshot snapshot;
    private volatile Report lastReport;
    private long generation;

    public SpaceConfig(Path file, Consumer<String> warnings) {
        this.file = file;
        this.warnings = warnings;
        reload();
    }

    public Snapshot current() {
        return snapshot;
    }

    public Report lastReport() {
        return lastReport;
    }

    public Path file() {
        return file;
    }

    /** Reloads {@code config/dolfin-space.json} from disk, creating defaults if missing. */
    public synchronized Report reload() {
        long start = System.nanoTime();
        List<Problem> problems = new ArrayList<>();
        boolean[] created = {false};
        long nextGen = ++generation;

        Snapshot loaded = loadSnapshot(nextGen, problems, created);
        this.snapshot = loaded;
        for (Problem problem : problems) {
            warnings.accept("event=config_invalid generation=" + nextGen + " " + problem);
        }

        Report report =
                new Report(
                        nextGen,
                        created[0],
                        List.copyOf(problems),
                        (System.nanoTime() - start) / 1_000_000);
        this.lastReport = report;
        return report;
    }

    private Snapshot loadSnapshot(long nextGen, List<Problem> problems, boolean[] created) {
        Snapshot def = Snapshot.defaults(nextGen);
        if (!Files.exists(file)) {
            try {
                writeAtomically(file, toJsonObject(def));
                created[0] = true;
            } catch (IOException e) {
                problems.add(new Problem("<write>", "failed to write defaults: " + e.getMessage()));
            }
            return def;
        }

        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file));
            if (parsed == null || !parsed.isJsonObject()) {
                problems.add(new Problem("<root>", "expected JSON object"));
                return def;
            }
            root = parsed.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            problems.add(new Problem("<root>", "malformed JSON: " + e.getMessage()));
            return def;
        }

        for (String key : root.keySet()) {
            if (!KNOWN_SECTIONS.contains(key)) {
                problems.add(new Problem(key, "unknown configuration key"));
            }
        }
        return new Snapshot(
                parseGeneral(SectionReader.from(root, "general", problems)),
                parseDimension(SectionReader.from(root, "dimension", problems)),
                parseMachines(SectionReader.from(root, "maquinas", problems)),
                parseShips(SectionReader.from(root, "naves", problems)),
                parseOxygen(SectionReader.from(root, "oxigeno", problems)),
                parseMobs(SectionReader.from(root, "mobs", problems)),
                parseClient(SectionReader.from(root, "cliente", problems)),
                nextGen);
    }

    private static GeneralSection parseGeneral(SectionReader r) {
        GeneralSection d = GeneralSection.defaults();
        GeneralSection section =
                new GeneralSection(
                        r.bool("activo", d.activo()),
                        r.bool("exigirModEnCliente", d.exigirModEnCliente()),
                        r.string("mensajeSinMod", d.mensajeSinMod()),
                        r.bool("respetarClaims", d.respetarClaims()),
                        r.bool("mensajesChat", d.mensajesChat()));
        r.finish();
        return section;
    }

    private static DimensionSection parseDimension(SectionReader r) {
        DimensionSection d = DimensionSection.defaults();
        DimensionSection section =
                new DimensionSection(
                        r.identifier("idDimension", d.idDimension()),
                        r.decimal("multiplicadorGravedad", d.multiplicadorGravedad(), 0.05, 2.0),
                        r.decimal("multiplicadorDanioCaida", d.multiplicadorDanioCaida(), 0.0, 2.0),
                        r.decimal(
                                "multiplicadorDistanciaSeguraCaida",
                                d.multiplicadorDistanciaSeguraCaida(),
                                1.0,
                                10.0),
                        r.bool("sinLluvia", d.sinLluvia()),
                        r.bool("sinNubes", d.sinNubes()),
                        r.bool("sinMobsVanilla", d.sinMobsVanilla()),
                        r.bool("camasExplotan", d.camasExplotan()),
                        r.bool("anclaReaparicionActiva", d.anclaReaparicionActiva()),
                        r.bool("bloquearPortales", d.bloquearPortales()),
                        r.bool("bloquearTpDirecto", d.bloquearTpDirecto()),
                        r.bool("permitirExecuteAdmin", d.permitirExecuteAdmin()));
        r.finish();
        return section;
    }

    private static MachinesSection parseMachines(SectionReader r) {
        MachinesSection d = MachinesSection.defaults();
        MachinesSection section =
                new MachinesSection(
                        r.integer("ticksCompresor", d.ticksCompresor(), 20, 12000),
                        r.integer(
                                "ticksFabricadorCircuitos",
                                d.ticksFabricadorCircuitos(),
                                20,
                                12000),
                        r.integer("ticksRefineria", d.ticksRefineria(), 20, 12000),
                        r.integer("ticksCompresorOxigeno", d.ticksCompresorOxigeno(), 20, 12000),
                        r.integer(
                                "segundosRecargaPorCicloOxigeno",
                                d.segundosRecargaPorCicloOxigeno(),
                                1,
                                3600));
        r.finish();
        return section;
    }

    private static ShipsSection parseShips(SectionReader r) {
        ShipsSection d = ShipsSection.defaults();
        ShipsSection section =
                new ShipsSection(
                        r.bool("soloDuenioYAliadosPilotan", d.soloDuenioYAliadosPilotan()),
                        r.bool("invulnerables", d.invulnerables()),
                        r.bool(
                                "retornoAutomaticoAlReaparecerFuera",
                                d.retornoAutomaticoAlReaparecerFuera()),
                        r.integer(
                                "porcentajeCombustiblePorViaje",
                                d.porcentajeCombustiblePorViaje(),
                                1,
                                100),
                        r.integer(
                                "porcentajeCombustibleMinimoDespegueTierra",
                                d.porcentajeCombustibleMinimoDespegueTierra(),
                                1,
                                100),
                        r.integer(
                                "segundosCuentaRegresivaBasica",
                                d.segundosCuentaRegresivaBasica(),
                                1,
                                60),
                        r.integer(
                                "segundosCuentaRegresivaCarga",
                                d.segundosCuentaRegresivaCarga(),
                                1,
                                60),
                        r.integer(
                                "segundosCuentaRegresivaRapida",
                                d.segundosCuentaRegresivaRapida(),
                                1,
                                60),
                        r.integer("ranurasInventarioCarga", d.ranurasInventarioCarga(), 9, 54),
                        r.integer("alturaAscensoDespegue", d.alturaAscensoDespegue(), 16, 512));
        r.finish();
        return section;
    }

    private static OxygenSection parseOxygen(SectionReader r) {
        OxygenSection d = OxygenSection.defaults();
        r.alias("danioAsfixiaPorSegundo", "danioSinOxigenoPorSegundo");
        OxygenSection section =
                new OxygenSection(
                        r.integer("segundosTanqueChico", d.segundosTanqueChico(), 30, 7200),
                        r.integer("segundosTanqueMediano", d.segundosTanqueMediano(), 60, 14400),
                        r.integer("segundosTanqueGrande", d.segundosTanqueGrande(), 120, 28800),
                        r.decimal(
                                "danioSinOxigenoPorSegundo",
                                d.danioSinOxigenoPorSegundo(),
                                0.5,
                                20.0),
                        r.decimal(
                                "porcentajeAlertaOxigeno", d.porcentajeAlertaOxigeno(), 0.05, 0.90),
                        r.bool("consumirEnNave", d.consumirEnNave()),
                        r.bool("consumirFueraDelPlaneta", d.consumirFueraDelPlaneta()));
        r.finish();
        return section;
    }

    private static MobsSection parseMobs(SectionReader r) {
        MobsSection d = MobsSection.defaults();
        MobsSection section =
                new MobsSection(
                        r.decimal("parasitoVida", d.parasitoVida(), 1.0, 100.0),
                        r.decimal("parasitoDanio", d.parasitoDanio(), 0.5, 50.0),
                        r.integer(
                                "parasitoSegundosLevitacion",
                                d.parasitoSegundosLevitacion(),
                                1,
                                30),
                        r.decimal("acechadorVida", d.acechadorVida(), 1.0, 200.0),
                        r.decimal("acechadorDanio", d.acechadorDanio(), 0.5, 50.0),
                        r.decimal("gusanoVida", d.gusanoVida(), 1.0, 200.0),
                        r.decimal("gusanoDanio", d.gusanoDanio(), 0.5, 50.0),
                        r.decimal("centinelaVida", d.centinelaVida(), 1.0, 200.0),
                        r.decimal(
                                "centinelaDanioProyectil", d.centinelaDanioProyectil(), 0.5, 50.0));
        r.finish();
        return section;
    }

    private static ClientSection parseClient(SectionReader r) {
        ClientSection d = ClientSection.defaults();
        ClientSection section =
                new ClientSection(
                        r.decimal(
                                "intensidadVibracionCamara",
                                d.intensidadVibracionCamara(),
                                0.0,
                                1.0),
                        r.bool("mostrarTierraEnCielo", d.mostrarTierraEnCielo()),
                        r.decimal("brilloEstrellasKepler", d.brilloEstrellasKepler(), 0.0, 1.0));
        r.finish();
        return section;
    }

    private static JsonObject toJsonObject(Snapshot s) {
        JsonObject root = new JsonObject();
        root.add("general", GSON.toJsonTree(s.general()));
        root.add("dimension", GSON.toJsonTree(s.dimension()));
        root.add("maquinas", GSON.toJsonTree(s.maquinas()));
        root.add("naves", GSON.toJsonTree(s.naves()));
        root.add("oxigeno", GSON.toJsonTree(s.oxigeno()));
        root.add("mobs", GSON.toJsonTree(s.mobs()));
        root.add("cliente", GSON.toJsonTree(s.cliente()));
        return root;
    }

    private static void writeAtomically(Path target, JsonObject value) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, ".dolfin-space-", ".tmp");
        try {
            Files.writeString(temp, GSON.toJson(value) + "\n");
            try {
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
