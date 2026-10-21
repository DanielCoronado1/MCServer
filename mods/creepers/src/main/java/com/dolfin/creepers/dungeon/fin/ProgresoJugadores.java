package com.dolfin.creepers.dungeon.fin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Lo que se recuerda de cada jugador entre partidas, sin Minecraft: cooldown por dungeon, dungeons
 * completadas y cabezas de jefe pendientes de entregar a quien estaba desconectado. Es el almacén
 * del que leen los requisitos de entrada (#14): {@link #cooldownRestanteMillis} y {@link
 * #completo}. Lo escribe el fin de partida (#17) y se guarda con {@link #aJson}.
 */
public final class ProgresoJugadores {
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Set<String>> completadas = new HashMap<>();
    private final Map<UUID, Set<String>> cabezasPendientes = new HashMap<>();

    /** El jugador no puede volver a entrar a {@code dungeon} hasta {@code hastaMillis}. */
    public void iniciarCooldown(UUID jugador, String dungeon, long hastaMillis) {
        cooldowns.computeIfAbsent(jugador, k -> new HashMap<>()).put(dungeon, hastaMillis);
    }

    /** Milisegundos que le faltan para volver a entrar; 0 si no tiene cooldown. */
    public long cooldownRestanteMillis(UUID jugador, String dungeon, long ahoraMillis) {
        long hasta = cooldowns.getOrDefault(jugador, Map.of()).getOrDefault(dungeon, 0L);
        return Math.max(0, hasta - ahoraMillis);
    }

    public void marcarCompletada(UUID jugador, String dungeon) {
        completadas.computeIfAbsent(jugador, k -> new HashSet<>()).add(dungeon);
    }

    /** Ganó {@code dungeon} alguna vez, en cualquier dificultad. */
    public boolean completo(UUID jugador, String dungeon) {
        return completadas.getOrDefault(jugador, Set.of()).contains(dungeon);
    }

    public void pendienteCabeza(UUID jugador, String dungeon) {
        cabezasPendientes.computeIfAbsent(jugador, k -> new HashSet<>()).add(dungeon);
    }

    /** Quita y devuelve las dungeons cuya cabeza de jefe debe entregarse al jugador. */
    public Set<String> recogerCabezas(UUID jugador) {
        Set<String> dungeons = cabezasPendientes.remove(jugador);
        return dungeons == null ? Set.of() : dungeons;
    }

    /** Descarta cooldowns vencidos para que el archivo no crezca sin límite. */
    public void limpiarVencidos(long ahoraMillis) {
        cooldowns.values().forEach(m -> m.values().removeIf(hasta -> hasta <= ahoraMillis));
        cooldowns.values().removeIf(Map::isEmpty);
    }

    /** Documento guardado en disco. */
    public JsonObject aJson() {
        JsonObject raiz = new JsonObject();
        JsonObject cd = new JsonObject();
        cooldowns.forEach(
                (jugador, porDungeon) -> {
                    JsonObject o = new JsonObject();
                    porDungeon.forEach(o::addProperty);
                    cd.add(jugador.toString(), o);
                });
        raiz.add("cooldowns", cd);
        raiz.add("completadas", conjuntos(completadas));
        raiz.add("cabezasPendientes", conjuntos(cabezasPendientes));
        return raiz;
    }

    /** Lee un documento; las entradas inválidas se descartan y se informan en {@code avisos}. */
    public static ProgresoJugadores deJson(JsonObject raiz, Consumer<String> avisos) {
        ProgresoJugadores progreso = new ProgresoJugadores();
        leerMapa(
                raiz,
                "cooldowns",
                avisos,
                (jugador, valor) ->
                        valor.getAsJsonObject()
                                .entrySet()
                                .forEach(
                                        e ->
                                                progreso.iniciarCooldown(
                                                        jugador,
                                                        e.getKey(),
                                                        e.getValue().getAsLong())));
        leerMapa(
                raiz,
                "completadas",
                avisos,
                (jugador, valor) ->
                        valor.getAsJsonArray()
                                .forEach(d -> progreso.marcarCompletada(jugador, d.getAsString())));
        leerMapa(
                raiz,
                "cabezasPendientes",
                avisos,
                (jugador, valor) ->
                        valor.getAsJsonArray()
                                .forEach(d -> progreso.pendienteCabeza(jugador, d.getAsString())));
        return progreso;
    }

    private interface LectorJugador {
        void leer(UUID jugador, JsonElement valor);
    }

    private static void leerMapa(
            JsonObject raiz, String clave, Consumer<String> avisos, LectorJugador lector) {
        JsonElement seccion = raiz.get(clave);
        if (seccion == null || !seccion.isJsonObject()) {
            return;
        }
        for (var entrada : seccion.getAsJsonObject().entrySet()) {
            try {
                lector.leer(UUID.fromString(entrada.getKey()), entrada.getValue());
            } catch (RuntimeException e) {
                avisos.accept(clave + "." + entrada.getKey() + " descartado: " + e);
            }
        }
    }

    private static JsonObject conjuntos(Map<UUID, Set<String>> mapa) {
        JsonObject o = new JsonObject();
        mapa.forEach(
                (jugador, dungeons) -> {
                    JsonArray lista = new JsonArray();
                    dungeons.stream().sorted().forEach(lista::add);
                    o.add(jugador.toString(), lista);
                });
        return o;
    }
}
