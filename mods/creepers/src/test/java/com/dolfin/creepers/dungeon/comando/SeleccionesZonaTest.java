package com.dolfin.creepers.dungeon.comando;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.comando.SeleccionesZona.Esquina;
import com.dolfin.creepers.dungeon.modelo.Zona;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

class SeleccionesZonaTest {
    static final UUID ANA = UUID.randomUUID();
    static final UUID BETO = UUID.randomUUID();

    SeleccionesZona selecciones = new SeleccionesZona();

    @Test
    void sinLasDosEsquinasNoHayZona() {
        assertTrue(selecciones.zona(ANA).isEmpty());
        selecciones.marcarPrimera(ANA, new Esquina(1, 2, 3));
        assertTrue(selecciones.zona(ANA).isEmpty());
    }

    @Test
    void lasEsquinasEnCualquierOrdenDanLaCajaInclusiva() {
        selecciones.marcarSegunda(ANA, new Esquina(10, 0, -5));
        selecciones.marcarPrimera(ANA, new Esquina(2, 8, 4));
        assertEquals(Optional.of(new Zona(2, 0, -5, 10, 8, 4)), selecciones.zona(ANA));
    }

    @Test
    void marcarDeNuevoSustituyeLaEsquina() {
        selecciones.marcarPrimera(ANA, new Esquina(0, 0, 0));
        selecciones.marcarSegunda(ANA, new Esquina(5, 5, 5));
        selecciones.marcarPrimera(ANA, new Esquina(3, 3, 3));
        assertEquals(Optional.of(new Zona(3, 3, 3, 5, 5, 5)), selecciones.zona(ANA));
    }

    @Test
    void cadaAdministradorTieneLaSuyaYSePuedeOlvidar() {
        selecciones.marcarPrimera(ANA, new Esquina(0, 0, 0));
        selecciones.marcarSegunda(ANA, new Esquina(1, 1, 1));
        assertTrue(selecciones.zona(BETO).isEmpty());
        selecciones.olvidar(ANA);
        assertTrue(selecciones.zona(ANA).isEmpty());
    }
}
