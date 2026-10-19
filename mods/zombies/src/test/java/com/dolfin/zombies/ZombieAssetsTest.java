package com.dolfin.zombies;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.*;
import java.security.MessageDigest;
import java.util.*;

import javax.imageio.ImageIO;

/** Validate the packaged resource graph, not only files in the generator's output directory. */
class ZombieAssetsTest {
    private byte[] bytes(String path) throws Exception {
        try (var in = getClass().getResourceAsStream("/assets/dolfinzombies/" + path)) {
            assertNotNull(in, path);
            return in.readAllBytes();
        }
    }

    private JsonObject json(String path) throws Exception {
        return JsonParser.parseString(
                        new String(bytes(path), java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private java.awt.image.BufferedImage image(String path, int width, int height)
            throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(bytes(path)));
        assertNotNull(image, path);
        assertEquals(width, image.getWidth(), path);
        assertEquals(height, image.getHeight(), path);
        return image;
    }

    @Test
    void allSixteenHaveDistinct64PixelSkins() throws Exception {
        assertEquals(16, ZombieKind.values().length);
        Set<String> hashes = new HashSet<>();
        for (var kind : ZombieKind.values()) {
            String path = "textures/entity/" + kind.id() + ".png";
            image(path, 64, 64);
            assertTrue(
                    hashes.add(
                            HexFormat.of()
                                    .formatHex(
                                            MessageDigest.getInstance("SHA-256")
                                                    .digest(bytes(path)))),
                    kind.id());
            image("textures/entity/" + kind.id() + "_accessories.png", 64, 64);
        }
    }

    @Test
    void allFourLocalesNameEntitiesEggsAndSounds() throws Exception {
        for (String locale : List.of("en_us", "es_mx", "es_es", "es_ar")) {
            var lang = json("lang/" + locale + ".json");
            Set<String> names = new HashSet<>();
            for (var kind : ZombieKind.values())
                for (String key :
                        List.of(
                                "entity.dolfinzombies." + kind.id(),
                                "item.dolfinzombies." + kind.id() + "_spawn_egg")) {
                    assertTrue(lang.has(key), locale + ":" + key);
                    assertFalse(lang.get(key).getAsString().isBlank());
                    assertTrue(names.add(lang.get(key).getAsString()), locale + ":" + key);
                }
            for (var sound : json("sounds.json").entrySet())
                assertTrue(
                        lang.has(sound.getValue().getAsJsonObject().get("subtitle").getAsString()),
                        locale + ":" + sound.getKey());
        }
    }

    @Test
    void everyEggReferencesItsOwnModelAndDistinctColoredSprite() throws Exception {
        Set<String> hashes = new HashSet<>();
        for (var kind : ZombieKind.values()) {
            String id = kind.id() + "_spawn_egg";
            var model = json("items/" + id + ".json").getAsJsonObject("model");
            assertEquals("minecraft:model", model.get("type").getAsString());
            assertEquals("dolfinzombies:item/" + id, model.get("model").getAsString());
            var geometry = json("models/item/" + id + ".json");
            assertEquals("minecraft:item/generated", geometry.get("parent").getAsString());
            assertEquals(
                    "dolfinzombies:item/" + id,
                    geometry.getAsJsonObject("textures").get("layer0").getAsString());
            String texture = "textures/item/" + id + ".png";
            var egg = image(texture, 32, 32);
            assertEquals(0, egg.getRGB(0, 0) >>> 24);
            assertEquals(255, egg.getRGB(15, 15) >>> 24);
            assertTrue(
                    hashes.add(
                            HexFormat.of()
                                    .formatHex(
                                            MessageDigest.getInstance("SHA-256")
                                                    .digest(bytes(texture)))));
        }
    }

    @Test
    void emissionHasTransparentBackgroundAndOnlySelectedPixels() throws Exception {
        for (String id :
                List.of("minero", "bombero", "abismo", "enano", "contagiado", "explorador")) {
            var glow = image("textures/entity/" + id + "_zombie_eyes.png", 64, 64);
            int opaque = 0;
            for (int y = 0; y < 64; y++)
                for (int x = 0; x < 64; x++) if ((glow.getRGB(x, y) >>> 24) > 0) opaque++;
            assertTrue(opaque >= 4 && opaque < 512, id + ":" + opaque);
            if (id.equals("explorador")) {
                assertEquals(4, opaque);
                assertEquals(0xffff2434, glow.getRGB(9, 11));
            }
        }
        for (String id : List.of("minero", "enano"))
            image("textures/entity/" + id + "_zombie_accessories_emissive.png", 64, 64);
        image("textures/entity/mimo_zombie_disguise.png", 64, 64);
    }

    @Test
    void fourSoundDefinitionsResolveToMonoVorbisWithValidOggChecksums() throws Exception {
        var definitions = json("sounds.json");
        assertEquals(
                Set.of("griton_scream", "bilioso_burst", "abismo_grab", "ladron_steal"),
                definitions.keySet());
        Set<String> hashes = new HashSet<>();
        for (String id : definitions.keySet()) {
            var sound =
                    definitions
                            .getAsJsonObject(id)
                            .getAsJsonArray("sounds")
                            .get(0)
                            .getAsJsonObject();
            assertEquals("dolfinzombies:" + id, sound.get("name").getAsString());
            byte[] data = bytes("sounds/" + id + ".ogg");
            assertTrue(data.length > 1000);
            assertTrue(
                    hashes.add(
                            HexFormat.of()
                                    .formatHex(MessageDigest.getInstance("SHA-256").digest(data))));
            int offset = 0;
            long lastGranule = 0;
            boolean identification = false;
            while (offset < data.length) {
                assertEquals(
                        "OggS",
                        new String(data, offset, 4, java.nio.charset.StandardCharsets.US_ASCII));
                int segments = data[offset + 26] & 255;
                int size = 27 + segments;
                for (int i = 0; i < segments; i++) size += data[offset + 27 + i] & 255;
                assertTrue(offset + size <= data.length);
                int
                        stored =
                                ByteBuffer.wrap(data, offset + 22, 4)
                                        .order(ByteOrder.LITTLE_ENDIAN)
                                        .getInt(),
                        crc = 0;
                for (int i = 0; i < size; i++) {
                    int v = i >= 22 && i < 26 ? 0 : data[offset + i] & 255;
                    crc ^= v << 24;
                    for (int bit = 0; bit < 8; bit++)
                        crc = (crc << 1) ^ ((crc & 0x80000000) != 0 ? 0x04c11db7 : 0);
                }
                assertEquals(stored, crc, id);
                int payload = offset + 27 + segments;
                if (!identification) {
                    assertEquals(1, data[payload]);
                    assertEquals(
                            "vorbis",
                            new String(
                                    data,
                                    payload + 1,
                                    6,
                                    java.nio.charset.StandardCharsets.US_ASCII));
                    assertEquals(1, data[payload + 11], "mono positional audio");
                    assertEquals(
                            22050,
                            ByteBuffer.wrap(data, payload + 12, 4)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .getInt());
                    identification = true;
                }
                lastGranule =
                        ByteBuffer.wrap(data, offset + 6, 8)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .getLong();
                offset += size;
            }
            assertTrue(lastGranule > 8000 && lastGranule < 44100, id + ":" + lastGranule);
        }
    }
}
