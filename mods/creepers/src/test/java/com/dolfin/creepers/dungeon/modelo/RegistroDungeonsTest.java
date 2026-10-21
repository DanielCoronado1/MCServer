package com.dolfin.creepers.dungeon.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.config.CodecDefinicion;
import com.dolfin.creepers.dungeon.config.DungeonsConfig;
import com.dolfin.creepers.dungeon.eventos.BusEventos;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.DungeonCambioEstado;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorDesconectado;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorMurio;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorReconectado;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorSalio;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.PartidaIniciada;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.PartidaTerminada;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.Candidato;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.ResultadoInicio;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons.TipoRechazo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

class RegistroDungeonsTest {
    static final Candidato ANA = new Candidato(UUID.randomUUID(), "Ana", true);
    static final Candidato BETO = new Candidato(UUID.randomUUID(), "Beto", true);

    List<EventoDungeon> eventos = new ArrayList<>();
    RegistroDungeons registro;

    @BeforeEach
    void crear() {
        BusEventos bus =
                new BusEventos(
                        (e, error) -> {
                            throw error;
                        });
        bus.suscribir(EventoDungeon.class, eventos::add);
        registro = new RegistroDungeons(bus, config(true));
    }

    static DungeonsConfig.Snapshot config(boolean criptaHabilitada) {
        DungeonsConfig.Snapshot base = DungeonsConfig.Snapshot.porDefecto(1);
        return new DungeonsConfig.Snapshot(
                base.general(),
                base.partida(),
                base.dificultades(),
                List.of(
                        CodecDefinicion.porDefecto("cripta", Zona.ranura(0))
                                .conHabilitada(criptaHabilitada),
                        CodecDefinicion.porDefecto("torre", Zona.ranura(1))),
                1);
    }

    Partida iniciar(String id, Dificultad dificultad, Candidato... candidatos) {
        ResultadoInicio r = registro.iniciarPartida(id, dificultad, List.of(candidatos), 100, 0);
        return assertInstanceOf(ResultadoInicio.Iniciada.class, r).partida();
    }

    List<TipoRechazo> rechazos(String id, Dificultad dificultad, Candidato... candidatos) {
        ResultadoInicio r = registro.iniciarPartida(id, dificultad, List.of(candidatos), 100, 0);
        return assertInstanceOf(ResultadoInicio.Rechazada.class, r).rechazos().stream()
                .map(RegistroDungeons.Rechazo::tipo)
                .toList();
    }

    @Test
    void iniciarOcupaLaDungeonYPublicaEventos() {
        Partida p = iniciar("cripta", Dificultad.MEDIO, ANA, BETO);
        assertEquals(EstadoDungeon.EN_PARTIDA, registro.estado("cripta").orElseThrow());
        assertEquals(2, p.jugadoresIniciales());
        assertEquals(3, p.miembro(ANA.id()).orElseThrow().vidas());
        assertEquals(
                List.of(
                        new DungeonCambioEstado(
                                "cripta", EstadoDungeon.LIBRE, EstadoDungeon.EN_PARTIDA),
                        new PartidaIniciada(p)),
                eventos);
    }

    @Test
    void unaSolaPartidaPorDungeonYUnaOcupacionPorJugador() {
        iniciar("cripta", Dificultad.FACIL, ANA);
        assertEquals(
                List.of(TipoRechazo.DUNGEON_OCUPADA), rechazos("cripta", Dificultad.FACIL, BETO));
        assertEquals(
                List.of(TipoRechazo.JUGADOR_OCUPADO), rechazos("torre", Dificultad.FACIL, ANA));
        assertTrue(registro.entrarEnFila(BETO.id(), "torre"));
        assertFalse(registro.entrarEnFila(BETO.id(), "cripta"));
        assertFalse(registro.entrarEnFila(ANA.id(), "torre"));
    }

    @Test
    void laFilaDeLaMismaDungeonPasaALaPartida() {
        assertTrue(registro.entrarEnFila(ANA.id(), "torre"));
        iniciar("torre", Dificultad.FACIL, ANA);
        assertEquals(
                RegistroDungeons.Ocupacion.Tipo.PARTIDA,
                registro.ocupacion(ANA.id()).orElseThrow().tipo());
    }

    @Test
    void rechazosDeDungeonYDeJugadores() {
        Candidato creativo = new Candidato(UUID.randomUUID(), "Cris", false);
        assertEquals(
                List.of(TipoRechazo.DUNGEON_DESCONOCIDA), rechazos("nada", Dificultad.FACIL, ANA));
        assertEquals(
                List.of(TipoRechazo.MODO_DE_JUEGO, TipoRechazo.JUGADOR_REPETIDO),
                rechazos("cripta", Dificultad.FACIL, creativo, ANA, ANA));
        assertEquals(
                List.of(TipoRechazo.TAMANIO_GRUPO),
                rechazos(
                        "cripta",
                        Dificultad.FACIL,
                        ANA,
                        BETO,
                        new Candidato(UUID.randomUUID(), "C", true),
                        new Candidato(UUID.randomUUID(), "D", true),
                        new Candidato(UUID.randomUUID(), "E", true)));
        registro.aplicarConfig(config(false));
        assertEquals(EstadoDungeon.DESHABILITADA, registro.estado("cripta").orElseThrow());
        assertEquals(
                List.of(TipoRechazo.DUNGEON_DESHABILITADA),
                rechazos("cripta", Dificultad.FACIL, ANA));
    }

    @Test
    void terminarSacaATodosYPasaAReiniciar() {
        Partida p = iniciar("cripta", Dificultad.FACIL, ANA, BETO);
        registro.sacarMiembro(ANA.id(), MotivoSalida.ABANDONO);
        eventos.clear();
        registro.terminarPartida("cripta", ResultadoPartida.VICTORIA);
        assertEquals(
                List.of(
                        new JugadorSalio(p, BETO.id(), MotivoSalida.FIN_DE_PARTIDA),
                        new PartidaTerminada(p, ResultadoPartida.VICTORIA),
                        new DungeonCambioEstado(
                                "cripta", EstadoDungeon.EN_PARTIDA, EstadoDungeon.REINICIANDO)),
                eventos);
        assertFalse(p.activa());
        assertTrue(registro.ocupacion(BETO.id()).isEmpty());
        assertEquals(
                List.of(TipoRechazo.DUNGEON_OCUPADA), rechazos("cripta", Dificultad.FACIL, ANA));
        registro.finalizarReinicio("cripta");
        assertEquals(EstadoDungeon.LIBRE, registro.estado("cripta").orElseThrow());
    }

    @Test
    void reconectarATiempoRecuperaElEstado() {
        Partida p = iniciar("cripta", Dificultad.MEDIO, ANA);
        registro.desconectar(ANA.id(), 1000);
        Miembro ana = p.miembro(ANA.id()).orElseThrow();
        assertEquals(EstadoMiembro.DESCONECTADO, ana.estado());
        assertEquals(1000 + 60 * 20, ana.plazoReconexionTick());
        registro.vencerDesconexiones(1000 + 60 * 20 - 1);
        assertTrue(registro.reconectar(ANA.id()).isPresent());
        assertEquals(EstadoMiembro.VIVO, ana.estado());
        assertTrue(eventos.contains(new JugadorDesconectado(p, ANA.id(), 60)));
        assertTrue(eventos.contains(new JugadorReconectado(p, ANA.id())));
    }

    @Test
    void noVolverEnMedioSaleSinMorir() {
        Partida p = iniciar("cripta", Dificultad.MEDIO, ANA);
        registro.desconectar(ANA.id(), 0);
        registro.vencerDesconexiones(60 * 20);
        assertEquals(EstadoMiembro.FUERA, p.miembro(ANA.id()).orElseThrow().estado());
        assertTrue(eventos.contains(new JugadorSalio(p, ANA.id(), MotivoSalida.DESCONEXION)));
        assertTrue(eventos.stream().noneMatch(JugadorMurio.class::isInstance));
        assertTrue(registro.reconectar(ANA.id()).isEmpty());
        assertTrue(registro.ocupacion(ANA.id()).isEmpty());
    }

    @Test
    void noVolverEnHardcoreCuentaComoMuerte() {
        Partida p = iniciar("cripta", Dificultad.HARDCORE, ANA, BETO);
        registro.desconectar(ANA.id(), 0);
        registro.vencerDesconexiones(60 * 20);
        int muerte = eventos.indexOf(new JugadorMurio(p, ANA.id(), CausaMuerte.DESCONEXION, 0));
        int salida = eventos.indexOf(new JugadorSalio(p, ANA.id(), MotivoSalida.DESCONEXION));
        assertTrue(muerte >= 0 && salida > muerte, eventos.toString());
        assertTrue(p.hayParticipantes());
    }

    @Test
    void muertesRestanVidasSoloSiSonLimitadas() {
        iniciar("cripta", Dificultad.FACIL, ANA);
        iniciar("torre", Dificultad.MEDIO, BETO);
        assertEquals(
                OptionalInt.of(Miembro.VIDAS_ILIMITADAS),
                registro.registrarMuerte(ANA.id(), CausaMuerte.COMBATE));
        assertEquals(OptionalInt.of(2), registro.registrarMuerte(BETO.id(), CausaMuerte.COMBATE));
        assertTrue(registro.pasarAEspectador(BETO.id()));
        assertFalse(registro.pasarAEspectador(BETO.id()));
        assertEquals(
                OptionalInt.empty(),
                registro.registrarMuerte(UUID.randomUUID(), CausaMuerte.COMBATE));
    }

    @Test
    void checkpointsSoloAvanzan() {
        DungeonsConfig.Snapshot base = config(true);
        DefinicionDungeon cripta = base.dungeon("cripta").orElseThrow();
        Punto c0 = new Punto(0, 10, 0, 0, 0);
        Punto c1 = new Punto(5, 10, 5, 0, 0);
        DefinicionDungeon conCheckpoints =
                new DefinicionDungeon(
                        cripta.id(),
                        cripta.nombre(),
                        true,
                        cripta.zona(),
                        cripta.entrada(),
                        List.of(c0, c1),
                        cripta.salida(),
                        cripta.modo(),
                        1,
                        4,
                        cripta.dificultades(),
                        cripta.permiso(),
                        cripta.requisitos(),
                        0,
                        0);
        registro.aplicarConfig(
                new DungeonsConfig.Snapshot(
                        base.general(),
                        base.partida(),
                        base.dificultades(),
                        List.of(conCheckpoints),
                        2));
        Partida p = iniciar("cripta", Dificultad.FACIL, ANA);
        assertEquals(cripta.entrada(), p.puntoDeReaparicion(ANA.id()));
        assertTrue(registro.alcanzarCheckpoint(ANA.id(), 1));
        assertFalse(registro.alcanzarCheckpoint(ANA.id(), 0));
        assertFalse(registro.alcanzarCheckpoint(ANA.id(), 2));
        assertEquals(c1, p.puntoDeReaparicion(ANA.id()));
    }

    @Test
    void recargarNoCambiaLaConfigDeLaPartidaEnCurso() {
        Partida p = iniciar("cripta", Dificultad.FACIL, ANA);
        DungeonsConfig.Snapshot base = config(true);
        DungeonsConfig.ReglasPartida reglas = base.partida();
        DungeonsConfig.Snapshot nueva =
                new DungeonsConfig.Snapshot(
                        base.general(),
                        new DungeonsConfig.ReglasPartida(
                                reglas.readyCheckSegundos(),
                                10,
                                reglas.limiteMinutos(),
                                reglas.afkSegundos(),
                                reglas.hardcoreBanMinutos(),
                                reglas.hardcoreBanMensaje(),
                                reglas.desconexionHardcoreEsMuerte(),
                                reglas.vidaJefePorJugador(),
                                reglas.objetosProhibidos()),
                        base.dificultades(),
                        List.of(),
                        2);
        registro.aplicarConfig(nueva);
        assertEquals(60, p.config().partida().reconexionSegundos());
        assertEquals(EstadoDungeon.EN_PARTIDA, registro.estado("cripta").orElseThrow());
        assertTrue(registro.estado("torre").isEmpty());
        registro.terminarPartida("cripta", ResultadoPartida.CANCELADA);
        registro.finalizarReinicio("cripta");
        assertTrue(registro.estado("cripta").isEmpty());
    }

    @Test
    void reinicioTrasCaidaSoloSinPartida() {
        assertTrue(registro.marcarReiniciando("torre"));
        assertEquals(EstadoDungeon.REINICIANDO, registro.estado("torre").orElseThrow());
        iniciar("cripta", Dificultad.FACIL, ANA);
        assertFalse(registro.marcarReiniciando("cripta"));
        assertFalse(registro.marcarReiniciando("nada"));
    }
}
