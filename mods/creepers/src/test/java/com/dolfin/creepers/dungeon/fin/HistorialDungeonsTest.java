package com.dolfin.creepers.dungeon.fin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.modelo.Dificultad;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class HistorialDungeonsTest {
    @TempDir Path mundo;

    List<String> avisos = new ArrayList<>();

    @Test
    void guardaYRecargaRecordsYProgreso() {
        UUID ana = UUID.randomUUID();
        HistorialDungeons primero = new HistorialDungeons(avisos::add);
        primero.cargar(mundo);
        primero.records()
                .anadir(
                        new Marca(
                                "cripta",
                                Dificultad.MEDIO,
                                List.of(new Marca.Integrante(ana, "Ana")),
                                61_000,
                                1));
        primero.progreso().iniciarCooldown(ana, "cripta", 9_000);
        primero.guardar();

        HistorialDungeons segundo = new HistorialDungeons(avisos::add);
        segundo.cargar(mundo);
        assertEquals(1, segundo.records().tamano());
        assertEquals(
                61_000,
                segundo.records().mejor("cripta", Dificultad.MEDIO).orElseThrow().tiempoMillis());
        assertEquals(8_000, segundo.progreso().cooldownRestanteMillis(ana, "cripta", 1_000));
        assertTrue(avisos.isEmpty(), avisos.toString());
    }

    @Test
    void unArchivoCorruptoSeApartaYNoSePierde() throws IOException {
        Path carpeta = Files.createDirectories(mundo.resolve(HistorialDungeons.CARPETA));
        Files.writeString(carpeta.resolve("records.json"), "{ esto no es json");
        HistorialDungeons historial = new HistorialDungeons(avisos::add);
        historial.cargar(mundo);
        assertEquals(0, historial.records().tamano());
        assertEquals(1, avisos.size());
        assertTrue(Files.exists(carpeta.resolve("records.json.corrupto")));
        assertTrue(historial.records().top("x", Optional.empty(), 1).isEmpty());
    }

    @Test
    void sinCargarGuardarNoHaceNada() {
        new HistorialDungeons(avisos::add).guardar();
        assertTrue(avisos.isEmpty());
    }
}
