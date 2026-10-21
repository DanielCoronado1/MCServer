package com.dolfin.creepers.dungeon.muerte;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.config.DungeonsConfig;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.BanListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Ban temporal por morir en hardcore (#16). Es un ban vanilla ({@link UserBanListEntry} en {@code
 * banned-players.json}) con la fuente {@value #FUENTE}: aparece en {@code /banlist}, expira solo y
 * sigue funcionando aunque se desinstale el mod. La fuente distingue estos bans de los manuales:
 * {@code pardon} (#13) solo quita los que tengan esta fuente y nunca se pisa un ban manual vigente.
 */
public final class BanHardcore {
    /** Fuente de los bans del mod; {@code pardon} (#13) filtra por ella. */
    public static final String FUENTE = "DolfinDungeons";

    private BanHardcore() {}

    /**
     * Escribe el ban de {@code jugador} por morir en {@code dungeon}, salvo que ya tenga un ban
     * vigente de otra fuente (un {@code /ban} manual no se toca).
     *
     * @return la entrada escrita, o vacío si se respetó un ban ajeno
     */
    public static Optional<UserBanListEntry> aplicar(
            MinecraftServer server,
            NameAndId jugador,
            String dungeon,
            DungeonsConfig.ReglasPartida reglas,
            Instant ahora) {
        UserBanList bans = server.getPlayerList().getBans();
        UserBanListEntry existente = bans.get(jugador);
        if (!puedeEscribirse(existente == null ? null : existente.getSource())) {
            DolfinCreepers.LOGGER.info(
                    "event=dungeon_hardcore_ban_skipped player={} existingSource={}",
                    jugador.name(),
                    existente.getSource());
            return Optional.empty();
        }
        int minutos = reglas.hardcoreBanMinutos();
        UserBanListEntry entrada =
                entrada(
                        jugador,
                        ahora,
                        minutos,
                        motivo(reglas.hardcoreBanMensaje(), dungeon, minutos));
        bans.add(entrada);
        DolfinCreepers.LOGGER.info(
                "event=dungeon_hardcore_ban player={} dungeon={} minutes={} expires={}",
                jugador.name(),
                dungeon,
                minutos,
                entrada.getExpires().toInstant());
        return Optional.of(entrada);
    }

    /**
     * Entrada de ban con fuente {@value #FUENTE} que vence {@code minutos} después de {@code
     * ahora}.
     */
    public static UserBanListEntry entrada(
            NameAndId jugador, Instant ahora, int minutos, String motivo) {
        return new UserBanListEntry(
                jugador,
                Date.from(ahora),
                FUENTE,
                Date.from(ahora.plus(Duration.ofMinutes(minutos))),
                motivo);
    }

    /**
     * Sustituye {@code %dungeon%} y {@code %minutos%} en la plantilla {@code hardcoreBanMensaje}.
     */
    public static String motivo(String plantilla, String dungeon, int minutos) {
        return plantilla
                .replace("%dungeon%", dungeon)
                .replace("%minutos%", Integer.toString(minutos));
    }

    /** Solo se escribe si no hay ban o el que hay es nuestro (se renueva). */
    public static boolean puedeEscribirse(String fuenteExistente) {
        return fuenteExistente == null || FUENTE.equals(fuenteExistente);
    }

    /** Es un ban puesto por las dungeons. */
    public static boolean esDeDungeon(BanListEntry<?> entrada) {
        return FUENTE.equals(entrada.getSource());
    }

    /** Minutos que faltan para que venza, redondeando hacia arriba y como mínimo 1. */
    public static long minutosRestantes(Date expira, Instant ahora) {
        long milis = expira.getTime() - ahora.toEpochMilli();
        return Math.max(1, (milis + 59_999) / 60_000);
    }

    /** Pantalla al intentar entrar baneado: el motivo y el tiempo que falta. */
    public static Component mensajeAlConectar(UserBanListEntry entrada, Instant ahora) {
        String restante =
                entrada.getExpires() == null
                        ? ""
                        : "\n\nTiempo restante: "
                                + minutosRestantes(entrada.getExpires(), ahora)
                                + " min";
        return Component.literal(entrada.getReason() + restante);
    }
}
