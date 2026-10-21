package com.dolfin.creepers.dungeon.muerte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.config.CodecDefinicion;
import com.dolfin.creepers.dungeon.config.DungeonsConfig;
import com.dolfin.creepers.dungeon.eventos.BusEventos;
import com.dolfin.creepers.dungeon.modelo.CausaMuerte;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.Miembro;
import com.dolfin.creepers.dungeon.modelo.MotivoSalida;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.Candidato;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.ResultadoInicio;
import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;
import com.dolfin.creepers.dungeon.modelo.Zona;
import com.dolfin.creepers.dungeon.muerte.ReglasMuerte.Castigo;
import com.dolfin.creepers.dungeon.muerte.ReglasMuerte.Consecuencia;
import com.dolfin.creepers.dungeon.muerte.ReglasMuerte.Destino;
import com.dolfin.creepers.dungeon.muerte.ReglasMuerte.OrigenCastigo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class ReglasMuerteTest {
    static final Candidato ANA = new Candidato(UUID.randomUUID(), "Ana", true);
    static final Candidato BETO = new Candidato(UUID.randomUUID(), "Beto", true);

    RegistroDungeons registro;

    @BeforeEach
    void crear() {
        DungeonsConfig.Snapshot base = DungeonsConfig.Snapshot.porDefecto(1);
        DungeonsConfig.Snapshot config =
                new DungeonsConfig.Snapshot(
                        base.general(),
                        base.partida(),
                        base.dificultades(),
                        List.of(CodecDefinicion.porDefecto("cripta", Zona.ranura(0))),
                        1);
        registro =
                new RegistroDungeons(
                        new BusEventos(
                                (e, error) -> {
                                    throw error;
                                }),
                        config);
    }

    Partida iniciar(Dificultad dificultad) {
        ResultadoInicio r = registro.iniciarPartida("cripta", dificultad, List.of(ANA, BETO), 0, 0);
        return assertInstanceOf(ResultadoInicio.Iniciada.class, r).partida();
    }

    @Test
    void facilReapareceConservandoTodo() {
        assertEquals(
                new Consecuencia(false, Destino.REAPARECE),
                ReglasMuerte.consecuencia(Dificultad.FACIL, Miembro.VIDAS_ILIMITADAS));
    }

    @Test
    void medioDejaTumbaYConCeroVidasPasaAEspectador() {
        assertEquals(
                new Consecuencia(true, Destino.REAPARECE),
                ReglasMuerte.consecuencia(Dificultad.MEDIO, 2));
        assertEquals(
                new Consecuencia(true, Destino.ESPECTADOR),
                ReglasMuerte.consecuencia(Dificultad.MEDIO, 0));
    }

    @Test
    void hardcoreEliminaSinTumbaAunqueQuedenVidas() {
        assertEquals(
                new Consecuencia(false, Destino.ELIMINADO),
                ReglasMuerte.consecuencia(Dificultad.HARDCORE, 0));
        assertEquals(
                new Consecuencia(false, Destino.ELIMINADO),
                ReglasMuerte.consecuencia(Dificultad.HARDCORE, 2));
    }

    @Test
    void mensajesAlGrupo() {
        assertEquals(
                "Pepe murió (2 vidas restantes)",
                ReglasMuerte.mensajeMuerte("Pepe", Dificultad.MEDIO, CausaMuerte.COMBATE, 2));
        assertEquals(
                "Pepe murió (1 vida restante)",
                ReglasMuerte.mensajeMuerte("Pepe", Dificultad.MEDIO, CausaMuerte.COMBATE, 1));
        assertEquals(
                "Pepe murió sin vidas restantes y pasa a espectador",
                ReglasMuerte.mensajeMuerte("Pepe", Dificultad.MEDIO, CausaMuerte.COMBATE, 0));
        assertEquals(
                "Pepe murió",
                ReglasMuerte.mensajeMuerte(
                        "Pepe", Dificultad.FACIL, CausaMuerte.COMBATE, Miembro.VIDAS_ILIMITADAS));
        assertEquals(
                "Pepe ha caído en hardcore",
                ReglasMuerte.mensajeMuerte("Pepe", Dificultad.HARDCORE, CausaMuerte.COMBATE, 0));
        assertEquals(
                "Pepe ha caído en hardcore (no volvió a tiempo)",
                ReglasMuerte.mensajeMuerte(
                        "Pepe", Dificultad.HARDCORE, CausaMuerte.DESCONEXION, 0));
    }

    @Test
    void lasVidasDeMedioSeAgotanYCuentanMuertes() {
        Partida p = iniciar(Dificultad.MEDIO);
        registro.registrarMuerte(ANA.id(), CausaMuerte.COMBATE);
        registro.registrarMuerte(ANA.id(), CausaMuerte.COMBATE);
        int vidas = registro.registrarMuerte(ANA.id(), CausaMuerte.COMBATE).orElseThrow();
        assertEquals(0, vidas);
        assertEquals(3, p.miembro(ANA.id()).orElseThrow().muertes());
        assertEquals(
                Destino.ESPECTADOR, ReglasMuerte.consecuencia(p.dificultad(), vidas).destino());
    }

    @Test
    void derrotaCuandoTodosSonEspectadoresOFuera() {
        Partida p = iniciar(Dificultad.MEDIO);
        registro.pasarAEspectador(ANA.id());
        assertFalse(ReglasMuerte.derrotaPorBajas(p));
        registro.sacarMiembro(BETO.id(), MotivoSalida.ABANDONO);
        assertTrue(ReglasMuerte.derrotaPorBajas(p));
    }

    @Test
    void unDesconectadoVivoPuedeVolverYNoHayDerrota() {
        Partida p = iniciar(Dificultad.MEDIO);
        registro.pasarAEspectador(ANA.id());
        registro.desconectar(BETO.id(), 0);
        assertFalse(ReglasMuerte.derrotaPorBajas(p));
    }

    @Test
    void hardcoreSinSupervivientesEsDerrota() {
        Partida p = iniciar(Dificultad.HARDCORE);
        registro.registrarMuerte(ANA.id(), CausaMuerte.COMBATE);
        registro.sacarMiembro(ANA.id(), MotivoSalida.MUERTE_HARDCORE);
        assertFalse(ReglasMuerte.derrotaPorBajas(p));
        registro.registrarMuerte(BETO.id(), CausaMuerte.COMBATE);
        registro.sacarMiembro(BETO.id(), MotivoSalida.MUERTE_HARDCORE);
        assertTrue(ReglasMuerte.derrotaPorBajas(p));
    }

    @Test
    void salirTodosSinMorirNoEsDerrota() {
        Partida p = iniciar(Dificultad.MEDIO);
        registro.sacarMiembro(ANA.id(), MotivoSalida.ABANDONO);
        registro.sacarMiembro(BETO.id(), MotivoSalida.ABANDONO);
        assertFalse(ReglasMuerte.derrotaPorBajas(p));
    }

    @Test
    void laTumbaVuelveSalvoConDerrotaOTiempoAgotado() {
        assertTrue(ReglasMuerte.devolverTumba(ResultadoPartida.VICTORIA));
        assertTrue(ReglasMuerte.devolverTumba(ResultadoPartida.CANCELADA));
        assertTrue(ReglasMuerte.devolverTumba(ResultadoPartida.ABANDONADA));
        assertFalse(ReglasMuerte.devolverTumba(ResultadoPartida.DERROTA));
        assertFalse(ReglasMuerte.devolverTumba(ResultadoPartida.TIEMPO_AGOTADO));
    }

    @Test
    void hardcoreSiempreVaciaYBaneaSalvoAfkOBypass() {
        assertEquals(
                new Castigo(true, true, true),
                ReglasMuerte.castigoHardcore(OrigenCastigo.MUERTE, false));
        assertEquals(
                new Castigo(true, true, true),
                ReglasMuerte.castigoHardcore(OrigenCastigo.ABANDONO, false));
        assertEquals(
                new Castigo(true, false, false),
                ReglasMuerte.castigoHardcore(OrigenCastigo.AFK, false));
        assertEquals(
                new Castigo(true, false, false),
                ReglasMuerte.castigoHardcore(OrigenCastigo.MUERTE, true));
    }

    @Test
    void desconectadoSinVolverSeBaneaSinExpulsar() {
        assertEquals(
                new Castigo(true, true, false),
                ReglasMuerte.castigoHardcore(OrigenCastigo.DESCONEXION, false));
        assertEquals(
                new Castigo(true, false, false),
                ReglasMuerte.castigoHardcore(OrigenCastigo.DESCONEXION, true));
    }
}
