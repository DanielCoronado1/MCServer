package com.dolfin.creepers.dungeon.muerte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.config.DungeonsConfig;

import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

class BanHardcoreTest {
    static final Instant AHORA = Instant.parse("2026-10-21T12:00:00Z");
    static final NameAndId PEPE = new NameAndId(UUID.randomUUID(), "Pepe");

    @Test
    void laEntradaLlevaFuentePropiaYVenceALosMinutos() {
        UserBanListEntry entrada = BanHardcore.entrada(PEPE, AHORA, 60, "motivo");
        assertEquals("DolfinDungeons", entrada.getSource());
        assertEquals("motivo", entrada.getReason());
        assertEquals(Date.from(AHORA), entrada.getCreated());
        assertEquals(Date.from(AHORA.plus(Duration.ofMinutes(60))), entrada.getExpires());
        assertTrue(BanHardcore.esDeDungeon(entrada));
    }

    @Test
    void elMotivoPorDefectoEsElTextoDeLaPantallaDeKick() {
        String plantilla = DungeonsConfig.ReglasPartida.porDefecto().hardcoreBanMensaje();
        assertEquals(
                "Has muerto en la dungeon Cripta · Hardcore. Podrás volver en 60 min",
                BanHardcore.motivo(plantilla, "Cripta", 60));
    }

    @Test
    void nuncaPisaUnBanManual() {
        assertTrue(BanHardcore.puedeEscribirse(null));
        assertTrue(BanHardcore.puedeEscribirse("DolfinDungeons"));
        assertFalse(BanHardcore.puedeEscribirse("Server"));
        assertFalse(BanHardcore.puedeEscribirse("Admin"));
        assertFalse(
                BanHardcore.esDeDungeon(
                        new UserBanListEntry(PEPE, Date.from(AHORA), "Admin", null, "x")));
    }

    @Test
    void minutosRestantesRedondeaHaciaArribaYNoBajaDeUno() {
        Date expira = Date.from(AHORA.plus(Duration.ofMinutes(60)));
        assertEquals(60, BanHardcore.minutosRestantes(expira, AHORA));
        assertEquals(37, BanHardcore.minutosRestantes(expira, AHORA.plusSeconds(23 * 60 + 30)));
        assertEquals(1, BanHardcore.minutosRestantes(expira, AHORA.plusSeconds(3599)));
        assertEquals(1, BanHardcore.minutosRestantes(expira, AHORA.plusSeconds(4000)));
    }

    @Test
    void elMensajeAlConectarIncluyeElTiempoRestante() {
        UserBanListEntry entrada = BanHardcore.entrada(PEPE, AHORA, 60, "Has muerto");
        assertEquals(
                "Has muerto\n\nTiempo restante: 50 min",
                BanHardcore.mensajeAlConectar(entrada, AHORA.plusSeconds(600)).getString());
    }
}
