package com.dolfin.space.planet;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanetResourcesTest {
    private static final Path ROOT=Path.of("src/main/resources/data/dolfinspace");
    private JsonObject json(String path) throws Exception { return JsonParser.parseString(Files.readString(ROOT.resolve(path+".json"))).getAsJsonObject(); }
    @Test void fiveBiomesAreReachableWithDistinctAtmospheresAndFeatures() throws Exception {
        var source=json("dimension/kepler").getAsJsonObject("generator").getAsJsonObject("biome_source");
        assertEquals("minecraft:multi_noise",source.get("type").getAsString());
        Set<String> wired=new HashSet<>(),skies=new HashSet<>(),fog=new HashSet<>(),particles=new HashSet<>();
        for (var b:source.getAsJsonArray("biomes")) {
            var entry=b.getAsJsonObject();wired.add(entry.get("biome").getAsString());
            assertTrue(entry.getAsJsonObject("parameters").has("depth"));
        }
        assertEquals(5,wired.size());
        for (String id:PlanetCatalog.BIOMES) {
            assertTrue(wired.contains("dolfinspace:"+id)); var b=json("worldgen/biome/"+id);
            assertFalse(b.get("has_precipitation").getAsBoolean());
            var a=b.getAsJsonObject("attributes");skies.add(a.get("minecraft:visual/sky_color").getAsString());fog.add(a.get("minecraft:visual/fog_color").getAsString());
            particles.add(a.getAsJsonObject("minecraft:visual/ambient_particles").toString());
            var steps=b.getAsJsonArray("features");assertEquals(11,steps.size());assertFalse(steps.get(6).getAsJsonArray().isEmpty());
            for(var step:steps) for(var feature:step.getAsJsonArray()) {
                String name=feature.getAsString().split(":")[1];
                var placed=json("worldgen/placed_feature/"+name);
                assertTrue(placed.get("feature").getAsString().startsWith("dolfinspace:"));
                assertTrue(json("worldgen/feature/"+name).has("type"));
            }
        }
        assertEquals(5,skies.size()); assertEquals(5,fog.size()); assertEquals(5,particles.size());
    }
    @Test void climateNoiseAndMaterialRulesCoverAllPalettes() throws Exception {
        var n=json("worldgen/noise_settings/kepler");assertEquals("dolfinspace:regolito",n.get("default_block").getAsString());
        assertEquals("dolfinspace:kepler",n.get("material_rule").getAsString());
        var r=n.getAsJsonObject("noise_router");assertNotEquals("0.0",r.get("temperature").toString());assertTrue(r.get("depth").isJsonObject());
        var surface=json("worldgen/material_rule/kepler").toString();for(String b:PlanetCatalog.BIOMES) assertTrue(surface.contains(b));
        assertTrue(surface.contains("lodo_acido"));assertTrue(surface.contains("roca_lunar"));assertTrue(surface.contains("bedrock"));
    }
    @Test void oreDropsUseActual26Point3FortuneAndSilkTouchFormat() throws Exception {
        for(String ore:List.of("mena_titanio","mena_hierro_meteorico","mena_cristal_energia")) {
            var table=json("loot_table/blocks/"+ore);
            var children=table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("children");
            assertEquals("minecraft:tool/can_silk_touch",children.get(0).getAsJsonObject().get("condition").getAsString());
            var bonus=children.get(1).getAsJsonObject().getAsJsonArray("modifier").get(0).getAsJsonObject();
            assertEquals("minecraft:apply_bonus",bonus.get("type").getAsString());assertEquals("minecraft:fortune",bonus.get("enchantment").getAsString());
            assertEquals("minecraft:ore_drops",bonus.get("formula").getAsString());
        }
    }
    @Test void registeredManifestHasAssetsLootAndIntegrationTags() throws Exception {
        for(String id:PlanetCatalog.BLOCKS) { assertNotNull(json("loot_table/blocks/"+id));assertTrue(Files.exists(ROOT.resolve("../../assets/dolfinspace/blockstates/"+id+".json").normalize())); }
        for(String id:PlanetCatalog.ITEMS) assertTrue(Files.exists(ROOT.resolve("../../assets/dolfinspace/items/"+id+".json").normalize()));
        assertTrue(json("tags/item/cristales_energia").toString().contains("dolfinspace:cristal_energia"));assertTrue(json("tags/item/lingotes_titanio").toString().contains("dolfinspace:lingote_titanio"));
        var tool=Files.readString(Path.of("src/main/resources/data/minecraft/tags/block/needs_iron_tool.json"));assertTrue(tool.contains("mena_titanio"));assertTrue(tool.contains("mena_cristal_energia"));
    }
    @Test void acidPoisonIsServerOnlyAndHonorsPlayerExemptions() {
        assertEquals(60,PlanetCatalog.ACID_DURATION_TICKS);assertEquals(1,PlanetCatalog.ACID_AMPLIFIER);
        assertTrue(PlanetCatalog.acidAffects(true,false,false,true));
        assertFalse(PlanetCatalog.acidAffects(true,true,false,true));assertFalse(PlanetCatalog.acidAffects(true,false,true,true));
        assertFalse(PlanetCatalog.acidAffects(true,false,false,false));assertFalse(PlanetCatalog.acidAffects(false,false,false,true));
    }
}
