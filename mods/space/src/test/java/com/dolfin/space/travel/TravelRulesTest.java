package com.dolfin.space.travel;

import static org.junit.jupiter.api.Assertions.*;

import com.dolfin.space.SpaceConfig;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import java.util.*;

class TravelRulesTest {
    private final SpaceConfig.ShipsSection cfg = SpaceConfig.ShipsSection.defaults();

    @Test
    void mandatoryChecklistIgnoresOnlyOxygenWarning() {
        var ok = LaunchRules.check(true, true, 100, true, true, false, cfg);
        assertTrue(ok.allowed());
        assertTrue(ok.tankWarning());
        assertFalse(LaunchRules.check(true, false, 100, true, true, true, cfg).allowed());
        assertFalse(LaunchRules.check(true, true, 100, false, true, true, cfg).allowed());
        assertFalse(LaunchRules.check(true, true, 100, true, false, true, cfg).allowed());
    }

    @Test
    void earthRequiresFullTankButEachLegCostsHalf() {
        assertFalse(LaunchRules.check(true, true, 99, true, true, true, cfg).allowed());
        int remaining = LaunchRules.deduct(100, cfg.porcentajeCombustiblePorViaje());
        assertEquals(50, remaining);
        assertTrue(LaunchRules.check(false, true, remaining, true, true, true, cfg).allowed());
        assertEquals(0, LaunchRules.deduct(remaining, 50));
        assertFalse(LaunchRules.check(false, true, 49, true, true, true, cfg).allowed());
        assertThrows(IllegalArgumentException.class, () -> LaunchRules.deduct(49, 50));
    }

    @Test
    void landingRejectsLavaWaterAcidAndUnsafeFootprints() {
        for (String id :
                List.of(
                        "minecraft:lava",
                        "minecraft:water",
                        "dolfinspace:acido",
                        "dolfinspace:acid",
                        "minecraft:magma_block")) {
            SafeLanding.Terrain t =
                    (x, y, z) ->
                            y == 0
                                    ? new SafeLanding.Cell(
                                            true,
                                            false,
                                            false,
                                            x == 0 && z == 0 ? id : "minecraft:stone")
                                    : new SafeLanding.Cell(false, true, false, "minecraft:air");
            assertFalse(SafeLanding.safe(t, 0, 1, 0));
            assertTrue(SafeLanding.search(0, 0, 8, (x, z) -> 1, t).isPresent());
        }
        SafeLanding.Terrain ocean =
                (x, y, z) -> new SafeLanding.Cell(false, false, true, "minecraft:water");
        assertTrue(SafeLanding.search(0, 0, 4, (x, z) -> 1, ocean).isEmpty());
        SafeLanding.Terrain overhang =
                (x, y, z) ->
                        y == 0 || y == 3
                                ? new SafeLanding.Cell(true, false, false, "stone")
                                : new SafeLanding.Cell(false, true, false, "air");
        assertFalse(SafeLanding.safe(overhang, 0, 1, 0));
    }

    @Test
    void landingClearanceFitsTheTallestShip() {
        for (var tier : com.dolfin.space.ships.ShipTier.values())
            assertTrue(
                    com.dolfin.space.ships.ShipDesign.of(tier).height() <= SafeLanding.CLEARANCE,
                    tier + " taller than landing clearance");
        SafeLanding.Terrain lowCeiling =
                (x, y, z) ->
                        y == 0 || y == SafeLanding.CLEARANCE
                                ? new SafeLanding.Cell(true, false, false, "stone")
                                : new SafeLanding.Cell(false, true, false, "air");
        assertFalse(SafeLanding.safe(lowCeiling, 0, 1, 0));
    }

    @Test
    void landingLoadsWholeFootprintBeforeReadingHeight() {
        assertEquals(
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                SafeLanding.HEIGHTMAP);
        var loaded = new HashSet<String>();
        java.util.function.BiConsumer<Integer, Integer> load = (x, z) -> loaded.add(x + ":" + z);
        java.util.function.IntBinaryOperator height =
                (x, z) -> {
                    assertTrue(
                            loaded.contains((x >> 4) + ":" + (z >> 4)),
                            "height cannot sample an unloaded chunk");
                    return 1;
                };
        SafeLanding.Terrain terrain =
                (x, y, z) -> {
                    if (!loaded.contains((x >> 4) + ":" + (z >> 4)))
                        return new SafeLanding.Cell(false, false, false, "unloaded");
                    return new SafeLanding.Cell(y == 0, y != 0, false, y == 0 ? "stone" : "air");
                };
        var landing =
                SafeLanding.search(
                        0, 0, 0, (x, z) -> SafeLanding.preparedHeight(x, z, load, height), terrain);
        assertTrue(landing.isPresent());
        assertEquals(4, loaded.size());
    }

    @Test
    void savedKeplerTerrainRegressionUsesSurfaceAboveRegolitoNotBedrock() throws Exception {
        var fixture =
                JsonParser.parseString(
                                java.nio.file.Files.readString(
                                        java.nio.file.Path.of(
                                                "src/test/resources/com/dolfin/space/travel/kepler-surface.json")))
                        .getAsJsonObject();
        var grid = fixture.getAsJsonArray("worldSurface");
        java.util.function.IntBinaryOperator heights =
                (x, z) -> grid.get(x + 4).getAsJsonArray().get(z + 4).getAsInt();
        assertEquals(
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                SafeLanding.HEIGHTMAP);
        assertTrue(heights.applyAsInt(0, 0) > fixture.get("motionBlockingOrigin").getAsInt() + 64);
        int y = SafeLanding.emergencyHeight(0, 0, -64, 320, heights);
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) assertTrue(y >= heights.applyAsInt(x, z) + 4);
        SafeLanding.Terrain prepared =
                (x, yy, z) ->
                        yy == y - 1
                                ? new SafeLanding.Cell(true, false, false, "minecraft:smooth_stone")
                                : new SafeLanding.Cell(
                                        yy < heights.applyAsInt(x, z),
                                        yy >= heights.applyAsInt(x, z),
                                        false,
                                        yy < heights.applyAsInt(x, z)
                                                ? "dolfinspace:regolito"
                                                : "minecraft:air");
        assertTrue(SafeLanding.safe(prepared, 0, y, 0));
    }

    @Test
    void cameraAccessibilityClampsAndZeroDisables() {
        assertEquals(0, LaunchRules.shake(1, 0));
        assertEquals(.25, LaunchRules.shake(.5, .5));
        assertEquals(1, LaunchRules.shake(2, 3));
        assertEquals(0, LaunchRules.shake(Double.NaN, 1));
    }

    @Test
    void journalSurvivesRestartAndPreservesRidersAndDestination() {
        var j =
                new FlightJournal(
                        FlightJournal.Phase.ASCENT,
                        100,
                        65,
                        1234,
                        "dolfinspace:kepler",
                        List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        var json = FlightJournal.CODEC.encodeStart(JsonOps.INSTANCE, j).getOrThrow();
        assertEquals(
                j,
                FlightJournal.CODEC
                        .parse(JsonOps.INSTANCE, JsonParser.parseString(json.toString()))
                        .getOrThrow());
        assertEquals(99, j.step().ticks());
        assertEquals(FlightJournal.Phase.DESCENT, j.phase(FlightJournal.Phase.DESCENT, 80).phase());
    }

    @Test
    void disconnectAndDeathRecoveryPolicy() {
        for (var phase : FlightJournal.Phase.values()) {
            assertEquals(
                    phase == FlightJournal.Phase.COUNTDOWN, FlightJournal.cancelOnDismount(phase));
            assertEquals(
                    phase != FlightJournal.Phase.COUNTDOWN,
                    FlightJournal.completeOnReconnect(phase));
        }
        assertTrue(FlightJournal.autoReturn(true, true, true));
        assertFalse(FlightJournal.autoReturn(false, true, true));
        assertFalse(FlightJournal.autoReturn(true, false, true));
        assertFalse(FlightJournal.autoReturn(true, true, false));
    }

    @Test
    void fullStateSequenceChargesExactlyOnceAndCanCancelBeforeLiftoff() {
        var j =
                new FlightJournal(
                        FlightJournal.Phase.COUNTDOWN,
                        200,
                        65,
                        1234,
                        "dolfinspace:kepler",
                        List.of("pilot"));
        int fuel = 100;
        assertEquals(FlightJournal.Action.CANCEL, j.action(false));
        while (j.action(true) == FlightJournal.Action.WAIT) j = j.step();
        assertEquals(100, fuel);
        assertEquals(FlightJournal.Action.LIFTOFF, j.action(true));
        fuel = LaunchRules.deduct(fuel, 50);
        j = j.phase(FlightJournal.Phase.ASCENT, 200);
        while (j.action(true) == FlightJournal.Action.ASCEND) j = j.step();
        assertEquals(FlightJournal.Action.STARS, j.action(true));
        j = j.phase(FlightJournal.Phase.TRANSITION, 20);
        while (j.ticks() > 1) j = j.step();
        assertEquals(FlightJournal.Action.TRANSFER, j.action(true));
        var encoded =
                FlightJournal.CODEC
                        .encodeStart(JsonOps.INSTANCE, j.phase(FlightJournal.Phase.DESCENT, 80))
                        .getOrThrow();
        j = FlightJournal.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        while (j.action(true) == FlightJournal.Action.DESCEND) j = j.step();
        assertEquals(FlightJournal.Action.LAND, j.action(true));
        assertEquals(50, fuel);
    }

    @Test
    void ledgerPendingArrivalsAreIdempotentAndPersisted() {
        var entry =
                new FlightLedger.Entry(
                        "ship", "owner", "dolfinspace:kepler", 123, true, false, List.of());
        entry = entry.pending("pilot").pending("pilot").pending("passenger");
        assertEquals(2, entry.pending().size());
        var json = FlightLedger.Entry.CODEC.encodeStart(JsonOps.INSTANCE, entry).getOrThrow();
        var loaded = FlightLedger.Entry.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(entry, loaded);
        assertTrue(loaded.returning().autoReturn());
        assertEquals(List.of("passenger"), loaded.arrived("pilot").pending());
    }
}
