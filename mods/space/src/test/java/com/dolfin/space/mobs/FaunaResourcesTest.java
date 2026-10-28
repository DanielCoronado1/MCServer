package com.dolfin.space.mobs;

import static org.junit.jupiter.api.Assertions.*;

import com.dolfin.space.planet.PlanetCatalog;
import com.google.gson.*;

import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.*;

class FaunaResourcesTest {
    private static final Path ROOT = Path.of("src/main/resources");

    private JsonObject json(String path) throws Exception {
        return JsonParser.parseString(Files.readString(ROOT.resolve(path + ".json")))
                .getAsJsonObject();
    }

    @Test
    void allFiveBiomesSpawnAllSevenMobsWithCavernParasiteBias() throws Exception {
        int surfaceWeight = 0, caveWeight = 0;
        for (var biome : PlanetCatalog.BIOMES) {
            var b = json("data/dolfinspace/worldgen/biome/" + biome);
            var spawns =
                    b.getAsJsonObject("attributes")
                            .getAsJsonObject("minecraft:gameplay/natural_mob_spawns")
                            .getAsJsonObject("argument")
                            .getAsJsonObject("spawns_by_category")
                            .getAsJsonArray("monster");
            Set<String> names = new HashSet<>();
            for (var e : spawns) {
                var s = e.getAsJsonObject();
                String name = s.get("type").getAsString();
                names.add(name);
                assertTrue(s.get("weight").getAsInt() > 0);
                var count = s.getAsJsonObject("count");
                if (name.equals("dolfinspace:acechador")) {
                    assertEquals(2, count.get("min_inclusive").getAsInt());
                    assertEquals(3, count.get("max_inclusive").getAsInt());
                }
                if (name.equals("dolfinspace:parasito")) {
                    if (biome.endsWith("caverns")) caveWeight = s.get("weight").getAsInt();
                    else surfaceWeight = s.get("weight").getAsInt();
                }
            }
            assertEquals(
                    new HashSet<>(FaunaRules.MOBS.stream().map(n -> "dolfinspace:" + n).toList()),
                    names);
        }
        assertTrue(caveWeight > surfaceWeight);
    }

    @Test
    void everyMobHasEggTextureTranslationLootAndVanillaRendererBinding() throws Exception {
        var renderer =
                Files.readString(
                        Path.of("src/client/java/com/dolfin/space/client/mobs/MobsClient.java"));
        var lang = json("assets/dolfinspace/lang/es_mx");
        for (var name : FaunaRules.MOBS) {
            assertTrue(lang.has("entity.dolfinspace." + name));
            assertTrue(lang.has("item.dolfinspace." + name + "_spawn_egg"));
            assertNotNull(json("assets/dolfinspace/items/" + name + "_spawn_egg"));
            assertNotNull(json("data/dolfinspace/loot_table/entities/" + name));
            byte[] texture =
                    Files.readAllBytes(
                            ROOT.resolve("assets/dolfinspace/textures/entity/" + name + ".png"));
            assertEquals((byte) 0x89, texture[0]);
            assertTrue(renderer.contains("\"" + name + "\""), name);
            assertTrue(
                    Files.exists(
                            ROOT.resolve("assets/dolfinspace/dolfin/modelos/" + name + ".json")),
                    name);
            assertTrue(
                    Files.exists(
                            ROOT.resolve(
                                    "assets/dolfinspace/textures/entity/"
                                            + name
                                            + "_exterior.png")),
                    name);
        }
        assertTrue(renderer.contains("DesignRenderer"));
        assertTrue(renderer.contains("CreeperDesignRenderer"));
    }

    @Test
    void everyNewDropHasUsefulRecipe() throws Exception {
        var recipes = ROOT.resolve("data/dolfinspace/recipe");
        StringBuilder all = new StringBuilder();
        try (var paths = Files.list(recipes)) {
            for (var p : paths.toList()) all.append(Files.readString(p));
        }
        for (var drop : List.of("quitina_alienigena", "diente_gusano", "nucleo_energia"))
            assertTrue(all.toString().contains("dolfinspace:" + drop));
        assertEquals(
                "minecraft:shield",
                json("data/dolfinspace/recipe/escudo_quitina")
                        .getAsJsonObject("result")
                        .get("id")
                        .getAsString());
        assertEquals(
                "minecraft:iron_pickaxe",
                json("data/dolfinspace/recipe/pico_diente")
                        .getAsJsonObject("result")
                        .get("id")
                        .getAsString());
        assertEquals(
                "dolfinspace:cristal_energia",
                json("data/dolfinspace/recipe/cristales_del_nucleo")
                        .getAsJsonObject("result")
                        .get("id")
                        .getAsString());
    }

    @Test
    void mixinPackagesAreIsolatedFromNormalHelpers() throws Exception {
        for (var file :
                List.of(
                        "dolfinspace.oxygen.mixins",
                        "dolfinspace.oxygen.client.mixins",
                        "dolfinspace.mobs.mixins"))
            assertTrue(json(file).get("package").getAsString().endsWith(".mixin"));
    }
}
