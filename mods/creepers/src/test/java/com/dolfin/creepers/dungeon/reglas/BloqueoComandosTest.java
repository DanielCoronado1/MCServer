package com.dolfin.creepers.dungeon.reglas;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class BloqueoComandosTest {
    @Test
    void losTeletransportesDeEssentialCommandsSeBloquean() {
        for (String ruta :
                List.of(
                        "home.tp",
                        "home_tp_others",
                        "tpa",
                        "tpahere",
                        "tpaccept",
                        "tpdeny",
                        "back",
                        "spawn.tp",
                        "warp.tp",
                        "warp.tp_named",
                        "randomteleport")) {
            assertTrue(BloqueoComandos.bloquea("essentialcommands", ruta), ruta);
        }
    }

    @Test
    void lasVentajasSeBloqueanConSusVariantes() {
        assertTrue(BloqueoComandos.bloquea("essentialcommands", "fly.self"));
        assertTrue(BloqueoComandos.bloquea("essentialcommands", "fly.others"));
        assertTrue(BloqueoComandos.bloquea("essentialcommands", "heal.self"));
        assertTrue(BloqueoComandos.bloquea("essentialcommands", "enderchest"));
    }

    @Test
    void loQueNoMuevaNiDeVentajaSePermite() {
        assertFalse(BloqueoComandos.bloquea("essentialcommands", "home.set"));
        assertFalse(BloqueoComandos.bloquea("essentialcommands", "nickname.self"));
        assertFalse(BloqueoComandos.bloquea("essentialcommands", "motd"));
    }

    @Test
    void unaRutaNoCubreOtraQueSoloComienzaIgual() {
        assertFalse(BloqueoComandos.bloquea("essentialcommands", "tpahereplus"));
        assertFalse(BloqueoComandos.bloquea("essentialcommands", "backup"));
    }

    @Test
    void otrosEspaciosDeNombresNoSeTocan() {
        assertFalse(BloqueoComandos.bloquea("minecraft", "tpa"));
        assertFalse(BloqueoComandos.bloquea("dolfin.dungeon", "use"));
    }
}
