package com.dolfin.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class SpaceFoundationTest {
    @TempDir Path tempDir;

    @Test
    void createsDefaultConfigFileWhenMissing() {
        Path configFile = tempDir.resolve("config/dolfin-space.json");
        List<String> warnings = new ArrayList<>();

        SpaceConfig config = new SpaceConfig(configFile, warnings::add);
        SpaceConfig.Snapshot snapshot = config.current();

        assertTrue(
                Files.isRegularFile(configFile), "Default config file should be written to disk");
        assertTrue(config.lastReport().created(), "Report should indicate file was created");
        assertTrue(warnings.isEmpty(), "Default config creation should not emit warnings");
        assertEquals("dolfinspace:kepler", snapshot.dimension().idDimension());
        assertEquals(0.40D, snapshot.dimension().multiplicadorGravedad(), 1.0E-6D);
        assertEquals(0.40D, snapshot.dimension().multiplicadorDanioCaida(), 1.0E-6D);
        assertEquals(2.50D, snapshot.dimension().multiplicadorDistanciaSeguraCaida(), 1.0E-6D);
        assertTrue(snapshot.dimension().sinLluvia());
        assertTrue(snapshot.dimension().sinNubes());
        assertTrue(snapshot.dimension().sinMobsVanilla());
        assertTrue(snapshot.dimension().camasExplotan());
        assertTrue(snapshot.dimension().anclaReaparicionActiva());
        assertTrue(snapshot.dimension().bloquearPortales());
        assertTrue(snapshot.dimension().bloquearTpDirecto());
        assertTrue(snapshot.dimension().permitirExecuteAdmin());
        assertEquals(300, snapshot.oxigeno().segundosTanqueChico());
        assertEquals(50, snapshot.naves().porcentajeCombustiblePorViaje());
    }

    @Test
    void fallsBackPerFieldAndReportsValidationIssuesWithoutCrashing() throws IOException {
        Path configFile = tempDir.resolve("invalid-fields.json");
        String rawJson =
                """
                {
                  "general": {
                    "activo": "not_a_boolean",
                    "exigirModEnCliente": false,
                    "unknownGeneralKey": 123
                  },
                  "dimension": {
                    "idDimension": "   ",
                    "multiplicadorGravedad": -2.5,
                    "multiplicadorDanioCaida": "bad_double"
                  },
                  "maquinas": "not_an_object",
                  "oxigeno": {
                    "segundosTanqueChico": 0
                  },
                  "unknownTopLevelSection": {
                    "foo": "bar"
                  }
                }
                """;
        Files.writeString(configFile, rawJson, StandardCharsets.UTF_8);
        List<String> warnings = new ArrayList<>();

        SpaceConfig config = new SpaceConfig(configFile, warnings::add);
        SpaceConfig.Snapshot snapshot = config.current();

        assertTrue(snapshot.general().activo(), "Invalid boolean should fall back to default true");
        assertFalse(
                snapshot.general().exigirModEnCliente(),
                "Valid boolean in same section should be preserved");
        assertEquals(
                "dolfinspace:kepler",
                snapshot.dimension().idDimension(),
                "Blank string should fall back to default ID");
        assertEquals(0.40D, snapshot.dimension().multiplicadorGravedad(), 1.0E-6D);
        assertEquals(0.40D, snapshot.dimension().multiplicadorDanioCaida(), 1.0E-6D);
        assertEquals(
                200,
                snapshot.maquinas().ticksCompresor(),
                "Non-object section should fall back to defaults");
        assertEquals(
                300,
                snapshot.oxigeno().segundosTanqueChico(),
                "Out-of-range oxygen seconds should fall back to 300");
        assertFalse(warnings.isEmpty(), "Invalid fields should emit event=config_invalid warnings");
        assertTrue(warnings.stream().allMatch(w -> w.contains("event=config_invalid")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("dimension.multiplicadorGravedad")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("maquinas")));
    }

    @Test
    void handlesMalformedJsonSyntaxGracefully() throws IOException {
        Path configFile = tempDir.resolve("broken.json");
        Files.writeString(configFile, "{ broken json", StandardCharsets.UTF_8);
        List<String> warnings = new ArrayList<>();

        SpaceConfig config = new SpaceConfig(configFile, warnings::add);

        assertEquals(SpaceConfig.Snapshot.defaults(1L), config.current());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("event=config_invalid"));
    }

    @Test
    void reloadIncrementsGenerationCounter() {
        Path configFile = tempDir.resolve("reload.json");
        SpaceConfig config = new SpaceConfig(configFile, ignored -> {});
        long before = config.current().generation();

        SpaceConfig.Report report = config.reload();

        assertEquals(before + 1L, report.generation());
        assertEquals(before + 1L, config.current().generation());
    }

    @Test
    void gravityAndFallModifiersMatchFortyPercentSpecification() {
        assertEquals(-0.60D, KeplerDimension.gravityModifierAmount(0.40D), 1.0E-6D);
        assertEquals(-0.60D, KeplerDimension.fallDamageModifierAmount(0.40D), 1.0E-6D);
        assertEquals(1.50D, KeplerDimension.safeFallModifierAmount(2.50D), 1.0E-6D);
        assertEquals(0.0D, KeplerDimension.gravityModifierAmount(1.0D), 1.0E-6D);
        assertEquals(0.0D, KeplerDimension.safeFallModifierAmount(1.0D), 1.0E-6D);
    }

    @Test
    void naturalMobSpawnFilterBlocksVanillaMobsInKeplerOnly() {
        assertTrue(KeplerDimension.isKeplerId("dolfinspace:kepler"));
        assertFalse(KeplerDimension.isKeplerId("minecraft:overworld"));
        assertFalse(KeplerDimension.isAllowedKeplerNaturalSpawnId("minecraft:zombie"));
        assertTrue(KeplerDimension.isAllowedKeplerNaturalSpawnId("dolfinspace:kepler_parasite"));
    }

    @Test
    void travelAccessAllowsOnlyShipAndAdminExecuteInToReachKepler() {
        SpaceConfig.DimensionSection cfg = SpaceConfig.DimensionSection.defaults();
        String overworld = "minecraft:overworld";
        String nether = "minecraft:the_nether";
        String kepler = "dolfinspace:kepler";

        assertTrue(
                SpaceTravelAccess.evaluateTravel(
                                new SpaceTravelAccess.TravelRequest(
                                        overworld,
                                        nether,
                                        SpaceTravelAccess.TravelMechanism.PORTAL,
                                        false,
                                        false),
                                cfg)
                        .allowed());

        assertTrue(
                SpaceTravelAccess.evaluateTravel(
                                new SpaceTravelAccess.TravelRequest(
                                        kepler,
                                        kepler,
                                        SpaceTravelAccess.TravelMechanism.OTHER_TELEPORT,
                                        false,
                                        false),
                                cfg)
                        .allowed());

        assertFalse(
                SpaceTravelAccess.evaluateTravel(
                                new SpaceTravelAccess.TravelRequest(
                                        overworld,
                                        kepler,
                                        SpaceTravelAccess.TravelMechanism.DIRECT_TP_COMMAND,
                                        true,
                                        false),
                                cfg)
                        .allowed());

        assertFalse(
                SpaceTravelAccess.evaluateTravel(
                                new SpaceTravelAccess.TravelRequest(
                                        overworld,
                                        kepler,
                                        SpaceTravelAccess.TravelMechanism.PORTAL,
                                        true,
                                        false),
                                cfg)
                        .allowed());

        assertTrue(
                SpaceTravelAccess.evaluateTravel(
                                new SpaceTravelAccess.TravelRequest(
                                        overworld,
                                        kepler,
                                        SpaceTravelAccess.TravelMechanism.SHIP,
                                        false,
                                        true),
                                cfg)
                        .allowed());

        assertTrue(
                SpaceTravelAccess.evaluateTravel(
                                new SpaceTravelAccess.TravelRequest(
                                        overworld,
                                        kepler,
                                        SpaceTravelAccess.TravelMechanism.ADMIN_EXECUTE_IN_KEPLER,
                                        true,
                                        false),
                                cfg)
                        .allowed());

        assertFalse(
                SpaceTravelAccess.evaluateTravel(
                                new SpaceTravelAccess.TravelRequest(
                                        overworld,
                                        kepler,
                                        SpaceTravelAccess.TravelMechanism.ADMIN_EXECUTE_IN_KEPLER,
                                        false,
                                        false),
                                cfg)
                        .allowed());
    }

    @Test
    void modpackCheckEvaluatesMissingClientModCorrectly() {
        SpaceModpackCheck.HandshakeDecision allowedModded =
                SpaceModpackCheck.evaluateHandshake(true, true, "Custom msg");
        assertTrue(allowedModded.allowed());

        SpaceModpackCheck.HandshakeDecision rejectedVanilla =
                SpaceModpackCheck.evaluateHandshake(true, false, "Necesitas el modpack");
        assertFalse(rejectedVanilla.allowed());
        assertEquals("Necesitas el modpack", rejectedVanilla.disconnectMessage());

        SpaceModpackCheck.HandshakeDecision allowedWhenDisabled =
                SpaceModpackCheck.evaluateHandshake(false, false, "Necesitas el modpack");
        assertTrue(allowedWhenDisabled.allowed());
    }

    @Test
    void permissionsResolutionHonorsExplicitAndFallbackValues() {
        assertTrue(SpacePermissions.resolve(Boolean.TRUE, false));
        assertFalse(SpacePermissions.resolve(Boolean.FALSE, true));
        assertTrue(SpacePermissions.resolve(null, true));
        assertFalse(SpacePermissions.resolve(null, false));
    }

    @Test
    void datapackAndAssetResourcesMatchKeplerSpecification() throws IOException {
        Path resourcesRoot = Path.of("src/main/resources");
        JsonObject dimensionType =
                readJson(resourcesRoot.resolve("data/dolfinspace/dimension_type/kepler.json"));
        JsonObject attributes = dimensionType.getAsJsonObject("attributes");
        assertNotNull(attributes);
        assertTrue(
                attributes
                        .getAsJsonObject("minecraft:gameplay/bed_rule")
                        .get("explodes")
                        .getAsBoolean());
        assertTrue(attributes.get("minecraft:gameplay/respawn_anchor_works").getAsBoolean());
        assertEquals("#00000000", attributes.get("minecraft:visual/cloud_color").getAsString());
        assertEquals("#000000", attributes.get("minecraft:visual/sky_color").getAsString());
        assertEquals(
                1.0D, attributes.get("minecraft:visual/star_brightness").getAsDouble(), 1.0E-6D);

        JsonObject noiseSettings =
                readJson(
                        resourcesRoot.resolve(
                                "data/dolfinspace/worldgen/noise_settings/kepler.json"));
        assertTrue(noiseSettings.get("disable_mob_generation").getAsBoolean());
        assertEquals("dolfinspace:regolito", noiseSettings.get("default_block").getAsString());

        JsonObject biome =
                readJson(
                        resourcesRoot.resolve(
                                "data/dolfinspace/worldgen/biome/kepler_plains.json"));
        assertFalse(biome.get("has_precipitation").getAsBoolean());
        assertEquals(0.0D, biome.get("downfall").getAsDouble(), 1.0E-6D);

        Path earthPng =
                resourcesRoot.resolve(
                        "assets/dolfinspace/textures/environment/celestial/earth.png");
        Path regolitoPng = resourcesRoot.resolve("assets/dolfinspace/textures/block/regolito.png");
        assertTrue(Files.isRegularFile(earthPng));
        assertTrue(Files.isRegularFile(regolitoPng));
        byte[] signature = Files.readAllBytes(earthPng);
        assertTrue(
                signature.length > 8
                        && signature[0] == (byte) 0x89
                        && signature[1] == 0x50
                        && signature[2] == 0x4E
                        && signature[3] == 0x47);
    }

    @Test
    void sectionReaderParsesValidValuesAndEnforcesTypesAndRanges() {
        JsonObject json = new JsonObject();
        json.addProperty("unBoolean", true);
        json.addProperty("malBoolean", "not_bool");
        json.addProperty("unEntero", 42);
        json.addProperty("enteroFuera", 5);
        json.addProperty("enteroFraccional", 42.5);
        json.addProperty("unDecimal", 3.14);
        json.addProperty("decimalFuera", 100.0);
        json.addProperty("unTexto", "hola");
        json.addProperty("textoBlanco", "   ");
        json.addProperty("unId", "dolfinspace:kepler");
        json.addProperty("malId", "sin_namespace");
        json.addProperty("claveDesconocida", 999);

        List<SpaceConfig.Problem> problems = new ArrayList<>();
        SpaceConfig.SectionReader reader = new SpaceConfig.SectionReader("test", json, problems);

        assertTrue(reader.bool("unBoolean", false));
        assertFalse(reader.bool("malBoolean", false));
        assertFalse(reader.bool("ausenteBoolean", false));

        assertEquals(42, reader.integer("unEntero", 10, 10, 50));
        assertEquals(10, reader.integer("enteroFuera", 10, 10, 50));
        assertEquals(10, reader.integer("enteroFraccional", 10, 10, 50));
        assertEquals(10, reader.integer("ausenteEntero", 10, 10, 50));

        assertEquals(3.14, reader.decimal("unDecimal", 1.0, 0.0, 10.0), 1e-6);
        assertEquals(1.0, reader.decimal("decimalFuera", 1.0, 0.0, 10.0), 1e-6);
        assertEquals(1.0, reader.decimal("ausenteDecimal", 1.0, 0.0, 10.0), 1e-6);

        assertEquals("hola", reader.string("unTexto", "defecto"));
        assertEquals("defecto", reader.string("textoBlanco", "defecto"));
        assertEquals("defecto", reader.string("ausenteTexto", "defecto"));

        assertEquals("dolfinspace:kepler", reader.identifier("unId", "dolfinspace:defecto"));
        assertEquals("dolfinspace:defecto", reader.identifier("malId", "dolfinspace:defecto"));
        assertEquals("dolfinspace:defecto", reader.identifier("ausenteId", "dolfinspace:defecto"));

        reader.finish();

        assertTrue(problems.stream().anyMatch(p -> p.field().equals("test.malBoolean")));
        assertTrue(problems.stream().anyMatch(p -> p.field().equals("test.enteroFuera")));
        assertTrue(problems.stream().anyMatch(p -> p.field().equals("test.enteroFraccional")));
        assertTrue(problems.stream().anyMatch(p -> p.field().equals("test.decimalFuera")));
        assertTrue(problems.stream().anyMatch(p -> p.field().equals("test.textoBlanco")));
        assertTrue(problems.stream().anyMatch(p -> p.field().equals("test.malId")));
        assertTrue(problems.stream().anyMatch(p -> p.field().equals("test.claveDesconocida")));
    }

    @Test
    void sectionReaderHandlesAliasesWithLegacyPrecedence() {
        JsonObject onlyAlias = new JsonObject();
        onlyAlias.addProperty("legacyKey", 4.5);
        List<SpaceConfig.Problem> aliasProblems = new ArrayList<>();
        SpaceConfig.SectionReader aliasReader =
                new SpaceConfig.SectionReader("oxigeno", onlyAlias, aliasProblems);
        aliasReader.alias("legacyKey", "canonicalKey");
        double val1 = aliasReader.decimal("canonicalKey", 1.0, 0.0, 10.0);
        aliasReader.finish();

        assertEquals(4.5, val1, 1e-6);
        assertTrue(aliasProblems.isEmpty(), "Legacy alias should not be reported as unknown");

        JsonObject both = new JsonObject();
        both.addProperty("canonicalKey", 7.0);
        both.addProperty("legacyKey", 4.5);
        List<SpaceConfig.Problem> bothProblems = new ArrayList<>();
        SpaceConfig.SectionReader bothReader =
                new SpaceConfig.SectionReader("oxigeno", both, bothProblems);
        bothReader.alias("legacyKey", "canonicalKey");
        double val2 = bothReader.decimal("canonicalKey", 1.0, 0.0, 10.0);
        bothReader.finish();

        assertEquals(4.5, val2, 1e-6);
        assertTrue(
                bothProblems.isEmpty(), "Neither key should be unknown when both are registered");
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
