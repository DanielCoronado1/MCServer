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
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Loads, validates, and persists {@code config/dolfin-space.json} with one immutable record per
 * epic section. Invalid or out-of-range fields emit {@code event=config_invalid} warnings and
 * fall back to defaults independently without discarding valid sibling fields.
 */
public final class SpaceConfig {
    public static final String FILE_NAME = "dolfin-space.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Describes a single configuration validation fallback. */
    public record Problem(String field, String reason) {
        @Override
        public String toString() {
            return "file=" + FILE_NAME + " field=" + field + " reason=\"" + reason + "\" fallback=default";
        }
    }

    /** Summarizes a configuration load or reload pass. */
    public record Report(long generation, boolean created, List<Problem> problems, long millis) {
        public String summary() {
            return "generation=" + generation + " file=" + FILE_NAME + " created=" + created
                    + " errors=" + problems.size() + " durationMs=" + millis;
        }
    }

    /** Global switches and handshake settings (#25, #33). */
    public record GeneralSection(
            boolean activo,
            boolean exigirModEnCliente,
            String mensajeSinMod,
            boolean respetarClaims,
            boolean mensajesChat
    ) {
        public static GeneralSection defaults() {
            return new GeneralSection(
                    true,
                    true,
                    "Necesitas instalar el modpack de DolfinServer (mod dolfinspace) para entrar al servidor.",
                    true,
                    true
            );
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
            boolean permitirExecuteAdmin
    ) {
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
                    true
            );
        }
    }

    /** Processing durations and rates for the machine chain (#26, #29). */
    public record MachinesSection(
            int ticksCompresor,
            int ticksFabricadorCircuitos,
            int ticksRefineria,
            int ticksCompresorOxigeno,
            int segundosRecargaPorCicloOxigeno
    ) {
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
            int alturaAscensoDespegue
    ) {
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
            boolean consumirFueraDelPlaneta
    ) {
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
            double centinelaDanioProyectil
    ) {
        public static MobsSection defaults() {
            return new MobsSection(6.0, 1.0, 5, 16.0, 4.0, 24.0, 6.0, 20.0, 5.0);
        }
    }

    /** Client sky and camera shake accessibility preferences (#25, #28). */
    public record ClientSection(
            double intensidadVibracionCamara,
            boolean mostrarTierraEnCielo,
            double brilloEstrellasKepler
    ) {
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
            long generation
    ) {
        public static Snapshot defaults(long generation) {
            return new Snapshot(
                    GeneralSection.defaults(),
                    DimensionSection.defaults(),
                    MachinesSection.defaults(),
                    ShipsSection.defaults(),
                    OxygenSection.defaults(),
                    MobsSection.defaults(),
                    ClientSection.defaults(),
                    generation
            );
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

        Report report = new Report(
                nextGen,
                created[0],
                List.copyOf(problems),
                (System.nanoTime() - start) / 1_000_000
        );
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

        checkUnknownKeys(root, Set.of("general", "dimension", "maquinas", "naves", "oxigeno", "mobs", "cliente"), "", problems);
        return new Snapshot(
                parseGeneral(section(root, "general", problems), problems),
                parseDimension(section(root, "dimension", problems), problems),
                parseMachines(section(root, "maquinas", problems), problems),
                parseShips(section(root, "naves", problems), problems),
                parseOxygen(section(root, "oxigeno", problems), problems),
                parseMobs(section(root, "mobs", problems), problems),
                parseClient(section(root, "cliente", problems), problems),
                nextGen
        );
    }

    private static GeneralSection parseGeneral(JsonObject obj, List<Problem> problems) {
        GeneralSection d = GeneralSection.defaults();
        checkUnknownKeys(obj, Set.of("activo", "exigirModEnCliente", "mensajeSinMod", "respetarClaims", "mensajesChat"), "general.", problems);
        return new GeneralSection(
                boolField(obj, "general", "activo", d.activo(), problems),
                boolField(obj, "general", "exigirModEnCliente", d.exigirModEnCliente(), problems),
                nonBlankString(obj, "general", "mensajeSinMod", d.mensajeSinMod(), problems),
                boolField(obj, "general", "respetarClaims", d.respetarClaims(), problems),
                boolField(obj, "general", "mensajesChat", d.mensajesChat(), problems)
        );
    }

    private static DimensionSection parseDimension(JsonObject obj, List<Problem> problems) {
        DimensionSection d = DimensionSection.defaults();
        checkUnknownKeys(obj, Set.of(
                "idDimension", "multiplicadorGravedad", "multiplicadorDanioCaida",
                "multiplicadorDistanciaSeguraCaida", "sinLluvia", "sinNubes",
                "sinMobsVanilla", "camasExplotan", "anclaReaparicionActiva",
                "bloquearPortales", "bloquearTpDirecto", "permitirExecuteAdmin"
        ), "dimension.", problems);
        return new DimensionSection(
                identifierField(obj, "dimension", "idDimension", d.idDimension(), problems),
                doubleField(obj, "dimension", "multiplicadorGravedad", d.multiplicadorGravedad(), 0.05, 2.0, problems),
                doubleField(obj, "dimension", "multiplicadorDanioCaida", d.multiplicadorDanioCaida(), 0.0, 2.0, problems),
                doubleField(obj, "dimension", "multiplicadorDistanciaSeguraCaida", d.multiplicadorDistanciaSeguraCaida(), 1.0, 10.0, problems),
                boolField(obj, "dimension", "sinLluvia", d.sinLluvia(), problems),
                boolField(obj, "dimension", "sinNubes", d.sinNubes(), problems),
                boolField(obj, "dimension", "sinMobsVanilla", d.sinMobsVanilla(), problems),
                boolField(obj, "dimension", "camasExplotan", d.camasExplotan(), problems),
                boolField(obj, "dimension", "anclaReaparicionActiva", d.anclaReaparicionActiva(), problems),
                boolField(obj, "dimension", "bloquearPortales", d.bloquearPortales(), problems),
                boolField(obj, "dimension", "bloquearTpDirecto", d.bloquearTpDirecto(), problems),
                boolField(obj, "dimension", "permitirExecuteAdmin", d.permitirExecuteAdmin(), problems)
        );
    }

    private static MachinesSection parseMachines(JsonObject obj, List<Problem> problems) {
        MachinesSection d = MachinesSection.defaults();
        checkUnknownKeys(obj, Set.of(
                "ticksCompresor", "ticksFabricadorCircuitos", "ticksRefineria",
                "ticksCompresorOxigeno", "segundosRecargaPorCicloOxigeno"
        ), "maquinas.", problems);
        return new MachinesSection(
                intField(obj, "maquinas", "ticksCompresor", d.ticksCompresor(), 20, 12000, problems),
                intField(obj, "maquinas", "ticksFabricadorCircuitos", d.ticksFabricadorCircuitos(), 20, 12000, problems),
                intField(obj, "maquinas", "ticksRefineria", d.ticksRefineria(), 20, 12000, problems),
                intField(obj, "maquinas", "ticksCompresorOxigeno", d.ticksCompresorOxigeno(), 20, 12000, problems),
                intField(obj, "maquinas", "segundosRecargaPorCicloOxigeno", d.segundosRecargaPorCicloOxigeno(), 1, 3600, problems)
        );
    }

    private static ShipsSection parseShips(JsonObject obj, List<Problem> problems) {
        ShipsSection d = ShipsSection.defaults();
        checkUnknownKeys(obj, Set.of(
                "soloDuenioYAliadosPilotan", "invulnerables", "retornoAutomaticoAlReaparecerFuera",
                "porcentajeCombustiblePorViaje", "porcentajeCombustibleMinimoDespegueTierra",
                "segundosCuentaRegresivaBasica", "segundosCuentaRegresivaCarga",
                "segundosCuentaRegresivaRapida", "ranurasInventarioCarga", "alturaAscensoDespegue"
        ), "naves.", problems);
        return new ShipsSection(
                boolField(obj, "naves", "soloDuenioYAliadosPilotan", d.soloDuenioYAliadosPilotan(), problems),
                boolField(obj, "naves", "invulnerables", d.invulnerables(), problems),
                boolField(obj, "naves", "retornoAutomaticoAlReaparecerFuera", d.retornoAutomaticoAlReaparecerFuera(), problems),
                intField(obj, "naves", "porcentajeCombustiblePorViaje", d.porcentajeCombustiblePorViaje(), 1, 100, problems),
                intField(obj, "naves", "porcentajeCombustibleMinimoDespegueTierra", d.porcentajeCombustibleMinimoDespegueTierra(), 1, 100, problems),
                intField(obj, "naves", "segundosCuentaRegresivaBasica", d.segundosCuentaRegresivaBasica(), 1, 60, problems),
                intField(obj, "naves", "segundosCuentaRegresivaCarga", d.segundosCuentaRegresivaCarga(), 1, 60, problems),
                intField(obj, "naves", "segundosCuentaRegresivaRapida", d.segundosCuentaRegresivaRapida(), 1, 60, problems),
                intField(obj, "naves", "ranurasInventarioCarga", d.ranurasInventarioCarga(), 9, 54, problems),
                intField(obj, "naves", "alturaAscensoDespegue", d.alturaAscensoDespegue(), 16, 512, problems)
        );
    }

    private static OxygenSection parseOxygen(JsonObject obj, List<Problem> problems) {
        OxygenSection d = OxygenSection.defaults();
        // #29 public spelling, retaining the foundation spelling for existing configs.
        if (obj.has("danioAsfixiaPorSegundo")) {
            obj = obj.deepCopy();
            obj.add("danioSinOxigenoPorSegundo", obj.get("danioAsfixiaPorSegundo"));
        }
        checkUnknownKeys(obj, Set.of(
                "segundosTanqueChico", "segundosTanqueMediano", "segundosTanqueGrande",
                "danioSinOxigenoPorSegundo", "danioAsfixiaPorSegundo", "porcentajeAlertaOxigeno",
                "consumirEnNave", "consumirFueraDelPlaneta"
        ), "oxigeno.", problems);
        return new OxygenSection(
                intField(obj, "oxigeno", "segundosTanqueChico", d.segundosTanqueChico(), 30, 7200, problems),
                intField(obj, "oxigeno", "segundosTanqueMediano", d.segundosTanqueMediano(), 60, 14400, problems),
                intField(obj, "oxigeno", "segundosTanqueGrande", d.segundosTanqueGrande(), 120, 28800, problems),
                doubleField(obj, "oxigeno", "danioSinOxigenoPorSegundo", d.danioSinOxigenoPorSegundo(), 0.5, 20.0, problems),
                doubleField(obj, "oxigeno", "porcentajeAlertaOxigeno", d.porcentajeAlertaOxigeno(), 0.05, 0.90, problems),
                boolField(obj, "oxigeno", "consumirEnNave", d.consumirEnNave(), problems),
                boolField(obj, "oxigeno", "consumirFueraDelPlaneta", d.consumirFueraDelPlaneta(), problems)
        );
    }

    private static MobsSection parseMobs(JsonObject obj, List<Problem> problems) {
        MobsSection d = MobsSection.defaults();
        checkUnknownKeys(obj, Set.of(
                "parasitoVida", "parasitoDanio", "parasitoSegundosLevitacion",
                "acechadorVida", "acechadorDanio", "gusanoVida",
                "gusanoDanio", "centinelaVida", "centinelaDanioProyectil"
        ), "mobs.", problems);
        return new MobsSection(
                doubleField(obj, "mobs", "parasitoVida", d.parasitoVida(), 1.0, 100.0, problems),
                doubleField(obj, "mobs", "parasitoDanio", d.parasitoDanio(), 0.5, 50.0, problems),
                intField(obj, "mobs", "parasitoSegundosLevitacion", d.parasitoSegundosLevitacion(), 1, 30, problems),
                doubleField(obj, "mobs", "acechadorVida", d.acechadorVida(), 1.0, 200.0, problems),
                doubleField(obj, "mobs", "acechadorDanio", d.acechadorDanio(), 0.5, 50.0, problems),
                doubleField(obj, "mobs", "gusanoVida", d.gusanoVida(), 1.0, 200.0, problems),
                doubleField(obj, "mobs", "gusanoDanio", d.gusanoDanio(), 0.5, 50.0, problems),
                doubleField(obj, "mobs", "centinelaVida", d.centinelaVida(), 1.0, 200.0, problems),
                doubleField(obj, "mobs", "centinelaDanioProyectil", d.centinelaDanioProyectil(), 0.5, 50.0, problems)
        );
    }

    private static ClientSection parseClient(JsonObject obj, List<Problem> problems) {
        ClientSection d = ClientSection.defaults();
        checkUnknownKeys(obj, Set.of(
                "intensidadVibracionCamara", "mostrarTierraEnCielo", "brilloEstrellasKepler"
        ), "cliente.", problems);
        return new ClientSection(
                doubleField(obj, "cliente", "intensidadVibracionCamara", d.intensidadVibracionCamara(), 0.0, 1.0, problems),
                boolField(obj, "cliente", "mostrarTierraEnCielo", d.mostrarTierraEnCielo(), problems),
                doubleField(obj, "cliente", "brilloEstrellasKepler", d.brilloEstrellasKepler(), 0.0, 1.0, problems)
        );
    }

    private static JsonObject section(JsonObject root, String name, List<Problem> problems) {
        if (!root.has(name)) {
            return new JsonObject();
        }
        JsonElement el = root.get(name);
        if (!el.isJsonObject()) {
            problems.add(new Problem(name, "expected object section"));
            return new JsonObject();
        }
        return el.getAsJsonObject();
    }

    private static void checkUnknownKeys(JsonObject obj, Set<String> allowed, String prefix, List<Problem> problems) {
        for (String key : obj.keySet()) {
            if (!allowed.contains(key)) {
                problems.add(new Problem(prefix + key, "unknown configuration key"));
            }
        }
    }

    private static boolean boolField(JsonObject obj, String section, String key, boolean def, List<Problem> problems) {
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

    private static int intField(JsonObject obj, String section, String key, int def, int min, int max, List<Problem> problems) {
        if (!obj.has(key)) {
            return def;
        }
        JsonElement el = obj.get(key);
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            problems.add(new Problem(section + "." + key, "expected integer in [" + min + "," + max + "]"));
            return def;
        }
        double value = el.getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < min || value > max) {
            problems.add(new Problem(section + "." + key, "value " + el + " out of integer range [" + min + "," + max + "]"));
            return def;
        }
        return (int) value;
    }

    private static double doubleField(JsonObject obj, String section, String key, double def, double min, double max, List<Problem> problems) {
        if (!obj.has(key)) {
            return def;
        }
        JsonElement el = obj.get(key);
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            problems.add(new Problem(section + "." + key, "expected number in [" + min + "," + max + "]"));
            return def;
        }
        double value = el.getAsDouble();
        if (!Double.isFinite(value) || value < min || value > max) {
            problems.add(new Problem(section + "." + key, "value " + el + " out of range [" + min + "," + max + "]"));
            return def;
        }
        return value;
    }

    private static String nonBlankString(JsonObject obj, String section, String key, String def, List<Problem> problems) {
        if (!obj.has(key)) {
            return def;
        }
        JsonElement el = obj.get(key);
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString() || el.getAsString().isBlank()) {
            problems.add(new Problem(section + "." + key, "expected non-blank string"));
            return def;
        }
        return el.getAsString();
    }

    private static String identifierField(JsonObject obj, String section, String key, String def, List<Problem> problems) {
        if (!obj.has(key)) {
            return def;
        }
        JsonElement el = obj.get(key);
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()
                || !el.getAsString().matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
            problems.add(new Problem(section + "." + key, "expected namespaced identifier"));
            return def;
        }
        return el.getAsString();
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
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
