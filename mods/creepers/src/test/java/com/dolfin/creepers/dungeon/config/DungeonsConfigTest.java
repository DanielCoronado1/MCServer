package com.dolfin.creepers.dungeon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.Zona;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class DungeonsConfigTest {
    @TempDir Path dir;
    Path archivo;
    List<String> avisos = new ArrayList<>();
    DungeonsConfig config;

    @BeforeEach
    void crear() {
        archivo = dir.resolve(DungeonsConfig.ARCHIVO);
        config = new DungeonsConfig(archivo, avisos::add);
    }

    @Test
    void sinArchivoSeCreaConLosValoresPorDefecto() throws IOException {
        assertTrue(Files.exists(archivo));
        assertTrue(avisos.isEmpty());
        DungeonsConfig.Snapshot s = config.actual();
        assertEquals(60, s.partida().hardcoreBanMinutos());
        assertEquals(30, s.partida().readyCheckSegundos());
        assertEquals(60, s.partida().reconexionSegundos());
        assertEquals(45, s.partida().limiteMinutos());
        assertEquals(300, s.partida().afkSegundos());
        assertEquals(0.5, s.partida().vidaJefePorJugador());
        assertEquals(1, s.dungeons().size());
        assertFalse(s.dungeons().getFirst().habilitada());

        DungeonsConfig.Informe recarga = config.recargar();
        assertFalse(recarga.creado());
        assertTrue(recarga.problemas().isEmpty(), recarga.problemas().toString());
        assertEquals(s.dungeons(), config.actual().dungeons());
    }

    @Test
    void errorDeSintaxisAvisaConArchivoYLineaYNoTocaElArchivo() throws IOException {
        String roto = "{\n  \"partida\": {\n    \"afkSegundos\": 300,,\n  }\n}";
        Files.writeString(archivo, roto);
        DungeonsConfig.Informe informe = config.recargar();
        assertEquals(1, informe.problemas().size());
        String motivo = informe.problemas().getFirst().motivo();
        assertTrue(motivo.contains(archivo.toString()), motivo);
        assertTrue(motivo.contains("line 3"), motivo);
        assertEquals(60, config.actual().partida().hardcoreBanMinutos());
        assertEquals(roto, Files.readString(archivo));
    }

    @Test
    void valorFueraDeRangoSeRechazaSoloEseCampo() throws IOException {
        editar(
                raiz -> {
                    raiz.getAsJsonObject("partida").addProperty("hardcoreBanMinutos", -5);
                    raiz.getAsJsonObject("partida").addProperty("afkSegundos", 120);
                });
        DungeonsConfig.Informe informe = config.recargar();
        assertEquals(1, informe.problemas().size());
        assertEquals("partida.hardcoreBanMinutos", informe.problemas().getFirst().campo());
        assertEquals(60, config.actual().partida().hardcoreBanMinutos());
        assertEquals(120, config.actual().partida().afkSegundos());
        assertTrue(avisos.getLast().contains("event=config_invalid"));
    }

    @Test
    void clavesDesconocidasYTiposIncorrectosSeAvisan() throws IOException {
        editar(
                raiz -> {
                    raiz.getAsJsonObject("general").addProperty("activ", true);
                    raiz.getAsJsonObject("dificultades")
                            .getAsJsonObject("medio")
                            .addProperty("vidas", "tres");
                });
        DungeonsConfig.Informe informe = config.recargar();
        List<String> campos =
                informe.problemas().stream().map(DungeonsConfig.Problema::campo).toList();
        assertEquals(List.of("general.activ", "dificultades.medio.vidas"), campos);
        assertEquals(3, config.actual().dificultades().de(Dificultad.MEDIO).vidas());
    }

    @Test
    void dungeonsQueSeSolapanOSeRepitenSeDescartan() {
        JsonObject raiz = DungeonsConfig.aJson(DungeonsConfig.Snapshot.porDefecto(1));
        var dungeons = raiz.getAsJsonArray("dungeons");
        dungeons.add(CodecDefinicion.aJson(CodecDefinicion.porDefecto("solapada", Zona.ranura(0))));
        dungeons.add(CodecDefinicion.aJson(CodecDefinicion.porDefecto("ejemplo", Zona.ranura(1))));
        dungeons.add(CodecDefinicion.aJson(CodecDefinicion.porDefecto("otra", Zona.ranura(2))));
        List<DungeonsConfig.Problema> problemas = new ArrayList<>();
        DungeonsConfig.Snapshot s = DungeonsConfig.interpretar(raiz, 1, problemas);
        assertEquals(
                List.of("ejemplo", "otra"),
                s.dungeons().stream().map(DefinicionDungeon::id).toList());
        assertEquals(2, problemas.size());
    }

    @Test
    void camposDeDungeonInvalidosUsanSuValorPorDefecto() {
        JsonObject raiz =
                JsonParser.parseString(
                                """
                                {"dungeons": [{"id": "cripta", "modo": "solo", "maxJugadores": 4,
                                  "entrada": {"x": 99999, "y": 10, "z": 0},
                                  "dificultades": ["facil", "imposible"],
                                  "checkpoints": [{"x": 1, "y": 2, "z": 3}, {"x": "a"}]}]}
                                """)
                        .getAsJsonObject();
        List<DungeonsConfig.Problema> problemas = new ArrayList<>();
        DefinicionDungeon d = DungeonsConfig.interpretar(raiz, 1, problemas).dungeons().getFirst();
        assertEquals(DefinicionDungeon.Modo.SOLO, d.modo());
        assertEquals(1, d.maxJugadores());
        assertEquals(d.zona().centroInferior(), d.entrada());
        assertEquals(Set.of(Dificultad.values()), d.dificultades());
        assertEquals(1, d.checkpoints().size());
        assertEquals(Zona.ranura(0), d.zona());
        assertEquals(
                List.of(
                        "dungeons.cripta.entrada",
                        "dungeons.cripta.checkpoints[1]",
                        "dungeons.cripta.dificultades"),
                problemas.stream().map(DungeonsConfig.Problema::campo).toList());
    }

    @Test
    void guardarDungeonConservaElRestoYRecarga() throws IOException {
        editar(raiz -> raiz.getAsJsonObject("partida").addProperty("afkSegundos", 90));
        DefinicionDungeon nueva = CodecDefinicion.porDefecto("cripta", Zona.ranura(3));
        config.guardarDungeon(nueva);
        assertEquals(90, config.actual().partida().afkSegundos());
        assertEquals(nueva, config.actual().dungeon("cripta").orElseThrow());

        config.guardarDungeon(nueva.conHabilitada(false));
        assertEquals(2, config.actual().dungeons().size());
        assertFalse(config.actual().dungeon("cripta").orElseThrow().habilitada());

        config.quitarDungeon("cripta");
        assertTrue(config.actual().dungeon("cripta").isEmpty());
    }

    @Test
    void guardarDungeonNoPisaUnArchivoRoto() throws IOException {
        Files.writeString(archivo, "{roto");
        assertThrows(
                IOException.class,
                () -> config.guardarDungeon(CodecDefinicion.porDefecto("x", Zona.ranura(1))));
        assertEquals("{roto", Files.readString(archivo));
    }

    private void editar(java.util.function.Consumer<JsonObject> cambio) throws IOException {
        JsonObject raiz = JsonParser.parseString(Files.readString(archivo)).getAsJsonObject();
        cambio.accept(raiz);
        Files.writeString(archivo, raiz.toString());
    }
}
