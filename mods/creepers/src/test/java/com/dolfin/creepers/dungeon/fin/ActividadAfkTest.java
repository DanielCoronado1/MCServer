package com.dolfin.creepers.dungeon.fin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.fin.ActividadAfk.Huella;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

class ActividadAfkTest {
    static final UUID ANA = UUID.randomUUID();
    static final UUID BETO = UUID.randomUUID();
    static final Huella QUIETO = new Huella(1, 64, 1, 0, 0);

    ActividadAfk afk = new ActividadAfk();

    @Test
    void quienNoCambiaDeHuellaVenceAlLimite() {
        afk.observar(ANA, QUIETO, 0);
        for (int t = 1; t < 300; t++) {
            afk.observar(ANA, QUIETO, t);
        }
        assertTrue(afk.vencidos(299, 300).isEmpty());
        assertEquals(List.of(ANA), afk.vencidos(300, 300));
    }

    @Test
    void moverseOMirarReiniciaLaCuenta() {
        afk.observar(ANA, QUIETO, 0);
        afk.observar(ANA, new Huella(1, 64, 1, 15, 0), 200);
        assertTrue(afk.vencidos(400, 300).isEmpty());
        assertEquals(List.of(ANA), afk.vencidos(500, 300));
    }

    @Test
    void tocarReiniciaLaCuentaSinHuella() {
        afk.observar(ANA, QUIETO, 0);
        afk.tocar(ANA, 250);
        assertTrue(afk.vencidos(500, 300).isEmpty());
        afk.tocar(BETO, 250);
        assertTrue(afk.vencidos(9999, 300).contains(ANA));
        assertEquals(1, afk.vencidos(9999, 300).size());
    }

    @Test
    void quienDejaDeVigilarseEmpiezaDeCeroAlVolver() {
        afk.observar(ANA, QUIETO, 0);
        afk.observar(BETO, QUIETO, 0);
        afk.retener(Set.of(BETO));
        afk.observar(ANA, QUIETO, 290);
        assertEquals(List.of(BETO), afk.vencidos(300, 300));
        assertEquals(2, afk.vencidos(590, 300).size());
    }
}
