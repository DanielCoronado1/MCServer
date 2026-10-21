package com.dolfin.creepers.dungeon.config;

import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Carga, valida y persiste {@code config/dolfin-dungeons.json}, con un record inmutable por
 * sección.
 *
 * <ul>
 *   <li>Sin archivo: se escribe con los valores por defecto.
 *   <li>JSON mal formado: aviso con archivo, línea y columna; se usan los valores por defecto y el
 *       archivo no se toca.
 *   <li>Campo inválido o fuera de rango: aviso {@code event=config_invalid} y ese campo toma su
 *       valor por defecto sin descartar a sus hermanos.
 * </ul>
 *
 * <p>Cada {@link #recargar()} publica un {@link Snapshot} nuevo. Las partidas guardan el snapshot
 * con el que empezaron, así una recarga solo afecta a las partidas nuevas.
 */
public final class DungeonsConfig {
    public static final String ARCHIVO = "dolfin-dungeons.json";
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> SECCIONES =
            Set.of("general", "partida", "dificultades", "dungeons");
    private static final String PATRON_ID_OBJETO = "[a-z0-9_.-]+:[a-z0-9/._-]+";

    /** Un campo rechazado; siempre se usa su valor por defecto. */
    public record Problema(String campo, String motivo) {
        @Override
        public String toString() {
            return "file="
                    + ARCHIVO
                    + " field="
                    + campo
                    + " reason=\""
                    + motivo
                    + "\" fallback=default";
        }
    }

    /** Resultado de una carga o recarga. */
    public record Informe(long generacion, boolean creado, List<Problema> problemas) {
        public String resumen() {
            return "generation="
                    + generacion
                    + " file="
                    + ARCHIVO
                    + " created="
                    + creado
                    + " errors="
                    + problemas.size();
        }
    }

    /**
     * Interruptores globales.
     *
     * @param activo {@code false} impide empezar partidas nuevas (las en curso siguen)
     * @param mensajesChat avisos de grupo en el chat
     */
    public record General(boolean activo, boolean mensajesChat) {
        public static General porDefecto() {
            return new General(true, true);
        }
    }

    /**
     * Reglas comunes a todas las partidas. Los valores marcados como pendientes de {@code EPICA.md}
     * son supuestos razonables hasta que se decidan.
     *
     * @param readyCheckSegundos plazo del ready check de la fila (#14), 30
     * @param reconexionSegundos plazo para volver tras desconectarse (#12), 60
     * @param limiteMinutos duración máxima de una partida (#17), 45
     * @param afkSegundos inactividad que saca de la partida (#17), 300
     * @param hardcoreBanMinutos duración del ban al morir en hardcore (#16), 60
     * @param hardcoreBanMensaje motivo del ban; {@code %dungeon%} y {@code %minutos%} se sustituyen
     * @param desconexionHardcoreEsMuerte decisión P2: no volver a tiempo en hardcore cuenta como
     *     muerte, true
     * @param vidaJefePorJugador vida extra del jefe por cada jugador después del primero (#18), 0.5
     * @param objetosProhibidos ids de objetos que no se pueden usar dentro (#15); pendiente, por
     *     defecto perla de ender y fruta coral
     */
    public record ReglasPartida(
            int readyCheckSegundos,
            int reconexionSegundos,
            int limiteMinutos,
            int afkSegundos,
            int hardcoreBanMinutos,
            String hardcoreBanMensaje,
            boolean desconexionHardcoreEsMuerte,
            double vidaJefePorJugador,
            List<String> objetosProhibidos) {
        public ReglasPartida {
            objetosProhibidos = List.copyOf(objetosProhibidos);
        }

        public static ReglasPartida porDefecto() {
            return new ReglasPartida(
                    30,
                    60,
                    45,
                    300,
                    60,
                    "Has muerto en la dungeon %dungeon% · Hardcore. Podrás volver en %minutos% min",
                    true,
                    0.5,
                    List.of("minecraft:ender_pearl", "minecraft:chorus_fruit"));
        }
    }

    /**
     * Ajustes de una dificultad (#16, #18). Pendiente de {@code EPICA.md}: fácil sin límite de
     * vidas ni escalado, medio con 3 vidas, hardcore con una vida y mobs ×1.5.
     *
     * @param vidas vidas por jugador; 0 = ilimitadas
     * @param multVida multiplicador de vida de los mobs de la partida
     * @param multDanio multiplicador de daño de los mobs de la partida
     */
    public record ParametrosDificultad(int vidas, double multVida, double multDanio) {}

    /** Ajustes por dificultad. */
    public record Dificultades(
            ParametrosDificultad facil, ParametrosDificultad medio, ParametrosDificultad hardcore) {
        public static Dificultades porDefecto() {
            return new Dificultades(
                    new ParametrosDificultad(0, 1.0, 1.0),
                    new ParametrosDificultad(3, 1.25, 1.25),
                    new ParametrosDificultad(1, 1.5, 1.5));
        }

        public ParametrosDificultad de(Dificultad dificultad) {
            return switch (dificultad) {
                case FACIL -> facil;
                case MEDIO -> medio;
                case HARDCORE -> hardcore;
            };
        }
    }

    /** Configuración completa e inmutable de una generación. */
    public record Snapshot(
            General general,
            ReglasPartida partida,
            Dificultades dificultades,
            List<DefinicionDungeon> dungeons,
            long generacion) {
        public Snapshot {
            dungeons = List.copyOf(dungeons);
        }

        public static Snapshot porDefecto(long generacion) {
            return new Snapshot(
                    General.porDefecto(),
                    ReglasPartida.porDefecto(),
                    Dificultades.porDefecto(),
                    List.of(CodecDefinicion.ejemplo()),
                    generacion);
        }

        public Optional<DefinicionDungeon> dungeon(String id) {
            return dungeons.stream().filter(d -> d.id().equals(id)).findFirst();
        }
    }

    private final Path archivo;
    private final Consumer<String> avisos;
    private volatile Snapshot snapshot;
    private long generacion;

    /** Carga el archivo de inmediato; {@code avisos} recibe una línea por problema. */
    public DungeonsConfig(Path archivo, Consumer<String> avisos) {
        this.archivo = archivo;
        this.avisos = avisos;
        recargar();
    }

    public Snapshot actual() {
        return snapshot;
    }

    public Path archivo() {
        return archivo;
    }

    /** Relee el archivo; si falta, lo crea con los valores por defecto. */
    public synchronized Informe recargar() {
        List<Problema> problemas = new ArrayList<>();
        boolean[] creado = {false};
        long siguiente = ++generacion;
        snapshot = cargar(siguiente, problemas, creado);
        for (Problema problema : problemas) {
            avisos.accept("event=config_invalid generation=" + siguiente + " " + problema);
        }
        return new Informe(siguiente, creado[0], List.copyOf(problemas));
    }

    /**
     * Añade o reemplaza (por id) una dungeon en el archivo y recarga. Conserva el resto del JSON
     * tal como lo escribió el administrador; si el archivo no se puede leer, falla sin
     * sobrescribirlo.
     */
    public synchronized Informe guardarDungeon(DefinicionDungeon definicion) throws IOException {
        JsonObject raiz = leerRaizEditable();
        JsonArray dungeons = sinDungeon(raiz, definicion.id());
        dungeons.add(CodecDefinicion.aJson(definicion));
        raiz.add("dungeons", dungeons);
        escribirAtomico(archivo, raiz);
        return recargar();
    }

    /** Quita una dungeon del archivo y recarga. */
    public synchronized Informe quitarDungeon(String id) throws IOException {
        JsonObject raiz = leerRaizEditable();
        raiz.add("dungeons", sinDungeon(raiz, id));
        escribirAtomico(archivo, raiz);
        return recargar();
    }

    /** Convierte un snapshot al JSON que se escribe en disco. */
    public static JsonObject aJson(Snapshot snapshot) {
        JsonObject raiz = new JsonObject();
        raiz.add("general", GSON.toJsonTree(snapshot.general()));
        raiz.add("partida", GSON.toJsonTree(snapshot.partida()));
        raiz.add("dificultades", GSON.toJsonTree(snapshot.dificultades()));
        JsonArray dungeons = new JsonArray();
        snapshot.dungeons().forEach(d -> dungeons.add(CodecDefinicion.aJson(d)));
        raiz.add("dungeons", dungeons);
        return raiz;
    }

    /** Interpreta un JSON ya parseado; función pura usada por la carga y por las pruebas. */
    public static Snapshot interpretar(JsonObject raiz, long generacion, List<Problema> problemas) {
        for (String clave : raiz.keySet()) {
            if (!SECCIONES.contains(clave)) {
                problemas.add(new Problema(clave, "sección desconocida"));
            }
        }
        return new Snapshot(
                leerGeneral(LectorSeccion.de(raiz, "general", problemas)),
                leerPartida(LectorSeccion.de(raiz, "partida", problemas)),
                leerDificultades(LectorSeccion.de(raiz, "dificultades", problemas), problemas),
                CodecDefinicion.leerTodas(raiz.get("dungeons"), problemas),
                generacion);
    }

    private Snapshot cargar(long siguiente, List<Problema> problemas, boolean[] creado) {
        Snapshot porDefecto = Snapshot.porDefecto(siguiente);
        if (!Files.exists(archivo)) {
            try {
                escribirAtomico(archivo, aJson(porDefecto));
                creado[0] = true;
            } catch (IOException e) {
                problemas.add(new Problema("<escritura>", "no se pudo crear: " + e.getMessage()));
            }
            return porDefecto;
        }
        try {
            JsonElement leido = JsonParser.parseString(Files.readString(archivo));
            if (leido == null || !leido.isJsonObject()) {
                problemas.add(new Problema("<raíz>", "se esperaba un objeto JSON en " + archivo));
                return porDefecto;
            }
            return interpretar(leido.getAsJsonObject(), siguiente, problemas);
        } catch (IOException | RuntimeException e) {
            // Gson incluye "line N column M" en el mensaje de un JSON mal formado.
            problemas.add(new Problema("<sintaxis>", archivo + ": " + causa(e)));
            return porDefecto;
        }
    }

    private static General leerGeneral(LectorSeccion r) {
        General d = General.porDefecto();
        General seccion =
                new General(
                        r.booleano("activo", d.activo()),
                        r.booleano("mensajesChat", d.mensajesChat()));
        r.terminar();
        return seccion;
    }

    private static ReglasPartida leerPartida(LectorSeccion r) {
        ReglasPartida d = ReglasPartida.porDefecto();
        ReglasPartida seccion =
                new ReglasPartida(
                        r.entero("readyCheckSegundos", d.readyCheckSegundos(), 5, 300),
                        r.entero("reconexionSegundos", d.reconexionSegundos(), 5, 600),
                        r.entero("limiteMinutos", d.limiteMinutos(), 1, 600),
                        r.entero("afkSegundos", d.afkSegundos(), 30, 3600),
                        r.entero("hardcoreBanMinutos", d.hardcoreBanMinutos(), 1, 525_600),
                        r.texto("hardcoreBanMensaje", d.hardcoreBanMensaje()),
                        r.booleano("desconexionHardcoreEsMuerte", d.desconexionHardcoreEsMuerte()),
                        r.decimal("vidaJefePorJugador", d.vidaJefePorJugador(), 0, 10),
                        r.textos("objetosProhibidos", d.objetosProhibidos(), PATRON_ID_OBJETO));
        r.terminar();
        return seccion;
    }

    private static Dificultades leerDificultades(LectorSeccion r, List<Problema> problemas) {
        Dificultades d = Dificultades.porDefecto();
        Dificultades seccion =
                new Dificultades(
                        leerDificultad(r, "facil", d.facil(), problemas),
                        leerDificultad(r, "medio", d.medio(), problemas),
                        leerDificultad(r, "hardcore", d.hardcore(), problemas));
        r.terminar();
        return seccion;
    }

    private static ParametrosDificultad leerDificultad(
            LectorSeccion padre, String clave, ParametrosDificultad d, List<Problema> problemas) {
        LectorSeccion r =
                new LectorSeccion(
                        padre.ruta() + "." + clave, padre.objeto(clave).orElse(null), problemas);
        ParametrosDificultad parametros =
                new ParametrosDificultad(
                        r.entero("vidas", d.vidas(), 0, 100),
                        r.decimal("multVida", d.multVida(), 0.1, 10),
                        r.decimal("multDanio", d.multDanio(), 0.1, 10));
        r.terminar();
        return parametros;
    }

    private JsonObject leerRaizEditable() throws IOException {
        if (!Files.exists(archivo)) {
            return aJson(snapshot);
        }
        try {
            JsonElement leido = JsonParser.parseString(Files.readString(archivo));
            if (!leido.isJsonObject()) {
                throw new IOException(archivo + " no contiene un objeto JSON");
            }
            return leido.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException(archivo + " tiene un error de sintaxis: " + causa(e), e);
        }
    }

    private static JsonArray sinDungeon(JsonObject raiz, String id) {
        JsonArray resultado = new JsonArray();
        JsonElement actuales = raiz.get("dungeons");
        if (actuales != null && actuales.isJsonArray()) {
            for (JsonElement elemento : actuales.getAsJsonArray()) {
                boolean esLaMisma =
                        elemento.isJsonObject()
                                && elemento.getAsJsonObject().has("id")
                                && id.equals(elemento.getAsJsonObject().get("id").getAsString());
                if (!esLaMisma) {
                    resultado.add(elemento);
                }
            }
        }
        return resultado;
    }

    private static String causa(Exception e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        return raiz.getMessage() != null ? raiz.getMessage() : raiz.toString();
    }

    private static void escribirAtomico(Path destino, JsonObject valor) throws IOException {
        Path padre = destino.toAbsolutePath().getParent();
        Files.createDirectories(padre);
        Path temporal = Files.createTempFile(padre, ".dolfin-dungeons-", ".tmp");
        try {
            Files.writeString(temporal, GSON.toJson(valor) + "\n");
            try {
                Files.move(
                        temporal,
                        destino,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporal);
        }
    }
}
