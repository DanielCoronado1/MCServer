package com.dolfin.creepers.dungeon.reglas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Optional;

class PropietariosTest {
    static final class Nodo {
        final String nombre;
        final boolean jugador;
        Nodo dueno;

        Nodo(String nombre, boolean jugador, Nodo dueno) {
            this.nombre = nombre;
            this.jugador = jugador;
            this.dueno = dueno;
        }
    }

    static Optional<Nodo> resolver(Nodo origen) {
        return Propietarios.jugadorResponsable(origen, n -> n.jugador, n -> n.dueno);
    }

    @Test
    void unJugadorSeResuelveASiMismo() {
        Nodo ana = new Nodo("ana", true, null);
        assertEquals(Optional.of(ana), resolver(ana));
    }

    @Test
    void proyectilYMascotaLlevanAlDueno() {
        Nodo ana = new Nodo("ana", true, null);
        Nodo flecha = new Nodo("flecha", false, ana);
        Nodo lobo = new Nodo("lobo", false, ana);
        assertEquals(Optional.of(ana), resolver(flecha));
        assertEquals(Optional.of(ana), resolver(lobo));
    }

    @Test
    void cadenasLargasSeSiguen() {
        Nodo ana = new Nodo("ana", true, null);
        Nodo lobo = new Nodo("lobo", false, ana);
        Nodo flecha = new Nodo("flecha disparada por el lobo", false, lobo);
        assertEquals(Optional.of(ana), resolver(flecha));
    }

    @Test
    void sinDuenoJugadorNoHayResponsable() {
        Nodo zombi = new Nodo("zombi", false, null);
        Nodo flecha = new Nodo("flecha", false, new Nodo("esqueleto", false, null));
        assertTrue(resolver(zombi).isEmpty());
        assertTrue(resolver(flecha).isEmpty());
    }

    @Test
    void origenNuloNoHayResponsable() {
        assertTrue(resolver(null).isEmpty());
    }

    @Test
    void unCicloDeDuenosTermina() {
        Nodo a = new Nodo("a", false, null);
        Nodo b = new Nodo("b", false, a);
        a.dueno = b;
        assertTrue(resolver(a).isEmpty());
    }

    @Test
    void unaCadenaMasLargaQueElLimiteNoLlegaAlJugador() {
        Nodo actual = new Nodo("ana", true, null);
        for (int i = 0; i <= Propietarios.SALTOS_MAX; i++) {
            actual = new Nodo("eslabon" + i, false, actual);
        }
        assertTrue(resolver(actual).isEmpty());
    }
}
