package com.dolfin.creepers.dungeon.fila;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.fila.Filas.Efecto;
import com.dolfin.creepers.dungeon.fila.Filas.Entrada;
import com.dolfin.creepers.dungeon.fila.Filas.Motivo;
import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.EstadoDungeon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

class FilasTest {
    static final int CHECK_TICKS = 30 * 20;
    static final UUID ANA = UUID.randomUUID();
    static final UUID BETO = UUID.randomUUID();
    static final UUID CARO = UUID.randomUUID();
    static final UUID DANI = UUID.randomUUID();

    final Map<String, EstadoDungeon> estados = new HashMap<>(Map.of("cripta", EstadoDungeon.LIBRE));
    final Map<UUID, List<UUID>> grupos = new HashMap<>();
    final Set<UUID> conectados = new HashSet<>(List.of(ANA, BETO, CARO, DANI));
    Filas filas;

    @BeforeEach
    void crear() {
        filas =
                new Filas(
                        new Filas.Entorno() {
                            @Override
                            public Optional<EstadoDungeon> estado(String dungeon) {
                                return Optional.ofNullable(estados.get(dungeon));
                            }

                            @Override
                            public List<UUID> miembros(UUID jugador) {
                                return grupos.getOrDefault(jugador, List.of(jugador));
                            }

                            @Override
                            public boolean enLinea(UUID jugador) {
                                return conectados.contains(jugador);
                            }

                            @Override
                            public int readyCheckSegundos() {
                                return 30;
                            }
                        });
    }

    static Entrada entrada(UUID solicitante) {
        return new Entrada(solicitante, Dificultad.MEDIO);
    }

    @SuppressWarnings("unchecked")
    <T extends Efecto> List<T> de(List<Efecto> efectos, Class<T> tipo) {
        return efectos.stream().filter(tipo::isInstance).map(e -> (T) e).toList();
    }

    @Test
    void enDungeonLibreElPrimerGrupoRecibeReadyCheckAlInstante() {
        grupos.put(ANA, List.of(ANA, BETO));
        var efectos = filas.unirse("cripta", entrada(ANA), true, 0);
        var inicio = de(efectos, Efecto.ReadyCheckIniciado.class).get(0);
        assertEquals(CHECK_TICKS, inicio.venceTick());
        assertEquals(Set.of(BETO), inicio.pendientes());
        assertTrue(de(efectos, Efecto.Completa.class).isEmpty());
        assertTrue(filas.tieneReadyCheck(BETO));
    }

    @Test
    void unJugadorSoloQueEntraDirectoCompletaSinConfirmar() {
        var efectos = filas.unirse("cripta", entrada(ANA), true, 0);
        assertEquals(1, de(efectos, Efecto.Completa.class).size());
        assertEquals(0, filas.grupos("cripta"));
    }

    @Test
    void sinSerDirectoElSolicitanteTambienDebeConfirmar() {
        var efectos = filas.unirse("cripta", entrada(ANA), false, 0);
        assertTrue(de(efectos, Efecto.Completa.class).isEmpty());
        assertEquals(Set.of(ANA), de(efectos, Efecto.ReadyCheckIniciado.class).get(0).pendientes());
        var listo = filas.responder(ANA, true, 10);
        assertEquals(1, de(listo, Efecto.Completa.class).size());
    }

    @Test
    void completaCuandoTodosEstanListos() {
        grupos.put(ANA, List.of(ANA, BETO, CARO));
        filas.unirse("cripta", entrada(ANA), true, 0);
        var uno = filas.responder(BETO, true, 5);
        assertEquals(1, de(uno, Efecto.MiembroListo.class).get(0).faltan());
        assertTrue(de(uno, Efecto.Completa.class).isEmpty());
        var todos = filas.responder(CARO, true, 6);
        var completa = de(todos, Efecto.Completa.class).get(0);
        assertEquals(Set.of(ANA, BETO, CARO), completa.miembros());
        assertEquals(0, filas.grupos("cripta"));
    }

    @Test
    void seEntraAunqueElGrupoEstuvieraEnCombate() {
        // La fila no conoce el combate: el ready check es lo único que decide.
        filas.unirse("cripta", entrada(ANA), true, 0);
        assertEquals(0, filas.grupos("cripta"));
    }

    @Test
    void ocupadaLaDungeonEsperaYAlLiberarseEmpiezaElReadyCheck() {
        estados.put("cripta", EstadoDungeon.EN_PARTIDA);
        var efectos = filas.unirse("cripta", entrada(ANA), true, 0);
        assertTrue(efectos.isEmpty());
        assertEquals(OptionalInt.of(1), filas.posicion(ANA));
        estados.put("cripta", EstadoDungeon.REINICIANDO);
        assertTrue(filas.tick(100).isEmpty());
        estados.put("cripta", EstadoDungeon.LIBRE);
        var libre = filas.tick(200);
        assertEquals(1, de(libre, Efecto.ReadyCheckIniciado.class).size());
        assertEquals(
                200 + CHECK_TICKS, de(libre, Efecto.ReadyCheckIniciado.class).get(0).venceTick());
    }

    @Test
    void siUnoRechazaSaleElGrupoYLeTocaAlSiguiente() {
        grupos.put(ANA, List.of(ANA, BETO));
        filas.unirse("cripta", entrada(ANA), true, 0);
        filas.unirse("cripta", entrada(CARO), false, 1);
        var efectos = filas.responder(BETO, false, 50);
        var retirada = de(efectos, Efecto.Retirada.class).get(0);
        assertEquals(Motivo.RECHAZO, retirada.motivo());
        assertEquals(Optional.of(BETO), retirada.culpable());
        assertEquals(Set.of(ANA, BETO), retirada.miembros());
        var siguiente = de(efectos, Efecto.ReadyCheckIniciado.class).get(0);
        assertEquals(CARO, siguiente.entrada().solicitante());
        assertEquals(50 + CHECK_TICKS, siguiente.venceTick());
    }

    @Test
    void siNoRespondenAlPlazoSalenYLeTocaAlSiguiente() {
        grupos.put(ANA, List.of(ANA, BETO));
        filas.unirse("cripta", entrada(ANA), true, 0);
        filas.unirse("cripta", entrada(CARO), false, 1);
        assertTrue(filas.tick(CHECK_TICKS - 1).isEmpty());
        var efectos = filas.tick(CHECK_TICKS);
        assertEquals(Motivo.SIN_RESPUESTA, de(efectos, Efecto.Retirada.class).get(0).motivo());
        assertEquals(
                CARO, de(efectos, Efecto.ReadyCheckIniciado.class).get(0).entrada().solicitante());
        assertTrue(filas.posicion(ANA).isEmpty());
    }

    @Test
    void conDosGruposSoloElPrimeroTieneReadyCheck() {
        grupos.put(ANA, List.of(ANA, BETO));
        grupos.put(CARO, List.of(CARO, DANI));
        filas.unirse("cripta", entrada(ANA), true, 0);
        filas.unirse("cripta", entrada(CARO), true, 1);
        assertTrue(filas.tieneReadyCheck(BETO));
        assertFalse(filas.tieneReadyCheck(DANI));
        assertEquals(OptionalInt.of(2), filas.posicion(DANI));
    }

    @Test
    void alAvanzarSeAvisaElNuevoPuesto() {
        estados.put("cripta", EstadoDungeon.EN_PARTIDA);
        filas.unirse("cripta", entrada(ANA), false, 0);
        filas.unirse("cripta", entrada(BETO), false, 0);
        filas.unirse("cripta", entrada(CARO), false, 0);
        var efectos = filas.salir(ANA, 1);
        var avances = de(efectos, Efecto.PosicionCambiada.class);
        assertEquals(2, avances.size());
        assertEquals(BETO, avances.get(0).entrada().solicitante());
        assertEquals(1, avances.get(0).posicion());
        assertEquals(2, avances.get(0).total());
        assertEquals(CARO, avances.get(1).entrada().solicitante());
        assertEquals(2, avances.get(1).posicion());
        assertEquals(Set.of(CARO), avances.get(1).miembros());
    }

    @Test
    void quienNoAvanzaNoRecibeAviso() {
        estados.put("cripta", EstadoDungeon.EN_PARTIDA);
        filas.unirse("cripta", entrada(ANA), false, 0);
        filas.unirse("cripta", entrada(BETO), false, 0);
        var efectos = filas.unirse("cripta", entrada(CARO), false, 0);
        assertTrue(de(efectos, Efecto.PosicionCambiada.class).isEmpty());
    }

    @Test
    void unGrupoConTodosDesconectadosSaleDeLaFila() {
        estados.put("cripta", EstadoDungeon.EN_PARTIDA);
        grupos.put(BETO, List.of(BETO, CARO));
        filas.unirse("cripta", entrada(ANA), false, 0);
        filas.unirse("cripta", entrada(BETO), false, 0);
        conectados.removeAll(List.of(BETO, CARO));
        var efectos = filas.tick(10);
        var retirada = de(efectos, Efecto.Retirada.class).get(0);
        assertEquals(Motivo.SIN_CONEXION, retirada.motivo());
        assertEquals(Set.of(BETO, CARO), retirada.miembros());
        assertEquals(1, filas.grupos("cripta"));
    }

    @Test
    void conAlgunMiembroConectadoNoSale() {
        estados.put("cripta", EstadoDungeon.EN_PARTIDA);
        grupos.put(BETO, List.of(BETO, CARO));
        filas.unirse("cripta", entrada(BETO), false, 0);
        conectados.remove(BETO);
        assertTrue(de(filas.tick(10), Efecto.Retirada.class).isEmpty());
    }

    @Test
    void salirCancelaElReadyCheckYPasaAlSiguiente() {
        grupos.put(ANA, List.of(ANA, BETO));
        filas.unirse("cripta", entrada(ANA), true, 0);
        filas.unirse("cripta", entrada(CARO), false, 1);
        var efectos = filas.salir(BETO, 20);
        assertEquals(Motivo.CANCELADA, de(efectos, Efecto.Retirada.class).get(0).motivo());
        assertEquals(1, de(efectos, Efecto.ReadyCheckIniciado.class).size());
        assertFalse(filas.tieneReadyCheck(ANA));
    }

    @Test
    void siLaDungeonSeOcupaDuranteElReadyCheckSeCancelaYSeEspera() {
        grupos.put(ANA, List.of(ANA, BETO));
        filas.unirse("cripta", entrada(ANA), true, 0);
        estados.put("cripta", EstadoDungeon.EN_PARTIDA);
        assertTrue(filas.tick(10).isEmpty());
        assertFalse(filas.tieneReadyCheck(BETO));
        assertEquals(OptionalInt.of(1), filas.posicion(ANA));
        estados.put("cripta", EstadoDungeon.LIBRE);
        assertEquals(1, de(filas.tick(20), Efecto.ReadyCheckIniciado.class).size());
    }

    @Test
    void siLaDungeonSeDeshabilitaTodosSalen() {
        estados.put("cripta", EstadoDungeon.EN_PARTIDA);
        filas.unirse("cripta", entrada(ANA), false, 0);
        filas.unirse("cripta", entrada(BETO), false, 0);
        estados.put("cripta", EstadoDungeon.DESHABILITADA);
        var retiradas = de(filas.tick(5), Efecto.Retirada.class);
        assertEquals(2, retiradas.size());
        assertTrue(retiradas.stream().allMatch(r -> r.motivo() == Motivo.CERRADA));
        assertEquals(0, filas.grupos("cripta"));
    }

    @Test
    void siElGrupoCambiaSeExigeLaRespuestaDeLosMiembrosActuales() {
        grupos.put(ANA, List.of(ANA, BETO));
        filas.unirse("cripta", entrada(ANA), true, 0);
        grupos.put(ANA, List.of(ANA));
        var efectos = filas.tick(5);
        assertEquals(1, de(efectos, Efecto.Completa.class).size());
        assertEquals(Set.of(ANA), de(efectos, Efecto.Completa.class).get(0).miembros());
    }

    @Test
    void responderSinReadyCheckNoHaceNada() {
        estados.put("cripta", EstadoDungeon.EN_PARTIDA);
        filas.unirse("cripta", entrada(ANA), false, 0);
        assertTrue(filas.responder(ANA, true, 1).isEmpty());
        assertTrue(filas.responder(DANI, false, 1).isEmpty());
        assertEquals(1, filas.grupos("cripta"));
    }

    @Test
    void trasCompletarSeLiberaElPuestoYElSiguienteEsperaAQueSeOcupeLaDungeon() {
        grupos.put(ANA, List.of(ANA, BETO));
        filas.unirse("cripta", entrada(ANA), true, 0);
        filas.unirse("cripta", entrada(CARO), false, 1);
        var efectos = filas.responder(BETO, true, 5);
        assertEquals(1, de(efectos, Efecto.Completa.class).size());
        assertEquals(1, de(efectos, Efecto.PosicionCambiada.class).size());
        assertEquals(1, filas.grupos("cripta"));
    }

    @Test
    void limpiarOlvidaLasFilas() {
        estados.put("cripta", EstadoDungeon.EN_PARTIDA);
        filas.unirse("cripta", entrada(ANA), false, 0);
        filas.limpiar();
        assertEquals(0, filas.grupos("cripta"));
    }
}
