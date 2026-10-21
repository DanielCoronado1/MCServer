package com.dolfin.creepers.dungeon.fin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.config.CodecDefinicion;
import com.dolfin.creepers.dungeon.config.DungeonsConfig;
import com.dolfin.creepers.dungeon.eventos.BusEventos;
import com.dolfin.creepers.dungeon.fin.ReglasFin.Premios;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.MotivoSalida;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.Candidato;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.ResultadoInicio;
import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;
import com.dolfin.creepers.dungeon.modelo.Zona;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class ReglasFinTest {
    static final Candidato ANA = new Candidato(UUID.randomUUID(), "Ana", true);
    static final Candidato BETO = new Candidato(UUID.randomUUID(), "Beto", true);

    RegistroDungeons registro;

    @BeforeEach
    void crear() {
        DungeonsConfig.Snapshot base = DungeonsConfig.Snapshot.porDefecto(1);
        registro =
                new RegistroDungeons(
                        new BusEventos(
                                (e, error) -> {
                                    throw error;
                                }),
                        new DungeonsConfig.Snapshot(
                                base.general(),
                                base.partida(),
                                base.dificultades(),
                                List.of(CodecDefinicion.porDefecto("cripta", Zona.ranura(0))),
                                1));
    }

    Partida iniciar(Dificultad dificultad) {
        ResultadoInicio r =
                registro.iniciarPartida("cripta", dificultad, List.of(ANA, BETO), 0, 1000);
        return assertInstanceOf(ResultadoInicio.Iniciada.class, r).partida();
    }

    @Test
    void ganadoresSonLosQueNoHanSalido() {
        Partida p = iniciar(Dificultad.MEDIO);
        registro.sacarMiembro(BETO.id(), MotivoSalida.ABANDONO);
        assertEquals(List.of(new Marca.Integrante(ANA.id(), "Ana")), ReglasFin.ganadores(p));
        Marca marca = ReglasFin.marca(p, 5000, 9000);
        assertEquals("cripta", marca.dungeon());
        assertEquals(Dificultad.MEDIO, marca.dificultad());
        assertEquals(5000, marca.tiempoMillis());
    }

    @Test
    void elTituloEsDeLaPrimeraVictoriaYLaCabezaDeLaPrimeraEnHardcore() {
        TablaRecords tabla = new TablaRecords();
        assertEquals(
                new Premios(true, false),
                ReglasFin.premios(tabla, ANA.id(), "cripta", Dificultad.MEDIO));
        assertEquals(
                new Premios(true, true),
                ReglasFin.premios(tabla, ANA.id(), "cripta", Dificultad.HARDCORE));
        tabla.anadir(
                new Marca(
                        "cripta",
                        Dificultad.HARDCORE,
                        List.of(new Marca.Integrante(ANA.id(), "Ana")),
                        1,
                        1));
        assertEquals(
                new Premios(false, false),
                ReglasFin.premios(tabla, ANA.id(), "cripta", Dificultad.HARDCORE));
        assertEquals(
                new Premios(true, false),
                ReglasFin.premios(tabla, ANA.id(), "cripta", Dificultad.FACIL));
        assertEquals(
                new Premios(true, true),
                ReglasFin.premios(tabla, ANA.id(), "torre", Dificultad.HARDCORE));
    }

    @Test
    void elCooldownAfectaATodosLosQueJugaronSalvoSiSeCancela() {
        Partida p = iniciar(Dificultad.MEDIO);
        registro.sacarMiembro(BETO.id(), MotivoSalida.ABANDONO);
        assertEquals(
                List.of(ANA.id(), BETO.id()),
                ReglasFin.sujetosACooldown(p, ResultadoPartida.DERROTA));
        assertEquals(
                List.of(ANA.id(), BETO.id()),
                ReglasFin.sujetosACooldown(p, ResultadoPartida.VICTORIA));
        assertTrue(ReglasFin.sujetosACooldown(p, ResultadoPartida.CANCELADA).isEmpty());
        assertEquals(1_000 + 30 * 60_000L, ReglasFin.finDeCooldown(p, 1_000));
    }
}
