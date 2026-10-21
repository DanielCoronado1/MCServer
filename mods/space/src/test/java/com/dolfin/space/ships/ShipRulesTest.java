package com.dolfin.space.ships;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import java.util.*;

class ShipRulesTest {
    @Test
    void platformsRequireSizeAndCenterAndSurviveBrokenBlockValidation() {
        assertTrue(
                PlatformRules.valid(
                        ShipTier.BASICA, (x, z) -> Math.abs(x) <= 1 && Math.abs(z) <= 1));
        assertFalse(
                PlatformRules.valid(
                        ShipTier.CARGA, (x, z) -> Math.abs(x) <= 1 && Math.abs(z) <= 1));
        assertTrue(
                PlatformRules.valid(
                        ShipTier.CARGA, (x, z) -> Math.abs(x) <= 2 && Math.abs(z) <= 2));
        assertFalse(
                PlatformRules.valid(
                        ShipTier.BASICA,
                        (x, z) -> Math.abs(x) <= 1 && Math.abs(z) <= 1 && !(x == 1 && z == 1)));
        assertFalse(
                PlatformRules.valid(
                        ShipTier.CARGA, (x, z) -> Math.abs(x - 1) <= 2 && Math.abs(z) <= 2));
    }

    @Test
    void exactAssemblyTemplatesAndSlotRules() {
        for (var tier : ShipTier.values()) {
            var ids = new ArrayList<String>();
            var counts = new ArrayList<Integer>();
            for (int i = 0; i < 7; i++) {
                ids.add(i < tier.template.size() ? tier.template.get(i).id() : "");
                counts.add(i < tier.template.size() ? tier.template.get(i).count() : 0);
            }
            assertTrue(tier.complete(ids, counts));
            counts.set(0, 0);
            assertFalse(tier.complete(ids, counts));
            counts.set(0, 1);
            ids.set(0, "minecraft:dirt");
            assertFalse(tier.complete(ids, counts));
            assertFalse(tier.accepts(7, "dolfinspace:punta_conica"));
        }
        assertEquals(14, ShipTier.CARGA.template.get(1).count());
        assertEquals(2, ShipTier.RAPIDA.template.get(6).count());
    }

    @Test
    void fuelTransferDoesNotWasteFullCanisters() {
        assertEquals(25, ShipState.transfer(75, 1, 1));
        assertEquals(0, ShipState.transfer(76, 1, 1));
        assertEquals(0, ShipState.transfer(0, 0, 1));
        assertEquals(0, ShipState.transfer(0, 1, 0));
    }

    @Test
    void shipCodecRoundTripPreservesOwnershipFuelOriginAndConsent() {
        var original = new ShipState(UUID.randomUUID().toString(), 1, 75, 123456789L, true, true);
        var json = ShipState.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        assertEquals(
                original,
                ShipState.CODEC
                        .parse(JsonOps.INSTANCE, JsonParser.parseString(json.toString()))
                        .getOrThrow());
        assertEquals(100, new ShipState("", 99, 999, 0, false, false).fuel());
    }

    @Test
    void homeDimensionMigratesOldSavesAndRoundTripsNewOrigins() {
        var legacy =
                JsonParser.parseString(
                        "{\"owner\":\"\",\"tier\":0,\"fuel\":100,\"home\":42,\"hasHome\":true,\"passengersAllowed\":false}");
        var old = ShipState.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals("minecraft:overworld", old.homeDimension());
        var showcase =
                new ShipState(
                        old.owner(),
                        old.tier(),
                        old.fuel(),
                        old.home(),
                        old.hasHome(),
                        old.passengersAllowed(),
                        "dolfin:showcase");
        var encoded = ShipState.CODEC.encodeStart(JsonOps.INSTANCE, showcase).getOrThrow();
        assertEquals(showcase, ShipState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }
}
