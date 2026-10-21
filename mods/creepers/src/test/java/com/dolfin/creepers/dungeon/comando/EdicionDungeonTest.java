package com.dolfin.creepers.dungeon.comando;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.comando.EdicionDungeon.Resultado;
import com.dolfin.creepers.dungeon.config.CodecDefinicion;
import com.dolfin.creepers.dungeon.modelo.DefinicionDungeon;
import com.dolfin.creepers.dungeon.modelo.Punto;
import com.dolfin.creepers.dungeon.modelo.Zona;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class EdicionDungeonTest {
    static final Zona ZONA = new Zona(0, 0, 0, 99, 99, 99);
    DefinicionDungeon cripta = CodecDefinicion.porDefecto("cripta", ZONA);

    static Punto punto(double x, double y, double z) {
        return new Punto(x, y, z, 0, 0);
    }

    static DefinicionDungeon listo(Resultado r) {
        return assertInstanceOf(Resultado.Listo.class, r).dungeon();
    }

    static String motivo(Resultado r) {
        return assertInstanceOf(Resultado.Rechazado.class, r).motivo();
    }

    @Test
    void zonaNuevaCreaLaDungeonDeshabilitada() {
        Resultado r = EdicionDungeon.definirZona("torre", Optional.empty(), ZONA, List.of());
        DefinicionDungeon d = listo(r);
        assertEquals("torre", d.id());
        assertEquals(ZONA, d.zona());
        assertFalse(d.habilitada());
        assertFalse(((Resultado.Listo) r).avisos().isEmpty());
    }

    @Test
    void idInvalidoSeRechaza() {
        Resultado r = EdicionDungeon.definirZona("Torre!", Optional.empty(), ZONA, List.of());
        assertTrue(motivo(r).contains("id"));
    }

    @Test
    void alturaFueraDeRangoSeRechaza() {
        Zona alta = new Zona(0, 0, 0, 9, Zona.Y_MAX + 1, 9);
        Resultado r = EdicionDungeon.definirZona("torre", Optional.empty(), alta, List.of());
        assertTrue(motivo(r).contains("altura"));
    }

    @Test
    void zonaSolapadaConOtraSeRechazaPeroNoConsigoMisma() {
        Zona sobre = new Zona(50, 0, 50, 150, 99, 150);
        Resultado otra =
                EdicionDungeon.definirZona("torre", Optional.empty(), sobre, List.of(cripta));
        assertTrue(motivo(otra).contains("cripta"));

        Resultado propia =
                EdicionDungeon.definirZona("cripta", Optional.of(cripta), sobre, List.of(cripta));
        assertEquals(sobre, listo(propia).zona());
    }

    @Test
    void cambiarLaZonaDescartaLoQueQuedaFuera() {
        DefinicionDungeon conPuntos =
                cripta.conGeometria(
                        ZONA,
                        punto(10, 5, 10),
                        List.of(punto(10, 5, 20), punto(90, 5, 90)),
                        Optional.of(punto(90, 5, 5)));
        Zona nueva = new Zona(0, 0, 0, 49, 99, 49);
        Resultado r =
                EdicionDungeon.definirZona(
                        "cripta", Optional.of(conPuntos), nueva, List.of(conPuntos));
        DefinicionDungeon d = listo(r);
        assertEquals(punto(10, 5, 10), d.entrada());
        assertEquals(List.of(punto(10, 5, 20)), d.checkpoints());
        assertTrue(d.salida().isEmpty());
        assertEquals(2, ((Resultado.Listo) r).avisos().size());
    }

    @Test
    void cambiarLaZonaMueveLaEntradaQueQuedaFuera() {
        DefinicionDungeon d =
                cripta.conGeometria(ZONA, punto(90, 5, 90), List.of(), Optional.empty());
        Zona nueva = new Zona(0, 0, 0, 49, 99, 49);
        Resultado r = EdicionDungeon.definirZona("cripta", Optional.of(d), nueva, List.of(d));
        assertEquals(nueva.centroInferior(), listo(r).entrada());
    }

    @Test
    void entradaYSalidaDebenCaerEnLaZona() {
        assertEquals(
                punto(5, 5, 5),
                listo(EdicionDungeon.definirEntrada(cripta, punto(5, 5, 5))).entrada());
        assertTrue(
                motivo(EdicionDungeon.definirEntrada(cripta, punto(500, 5, 5)))
                        .contains("entrada"));
        assertEquals(
                Optional.of(punto(6, 5, 6)),
                listo(EdicionDungeon.definirSalida(cripta, punto(6, 5, 6))).salida());
        assertTrue(
                motivo(EdicionDungeon.definirSalida(cripta, punto(-1, 5, 5))).contains("salida"));
    }

    @Test
    void checkpointsSeAnadenAlFinalYSeQuitaElMasCercano() {
        DefinicionDungeon d = listo(EdicionDungeon.anadirCheckpoint(cripta, punto(10, 5, 10)));
        d = listo(EdicionDungeon.anadirCheckpoint(d, punto(50, 5, 50)));
        d = listo(EdicionDungeon.anadirCheckpoint(d, punto(90, 5, 90)));
        assertEquals(3, d.checkpoints().size());
        assertEquals(punto(90, 5, 90), d.checkpoints().getLast());

        d = listo(EdicionDungeon.quitarCheckpoint(d, punto(48, 5, 52)));
        assertEquals(List.of(punto(10, 5, 10), punto(90, 5, 90)), d.checkpoints());
    }

    @Test
    void checkpointFueraDeLaZonaOSinNingunoSeRechaza() {
        assertTrue(
                motivo(EdicionDungeon.anadirCheckpoint(cripta, punto(500, 5, 5)))
                        .contains("checkpoint"));
        assertTrue(
                motivo(EdicionDungeon.quitarCheckpoint(cripta, punto(1, 1, 1)))
                        .contains("checkpoints"));
    }
}
