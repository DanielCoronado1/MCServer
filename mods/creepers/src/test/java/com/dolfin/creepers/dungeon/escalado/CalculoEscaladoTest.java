package com.dolfin.creepers.dungeon.escalado;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dolfin.creepers.dungeon.config.CodecDefinicion;
import com.dolfin.creepers.dungeon.config.DungeonsConfig;
import com.dolfin.creepers.dungeon.eventos.BusEventos;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.Candidato;
import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;
import com.dolfin.creepers.dungeon.modelo.Zona;
import com.dolfin.creepers.dungeon.plantilla.DatosMarcador;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class CalculoEscaladoTest {
    static final Candidato P1 = new Candidato(UUID.randomUUID(), "P1", true);
    static final Candidato P2 = new Candidato(UUID.randomUUID(), "P2", true);
    static final Candidato P3 = new Candidato(UUID.randomUUID(), "P3", true);
    static final Candidato P4 = new Candidato(UUID.randomUUID(), "P4", true);

    RegistroDungeons registro;

    @BeforeEach
    void setup() {
        DungeonsConfig.Snapshot base = DungeonsConfig.Snapshot.porDefecto(1);
        DungeonsConfig.Snapshot snapshot =
                new DungeonsConfig.Snapshot(
                        base.general(),
                        base.partida(),
                        base.dificultades(),
                        List.of(CodecDefinicion.porDefecto("cripta", Zona.ranura(0))),
                        1);
        registro = new RegistroDungeons(new BusEventos((e, err) -> {}), snapshot);
    }

    private void reiniciarDungeon() {
        registro.terminarPartida("cripta", ResultadoPartida.CANCELADA);
        registro.finalizarReinicio("cripta");
    }

    private Partida crearPartida(Dificultad dificultad, List<Candidato> jugadores) {
        var resultado = registro.iniciarPartida("cripta", dificultad, jugadores, 100, 1000L);
        return ((RegistroDungeons.ResultadoInicio.Iniciada) resultado).partida();
    }

    @Test
    void escaladoMobSegunDificultad() {
        Partida facil = crearPartida(Dificultad.FACIL, List.of(P1));
        assertEquals(1.0, CalculoEscalado.multiplicadorVidaMob(facil), 1e-6);
        assertEquals(1.0, CalculoEscalado.multiplicadorDanioMob(facil), 1e-6);

        reiniciarDungeon();
        Partida medio = crearPartida(Dificultad.MEDIO, List.of(P1));
        assertEquals(1.25, CalculoEscalado.multiplicadorVidaMob(medio), 1e-6);
        assertEquals(1.25, CalculoEscalado.multiplicadorDanioMob(medio), 1e-6);

        reiniciarDungeon();
        Partida hardcore = crearPartida(Dificultad.HARDCORE, List.of(P1));
        assertEquals(1.5, CalculoEscalado.multiplicadorVidaMob(hardcore), 1e-6);
        assertEquals(1.5, CalculoEscalado.multiplicadorDanioMob(hardcore), 1e-6);
    }

    @Test
    void escaladoJefePorJugadores() {
        // En fácil: multVida=1.0, con 1 jugador: 1.0 * (1 + 0.5 * 0) = 1.0
        Partida unJugador = crearPartida(Dificultad.FACIL, List.of(P1));
        assertEquals(1.0, CalculoEscalado.multiplicadorVidaJefe(unJugador), 1e-6);

        // En fácil con 4 jugadores: 1.0 * (1 + 0.5 * 3) = 2.5
        reiniciarDungeon();
        Partida cuatroJugadores = crearPartida(Dificultad.FACIL, List.of(P1, P2, P3, P4));
        assertEquals(2.5, CalculoEscalado.multiplicadorVidaJefe(cuatroJugadores), 1e-6);

        // En hardcore con 4 jugadores: 1.5 * (1 + 0.5 * 3) = 1.5 * 2.5 = 3.75
        reiniciarDungeon();
        Partida cuatroHardcore = crearPartida(Dificultad.HARDCORE, List.of(P1, P2, P3, P4));
        assertEquals(3.75, CalculoEscalado.multiplicadorVidaJefe(cuatroHardcore), 1e-6);
    }

    @Test
    void cantidadMobsExtraEnHardcore() {
        Partida facil = crearPartida(Dificultad.FACIL, List.of(P1));
        DatosMarcador marcadorConExtra =
                new DatosMarcador(0, 0, 0, "minecraft:zombie", 2, "{extraHardcore:3}");
        // En fácil ignora el extra de hardcore
        assertEquals(2, CalculoEscalado.cantidadMobs(facil, marcadorConExtra));

        reiniciarDungeon();
        Partida hardcore = crearPartida(Dificultad.HARDCORE, List.of(P1));
        // En hardcore suma los 3 extras
        assertEquals(5, CalculoEscalado.cantidadMobs(hardcore, marcadorConExtra));

        // Marcador sin extra en hardcore mantiene su cantidad base
        DatosMarcador normal = new DatosMarcador(0, 0, 0, "minecraft:skeleton", 3, "");
        assertEquals(3, CalculoEscalado.cantidadMobs(hardcore, normal));
    }
}
