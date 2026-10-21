package com.dolfin.creepers.dungeon.comando;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.comando.VistaDungeon.EstadoVisible;
import com.dolfin.creepers.dungeon.comando.VistaDungeon.EstadoVisible.Clase;
import com.dolfin.creepers.dungeon.config.CodecDefinicion;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon.Modo;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.EstadoDungeon;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.Rechazo;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.TipoRechazo;
import com.dolfin.creepers.dungeon.modelo.Zona;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

class VistaDungeonTest {
    static EstadoVisible estado(EstadoDungeon e, long segundos, int grupos, boolean bloqueada) {
        return VistaDungeon.estado(
                e,
                segundos < 0 ? OptionalLong.empty() : OptionalLong.of(segundos),
                grupos,
                bloqueada);
    }

    @Test
    void libre() {
        EstadoVisible v = estado(EstadoDungeon.LIBRE, -1, 0, false);
        assertEquals(Clase.LIBRE, v.clase());
        assertEquals("Libre", v.texto());
    }

    @Test
    void enPartidaMuestraMinutosYSegundos() {
        assertEquals("En partida (02:05)", estado(EstadoDungeon.EN_PARTIDA, 125, 0, false).texto());
    }

    @Test
    void enPartidaConFilaMuestraAmbos() {
        assertEquals(
                "En partida (00:09) · En fila (2 grupos)",
                estado(EstadoDungeon.EN_PARTIDA, 9, 2, false).texto());
    }

    @Test
    void libreConFilaSoloMuestraLaFila() {
        EstadoVisible v = estado(EstadoDungeon.LIBRE, -1, 1, false);
        assertEquals(Clase.EN_FILA, v.clase());
        assertEquals("En fila (1 grupo)", v.texto());
    }

    @Test
    void bloqueadaParaTiGanaALoDemasSalvoCerrada() {
        assertEquals("Bloqueada para ti", estado(EstadoDungeon.EN_PARTIDA, 5, 3, true).texto());
        assertEquals("Cerrada", estado(EstadoDungeon.DESHABILITADA, -1, 0, true).texto());
    }

    @Test
    void reiniciando() {
        assertEquals(Clase.REINICIANDO, estado(EstadoDungeon.REINICIANDO, -1, 0, false).clase());
    }

    @Test
    void tiempoNoSeDesbordaConMuchosMinutos() {
        assertEquals("125:03", VistaDungeon.tiempo(125 * 60 + 3));
        assertEquals("00:00", VistaDungeon.tiempo(-4));
    }

    @Test
    void infoListaLosDatosDeLaDungeon() {
        DefinicionDungeon d =
                CodecDefinicion.porDefecto("cripta", Zona.ranura(1)); // grupo 1-4, 30 min
        List<String> info = VistaDungeon.info(d, OptionalLong.of(754));
        assertTrue(info.contains("Jugadores: grupo (1-4)"), info.toString());
        assertTrue(info.contains("Dificultades: facil, medio, hardcore"), info.toString());
        assertTrue(info.contains("Requisitos: ninguno"), info.toString());
        assertTrue(info.contains("Cooldown: 30 min"), info.toString());
        assertTrue(info.contains("Tu mejor tiempo: 12:34"), info.toString());
    }

    @Test
    void infoMuestraElMejorTiempoDeCadaDificultad() {
        DefinicionDungeon d = CodecDefinicion.porDefecto("cripta", Zona.ranura(1));
        List<String> info =
                VistaDungeon.info(d, Map.of(Dificultad.HARDCORE, 754L, Dificultad.FACIL, 300L));
        assertTrue(info.contains("Tu mejor tiempo: facil 05:00 · hardcore 12:34"), info.toString());
        assertTrue(
                VistaDungeon.info(d, Map.<Dificultad, Long>of())
                        .contains("Tu mejor tiempo: sin registro"));
    }

    @Test
    void infoSinTiempoNiRequisitosExtra() {
        DefinicionDungeon base = CodecDefinicion.porDefecto("torre", Zona.ranura(2));
        DefinicionDungeon d =
                new DefinicionDungeon(
                        base.id(),
                        "La Torre",
                        true,
                        base.zona(),
                        base.entrada(),
                        List.of(),
                        Optional.empty(),
                        Modo.SOLO,
                        1,
                        1,
                        Set.of(Dificultad.FACIL),
                        base.permiso(),
                        List.of("cripta"),
                        0,
                        0);
        List<String> info = VistaDungeon.info(d, OptionalLong.empty());
        assertEquals("La Torre (torre)", info.getFirst());
        assertTrue(info.contains("Jugadores: solo (1)"), info.toString());
        assertTrue(info.contains("Requisitos: cripta"), info.toString());
        assertTrue(info.contains("Cooldown: ninguno"), info.toString());
        assertTrue(info.contains("Tu mejor tiempo: sin registro"), info.toString());
    }

    @Test
    void rechazoNombraAlCulpable() {
        Rechazo r = new Rechazo(TipoRechazo.JUGADOR_OCUPADO, Optional.of("Ana"));
        assertEquals(
                "Ana: ya está en una partida o en una fila",
                VistaDungeon.rechazo(r, Optional.empty()));
    }

    @Test
    void rechazoDeTamanioExplicaElRango() {
        DefinicionDungeon d = CodecDefinicion.porDefecto("cripta", Zona.ranura(1));
        Rechazo r = new Rechazo(TipoRechazo.TAMANIO_GRUPO, Optional.empty());
        assertEquals("esta dungeon es de grupo (1-4)", VistaDungeon.rechazo(r, Optional.of(d)));
    }

    @Test
    void avisoHardcoreMencionaElBaneo() {
        assertEquals(
                "Hardcore: si mueres pierdes todo y serás baneado 60 min.",
                VistaDungeon.avisoHardcore(60));
    }
}
