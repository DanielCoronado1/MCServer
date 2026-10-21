package com.dolfin.creepers.dungeon.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Lee campos tipados de un objeto JSON. Cada campo ausente toma su valor por defecto en silencio;
 * cada campo inválido o fuera de rango toma su valor por defecto y deja un {@link
 * DungeonsConfig.Problema}, sin afectar a sus hermanos. {@link #terminar()} avisa de claves
 * desconocidas (normalmente erratas).
 */
public final class LectorSeccion {
    private final String ruta;
    private final JsonObject objeto;
    private final List<DungeonsConfig.Problema> problemas;
    private final Set<String> conocidas = new HashSet<>();

    public LectorSeccion(String ruta, JsonObject objeto, List<DungeonsConfig.Problema> problemas) {
        this.ruta = ruta;
        this.objeto = objeto != null ? objeto : new JsonObject();
        this.problemas = problemas;
    }

    /** Lector de la sección {@code clave} de {@code raiz}; vacío si falta o no es un objeto. */
    public static LectorSeccion de(
            JsonObject raiz, String clave, List<DungeonsConfig.Problema> problemas) {
        JsonElement elemento = raiz.get(clave);
        if (elemento != null && !elemento.isJsonObject()) {
            problemas.add(new DungeonsConfig.Problema(clave, "se esperaba un objeto"));
            elemento = null;
        }
        return new LectorSeccion(
                clave, elemento == null ? null : elemento.getAsJsonObject(), problemas);
    }

    public String ruta() {
        return ruta;
    }

    public boolean booleano(String clave, boolean porDefecto) {
        JsonElement valor = leer(clave);
        if (valor == null) {
            return porDefecto;
        }
        if (!valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isBoolean()) {
            return rechazar(clave, "se esperaba true o false", porDefecto);
        }
        return valor.getAsBoolean();
    }

    public int entero(String clave, int porDefecto, int min, int max) {
        JsonElement valor = leer(clave);
        if (valor == null) {
            return porDefecto;
        }
        String rango = "se esperaba un entero en [" + min + ", " + max + "], llegó " + valor;
        if (!valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isNumber()) {
            return rechazar(clave, rango, porDefecto);
        }
        double numero = valor.getAsDouble();
        if (!Double.isFinite(numero)
                || numero != Math.rint(numero)
                || numero < min
                || numero > max) {
            return rechazar(clave, rango, porDefecto);
        }
        return (int) numero;
    }

    public double decimal(String clave, double porDefecto, double min, double max) {
        JsonElement valor = leer(clave);
        if (valor == null) {
            return porDefecto;
        }
        String rango = "se esperaba un número en [" + min + ", " + max + "], llegó " + valor;
        if (!valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isNumber()) {
            return rechazar(clave, rango, porDefecto);
        }
        double numero = valor.getAsDouble();
        if (!Double.isFinite(numero) || numero < min || numero > max) {
            return rechazar(clave, rango, porDefecto);
        }
        return numero;
    }

    public String texto(String clave, String porDefecto) {
        JsonElement valor = leer(clave);
        if (valor == null) {
            return porDefecto;
        }
        if (!valor.isJsonPrimitive()
                || !valor.getAsJsonPrimitive().isString()
                || valor.getAsString().isBlank()) {
            return rechazar(clave, "se esperaba un texto no vacío", porDefecto);
        }
        return valor.getAsString();
    }

    /**
     * Lista de textos que cumplen {@code patron}. Un elemento inválido invalida la lista entera
     * para no aplicar a medias una lista de seguridad como {@code objetosProhibidos}.
     */
    public List<String> textos(String clave, List<String> porDefecto, String patron) {
        JsonElement valor = leer(clave);
        if (valor == null) {
            return porDefecto;
        }
        String motivo = "se esperaba una lista de textos con formato " + patron;
        if (!valor.isJsonArray()) {
            return rechazar(clave, motivo, porDefecto);
        }
        List<String> lista = new ArrayList<>();
        for (JsonElement elemento : valor.getAsJsonArray()) {
            if (!elemento.isJsonPrimitive()
                    || !elemento.getAsJsonPrimitive().isString()
                    || !elemento.getAsString().matches(patron)) {
                return rechazar(clave, motivo + ", llegó " + elemento, porDefecto);
            }
            lista.add(elemento.getAsString());
        }
        return List.copyOf(lista);
    }

    /** Sub-objeto opcional; inválido deja un problema y devuelve vacío. */
    public Optional<JsonObject> objeto(String clave) {
        JsonElement valor = leer(clave);
        if (valor == null) {
            return Optional.empty();
        }
        if (!valor.isJsonObject()) {
            return rechazar(clave, "se esperaba un objeto", Optional.empty());
        }
        return Optional.of(valor.getAsJsonObject());
    }

    /** Lista opcional; inválida deja un problema y devuelve vacío. */
    public Optional<JsonArray> lista(String clave) {
        JsonElement valor = leer(clave);
        if (valor == null) {
            return Optional.empty();
        }
        if (!valor.isJsonArray()) {
            return rechazar(clave, "se esperaba una lista", Optional.empty());
        }
        return Optional.of(valor.getAsJsonArray());
    }

    /** Registra un problema en {@code ruta.clave} y devuelve {@code porDefecto}. */
    public <T> T rechazar(String clave, String motivo, T porDefecto) {
        problemas.add(new DungeonsConfig.Problema(ruta + "." + clave, motivo));
        return porDefecto;
    }

    /** Avisa de las claves presentes que ningún campo leyó. */
    public void terminar() {
        for (String clave : objeto.keySet()) {
            if (!conocidas.contains(clave)) {
                problemas.add(new DungeonsConfig.Problema(ruta + "." + clave, "clave desconocida"));
            }
        }
    }

    private JsonElement leer(String clave) {
        conocidas.add(clave);
        JsonElement valor = objeto.get(clave);
        return valor == null || valor.isJsonNull() ? null : valor;
    }
}
