package com.dolfin.creepers.dungeon.fin;

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
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Récords ({@code records.json}) y progreso de jugadores ({@code progreso.json}) de las dungeons,
 * guardados en JSON dentro de la carpeta del mundo (#22, #14). Antes de {@link #cargar} están
 * vacíos. Cada cambio se guarda al momento con {@link #guardar}: los archivos son pequeños y solo
 * cambian al terminar una partida. Un archivo ilegible se aparta como {@code .corrupto} para no
 * perder los datos al sobrescribirlo.
 */
public final class HistorialDungeons {
    public static final String CARPETA = "dolfin-dungeons";
    private static final String ARCHIVO_RECORDS = "records.json";
    private static final String ARCHIVO_PROGRESO = "progreso.json";
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Consumer<String> avisos;
    private Path carpeta;
    private TablaRecords records = new TablaRecords();
    private ProgresoJugadores progreso = new ProgresoJugadores();

    /** {@code avisos} recibe una línea por cada problema de lectura o escritura. */
    public HistorialDungeons(Consumer<String> avisos) {
        this.avisos = avisos;
    }

    public TablaRecords records() {
        return records;
    }

    /** Cooldowns y dungeons completadas por jugador; los lee la validación de entrada (#14). */
    public ProgresoJugadores progreso() {
        return progreso;
    }

    /** Lee los archivos de {@code carpetaMundo/dolfin-dungeons}; los que falten empiezan vacíos. */
    public void cargar(Path carpetaMundo) {
        carpeta = carpetaMundo.resolve(CARPETA);
        records =
                leer(ARCHIVO_RECORDS)
                        .map(o -> TablaRecords.deJson(o, avisos))
                        .orElseGet(TablaRecords::new);
        progreso =
                leer(ARCHIVO_PROGRESO)
                        .map(o -> ProgresoJugadores.deJson(o, avisos))
                        .orElseGet(ProgresoJugadores::new);
    }

    /** Escribe ambos archivos. Sin {@link #cargar} previo no hace nada. */
    public void guardar() {
        if (carpeta == null) {
            return;
        }
        escribir(ARCHIVO_RECORDS, records.aJson());
        escribir(ARCHIVO_PROGRESO, progreso.aJson());
    }

    private Optional<JsonObject> leer(String nombre) {
        Path archivo = carpeta.resolve(nombre);
        if (!Files.exists(archivo)) {
            return Optional.empty();
        }
        try {
            JsonElement leido = JsonParser.parseString(Files.readString(archivo));
            if (leido.isJsonObject()) {
                return Optional.of(leido.getAsJsonObject());
            }
            throw new IllegalStateException("se esperaba un objeto JSON");
        } catch (IOException | RuntimeException e) {
            apartar(archivo, e);
            return Optional.empty();
        }
    }

    private void apartar(Path archivo, Exception causa) {
        Path destino = archivo.resolveSibling(archivo.getFileName() + ".corrupto");
        try {
            Files.move(archivo, destino, StandardCopyOption.REPLACE_EXISTING);
            avisos.accept(archivo + " ilegible (" + causa + "); apartado en " + destino);
        } catch (IOException e) {
            avisos.accept(archivo + " ilegible (" + causa + ") y no se pudo apartar: " + e);
        }
    }

    private void escribir(String nombre, JsonObject valor) {
        try {
            Files.createDirectories(carpeta);
            Path temporal = Files.createTempFile(carpeta, nombre, ".tmp");
            try {
                Files.writeString(temporal, GSON.toJson(valor) + "\n");
                mover(temporal, carpeta.resolve(nombre));
            } finally {
                Files.deleteIfExists(temporal);
            }
        } catch (IOException e) {
            avisos.accept("no se pudo guardar " + nombre + ": " + e);
        }
    }

    private static void mover(Path origen, Path destino) throws IOException {
        try {
            Files.move(
                    origen,
                    destino,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
