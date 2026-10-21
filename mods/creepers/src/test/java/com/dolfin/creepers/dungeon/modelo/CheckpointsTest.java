package com.dolfin.creepers.dungeon.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.config.CodecDefinicion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

class CheckpointsTest {
    static DefinicionDungeon conCheckpoints(Punto... puntos) {
        DefinicionDungeon d = CodecDefinicion.porDefecto("cripta", Zona.ranura(0));
        return new DefinicionDungeon(
                d.id(),
                d.nombre(),
                true,
                d.zona(),
                d.entrada(),
                List.of(puntos),
                d.salida(),
                d.modo(),
                d.minJugadores(),
                d.maxJugadores(),
                d.dificultades(),
                d.permiso(),
                d.requisitos(),
                d.cooldownMinutos(),
                d.limiteMinutos());
    }

    @Test
    void unCheckpointSeAlcanzaDentroDeSuCaja() {
        DefinicionDungeon d =
                conCheckpoints(new Punto(10, 5, 10, 0, 0), new Punto(40, 5, 10, 0, 0));
        assertEquals(OptionalInt.of(0), d.checkpointEn(12.5, 7, 8));
        assertEquals(OptionalInt.of(1), d.checkpointEn(40, 5, 10));
        assertTrue(d.checkpointEn(13.5, 5, 10).isEmpty());
        assertTrue(d.checkpointEn(10, 9, 10).isEmpty());
    }

    @Test
    void siSeSolapanGanaElMasAvanzado() {
        DefinicionDungeon d = conCheckpoints(new Punto(0, 5, 0, 0, 0), new Punto(2, 5, 0, 0, 0));
        assertEquals(OptionalInt.of(1), d.checkpointEn(1, 5, 0));
    }

    @Test
    void sinCheckpointsSeReapareceEnLaEntrada() {
        DefinicionDungeon d = conCheckpoints();
        assertTrue(d.checkpointEn(0, 0, 0).isEmpty());
        assertEquals(d.entrada(), d.puntoDeCheckpoint(Miembro.SIN_CHECKPOINT));
    }
}
