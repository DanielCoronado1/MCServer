package com.dolfin.creepers.dungeon.grupo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.grupo.Grupos.Error;
import com.dolfin.creepers.dungeon.grupo.Grupos.Evento;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

class GruposTest {
    static final UUID ANA = UUID.randomUUID();
    static final UUID BETO = UUID.randomUUID();
    static final UUID CARO = UUID.randomUUID();
    static final UUID DANI = UUID.randomUUID();

    final Set<UUID> enPartida = new HashSet<>();
    final Set<UUID> ocupados = new HashSet<>();
    final Set<UUID> enReadyCheck = new HashSet<>();
    final List<Evento> eventos = new ArrayList<>();
    Grupos grupos;

    @BeforeEach
    void crear() {
        grupos =
                new Grupos(
                        new Grupos.Contexto() {
                            @Override
                            public boolean enPartida(UUID jugador) {
                                return enPartida.contains(jugador);
                            }

                            @Override
                            public boolean ocupado(UUID jugador) {
                                return ocupados.contains(jugador) || enPartida.contains(jugador);
                            }

                            @Override
                            public boolean enReadyCheck(UUID jugador) {
                                return enReadyCheck.contains(jugador);
                            }
                        });
        grupos.suscribir(eventos::add);
    }

    void formar(UUID lider, UUID... otros) {
        for (UUID otro : otros) {
            assertEquals(Optional.empty(), grupos.invitar(lider, otro, 0));
            assertEquals(Optional.empty(), grupos.aceptar(otro, 1));
        }
    }

    @Test
    void invitarCreaElGrupoYAceptarLoCompleta() {
        formar(ANA, BETO);
        assertEquals(List.of(ANA, BETO), grupos.miembrosDe(BETO));
        assertEquals(ANA, grupos.de(BETO).orElseThrow().lider());
        assertInstanceOf(Evento.Creado.class, eventos.get(0));
        assertInstanceOf(Evento.Unido.class, eventos.get(eventos.size() - 1));
    }

    @Test
    void sinGrupoUnJugadorEsUnGrupoDeUno() {
        assertEquals(List.of(ANA), grupos.miembrosDe(ANA));
        assertTrue(grupos.puedeDirigir(ANA));
    }

    @Test
    void soloElLiderPuedeDirigirEInvitar() {
        formar(ANA, BETO);
        assertFalse(grupos.puedeDirigir(BETO));
        assertEquals(Optional.of(Error.NO_ES_LIDER), grupos.invitar(BETO, CARO, 2));
    }

    @Test
    void laInvitacionVenceALos60Segundos() {
        grupos.invitar(ANA, BETO, 0);
        assertEquals(Optional.of(Error.SIN_INVITACION), grupos.aceptar(BETO, Grupos.INVITACION_MS));
        grupos.vencer(Grupos.INVITACION_MS);
        assertTrue(eventos.stream().anyMatch(e -> e instanceof Evento.InvitacionVencida));
    }

    @Test
    void laInvitacionVigenteSePuedeAceptarJustoAntesDeVencer() {
        grupos.invitar(ANA, BETO, 0);
        assertEquals(Optional.empty(), grupos.aceptar(BETO, Grupos.INVITACION_MS - 1));
    }

    @Test
    void rechazarDescartaLaInvitacion() {
        grupos.invitar(ANA, BETO, 0);
        assertEquals(Optional.empty(), grupos.rechazar(BETO, 1));
        assertEquals(Optional.of(Error.SIN_INVITACION), grupos.aceptar(BETO, 2));
    }

    @Test
    void noSeInvitaAQuienYaTieneGrupoNiEstaOcupado() {
        formar(ANA, BETO);
        formar(CARO);
        assertEquals(Optional.of(Error.OBJETIVO_EN_GRUPO), grupos.invitar(CARO, BETO, 3));
        ocupados.add(DANI);
        assertEquals(Optional.of(Error.OBJETIVO_OCUPADO), grupos.invitar(ANA, DANI, 3));
    }

    @Test
    void elGrupoTieneUnMaximo() {
        UUID lider = UUID.randomUUID();
        for (int i = 1; i < Grupos.MAX_MIEMBROS; i++) {
            formar(lider, UUID.randomUUID());
        }
        assertEquals(Optional.of(Error.GRUPO_LLENO), grupos.invitar(lider, CARO, 5));
    }

    @Test
    void expulsarEsSoloDelLiderYSoloDeOtros() {
        formar(ANA, BETO, CARO);
        assertEquals(Optional.of(Error.NO_ES_LIDER), grupos.expulsar(BETO, CARO));
        assertEquals(Optional.of(Error.A_UNO_MISMO), grupos.expulsar(ANA, ANA));
        assertEquals(Optional.of(Error.OBJETIVO_NO_ESTA), grupos.expulsar(ANA, DANI));
        assertEquals(Optional.empty(), grupos.expulsar(ANA, BETO));
        assertEquals(List.of(ANA, CARO), grupos.miembrosDe(ANA));
        assertEquals(List.of(BETO), grupos.miembrosDe(BETO));
    }

    @Test
    void siSaleElLiderElLiderazgoPasaAlSiguiente() {
        formar(ANA, BETO, CARO);
        assertEquals(Optional.empty(), grupos.salir(ANA));
        assertEquals(BETO, grupos.de(CARO).orElseThrow().lider());
        Evento.NuevoLider nuevo =
                assertInstanceOf(Evento.NuevoLider.class, eventos.get(eventos.size() - 1));
        assertEquals(BETO, nuevo.lider());
    }

    @Test
    void elNuevoLiderPrefiereUnMiembroConectado() {
        formar(ANA, BETO, CARO);
        grupos.desconectar(BETO, 10);
        grupos.salir(ANA);
        assertEquals(CARO, grupos.de(CARO).orElseThrow().lider());
    }

    @Test
    void salirSinSerLiderNoCambiaElLiderazgo() {
        formar(ANA, BETO, CARO);
        grupos.salir(BETO);
        assertEquals(ANA, grupos.de(ANA).orElseThrow().lider());
        assertTrue(eventos.stream().noneMatch(e -> e instanceof Evento.NuevoLider));
    }

    @Test
    void elGrupoVacioSeDisuelve() {
        formar(ANA, BETO);
        grupos.salir(ANA);
        grupos.salir(BETO);
        assertTrue(grupos.de(BETO).isEmpty());
        assertInstanceOf(Evento.Disuelto.class, eventos.get(eventos.size() - 1));
    }

    @Test
    void salirSinGrupoFalla() {
        assertEquals(Optional.of(Error.SIN_GRUPO), grupos.salir(ANA));
    }

    @Test
    void enPartidaNoSeInvitaNiSeExpulsaNiSeSale() {
        formar(ANA, BETO);
        enPartida.addAll(List.of(ANA, BETO));
        assertEquals(Optional.of(Error.EN_PARTIDA), grupos.invitar(ANA, CARO, 5));
        assertEquals(Optional.of(Error.EN_PARTIDA), grupos.expulsar(ANA, BETO));
        assertEquals(Optional.of(Error.EN_PARTIDA), grupos.salir(BETO));
        assertEquals(Optional.of(Error.EN_PARTIDA), grupos.salir(ANA));
        assertEquals(List.of(ANA, BETO), grupos.miembrosDe(ANA));
    }

    @Test
    void unaInvitacionPendienteNoEntraEnUnGrupoQueYaEstaEnPartida() {
        formar(ANA, BETO);
        grupos.invitar(ANA, CARO, 5);
        enPartida.add(BETO);
        assertEquals(Optional.of(Error.EN_PARTIDA), grupos.aceptar(CARO, 6));
    }

    @Test
    void duranteUnReadyCheckNoSeAceptaANadie() {
        formar(ANA, BETO);
        grupos.invitar(ANA, CARO, 5);
        enReadyCheck.add(ANA);
        assertEquals(Optional.of(Error.EN_READY_CHECK), grupos.aceptar(CARO, 6));
        assertEquals(Optional.of(Error.EN_READY_CHECK), grupos.invitar(ANA, DANI, 6));
    }

    @Test
    void ocupadoNoPuedeUnirseAUnGrupo() {
        grupos.invitar(ANA, BETO, 0);
        ocupados.add(BETO);
        assertEquals(Optional.of(Error.OCUPADO), grupos.aceptar(BETO, 1));
    }

    @Test
    void unMiembroDesconectadoSigueCincoMinutos() {
        formar(ANA, BETO);
        grupos.desconectar(BETO, 1_000);
        grupos.vencer(1_000 + Grupos.DESCONEXION_MS - 1);
        assertEquals(List.of(ANA, BETO), grupos.miembrosDe(ANA));
        grupos.vencer(1_000 + Grupos.DESCONEXION_MS);
        assertEquals(List.of(ANA), grupos.miembrosDe(ANA));
        Evento.Salio salio = assertInstanceOf(Evento.Salio.class, eventos.get(eventos.size() - 1));
        assertEquals(Grupos.MotivoSalida.DESCONEXION, salio.motivo());
    }

    @Test
    void reconectarAntesDelPlazoConservaElSitio() {
        formar(ANA, BETO);
        grupos.desconectar(BETO, 0);
        grupos.conectar(BETO);
        grupos.vencer(Grupos.DESCONEXION_MS * 2);
        assertEquals(List.of(ANA, BETO), grupos.miembrosDe(ANA));
    }

    @Test
    void enPartidaElPlazoDeDesconexionNoCorreYEmpiezaAlTerminar() {
        formar(ANA, BETO);
        enPartida.addAll(List.of(ANA, BETO));
        grupos.desconectar(BETO, 0);
        long t = Grupos.DESCONEXION_MS * 3;
        grupos.vencer(t);
        assertEquals(List.of(ANA, BETO), grupos.miembrosDe(ANA));
        enPartida.clear();
        grupos.vencer(t + Grupos.DESCONEXION_MS - 1);
        assertEquals(List.of(ANA, BETO), grupos.miembrosDe(ANA));
        grupos.vencer(t + Grupos.DESCONEXION_MS);
        assertEquals(List.of(ANA), grupos.miembrosDe(ANA));
    }

    @Test
    void siElLiderCaducaPorDesconexionElLiderazgoPasa() {
        formar(ANA, BETO);
        grupos.desconectar(ANA, 0);
        grupos.vencer(Grupos.DESCONEXION_MS);
        assertEquals(BETO, grupos.de(BETO).orElseThrow().lider());
    }

    @Test
    void limpiarOlvidaTodo() {
        formar(ANA, BETO);
        grupos.limpiar();
        assertTrue(grupos.de(ANA).isEmpty());
    }
}
