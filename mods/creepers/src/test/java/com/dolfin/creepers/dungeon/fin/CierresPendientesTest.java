package com.dolfin.creepers.dungeon.fin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.fin.CierresPendientes.Cierre;
import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;

import org.junit.jupiter.api.Test;

import java.util.List;

class CierresPendientesTest {
    CierresPendientes cierres = new CierresPendientes();

    @Test
    void elPrimerCierreGanaYLosDemasSeIgnoran() {
        assertTrue(cierres.programar("cripta", ResultadoPartida.VICTORIA, 1000));
        assertFalse(cierres.programar("cripta", ResultadoPartida.DERROTA, 500));
        assertEquals(ResultadoPartida.VICTORIA, cierres.de("cripta").orElseThrow().resultado());
    }

    @Test
    void soloVencenLosQueLlegaronYSeQuitan() {
        cierres.programar("cripta", ResultadoPartida.VICTORIA, 1000);
        cierres.programar("torre", ResultadoPartida.DERROTA, 2000);
        assertTrue(cierres.vencidos(999).isEmpty());
        assertEquals(
                List.of(new Cierre("cripta", ResultadoPartida.VICTORIA, 1000)),
                cierres.vencidos(1000));
        assertTrue(cierres.de("cripta").isEmpty());
        assertEquals(1, cierres.vencidos(5000).size());
        assertTrue(cierres.vencidos(5000).isEmpty());
    }

    @Test
    void descartarPermiteProgramarDeNuevo() {
        cierres.programar("cripta", ResultadoPartida.DERROTA, 10);
        cierres.descartar("cripta");
        assertTrue(cierres.programar("cripta", ResultadoPartida.VICTORIA, 20));
    }
}
