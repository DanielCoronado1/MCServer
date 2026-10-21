package com.dolfin.space;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

class MixinPackagesTest {
    @Test
    void reservedMixinPackagesContainOnlyDeclaredMixins() throws Exception {
        Path resources = Path.of("src/main/resources");
        var manifest =
                JsonParser.parseString(Files.readString(resources.resolve("fabric.mod.json")))
                        .getAsJsonObject();
        for (var entry : manifest.getAsJsonArray("mixins")) {
            String name =
                    entry.isJsonPrimitive()
                            ? entry.getAsString()
                            : entry.getAsJsonObject().get("config").getAsString();
            var config =
                    JsonParser.parseString(Files.readString(resources.resolve(name)))
                            .getAsJsonObject();
            String reserved = config.get("package").getAsString().replace('.', '/');
            Set<String> declared = new HashSet<>();
            for (String side : new String[] {"mixins", "client", "server"}) {
                if (!config.has(side)) continue;
                for (var mixin : config.getAsJsonArray(side)) {
                    declared.add(reserved + "/" + mixin.getAsString().replace('.', '/') + ".java");
                }
            }
            for (String side : new String[] {"main", "client"}) {
                Path source = Path.of("src", side, "java");
                Path directory = source.resolve(reserved);
                if (!Files.exists(directory)) continue;
                try (var files = Files.walk(directory)) {
                    for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                        String relative = source.relativize(file).toString().replace('\\', '/');
                        assertTrue(
                                declared.contains(relative),
                                () ->
                                        name
                                                + " reserves a package containing non-mixin "
                                                + relative);
                    }
                }
            }
        }
    }
}
