package com.dolfin.creepers.dungeon.fin;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.ServicioPartidas;
import com.dolfin.creepers.dungeon.eventos.BusEventos;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.PartidaIniciada;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.PartidaTerminada;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.VictoriaDeclarada;
import com.dolfin.creepers.dungeon.fin.CierresPendientes.Cierre;
import com.dolfin.creepers.dungeon.fin.ReglasFin.Premios;
import com.dolfin.creepers.dungeon.fin.RelojPartida.Avance;
import com.dolfin.creepers.dungeon.modelo.EstadoMiembro;
import com.dolfin.creepers.dungeon.modelo.Miembro;
import com.dolfin.creepers.dungeon.modelo.MotivoSalida;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;
import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;
import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Fin de toda partida (#17): bossbar con el tiempo, avisos y fin por límite de tiempo, expulsión
 * por AFK, victoria, derrota y lo que sigue al terminar (récords, cooldown y recompensas, #22).
 *
 * <p>Victoria: {@link #declararVictoria} (la llama {@link JefeDungeon} al caer el jefe, o cualquier
 * otro módulo) marca la victoria, guarda y anuncia el tiempo, y abre la ventana de botín; al
 * acabarla la partida termina con {@code VICTORIA}. Derrota: {@link #derrota} (el {@code
 * Terminador} de {@code FinPorBajas}) y el límite de tiempo avisan al grupo y terminan a los {@code
 * salidaDerrotaSegundos}. Ambos cierres son excluyentes: el primero que se programa gana.
 *
 * <p>{@link #tick} se llama una vez por segundo.
 */
public final class FinDePartida {
    private static final long AVISO_VISIBLE_MILLIS = 10_000;
    private static final long SEGUNDOS_ROJO = 60;
    private static final long SEGUNDOS_AMARILLO = 600;

    /** Estado de la cuenta atrás de una partida y del último aviso mostrado en la bossbar. */
    private static final class Seguimiento {
        final RelojPartida reloj;
        int avisoMinutos;
        long avisoHastaMillis;

        Seguimiento(RelojPartida reloj) {
            this.reloj = reloj;
        }
    }

    private final RegistroDungeons registro;
    private final ServicioPartidas partidas;
    private final HistorialDungeons historial;
    private final Supplier<MinecraftServer> server;
    private final Recompensas recompensas;
    private final BarrasPartida barras = new BarrasPartida();
    private final CierresPendientes cierres = new CierresPendientes();
    private final ActividadAfk afk = new ActividadAfk();
    private final Map<String, Seguimiento> seguimientos = new HashMap<>();

    public FinDePartida(
            RegistroDungeons registro,
            ServicioPartidas partidas,
            HistorialDungeons historial,
            Supplier<MinecraftServer> server) {
        this.registro = registro;
        this.partidas = partidas;
        this.historial = historial;
        this.server = server;
        this.recompensas = new Recompensas(registro, historial);
    }

    public void suscribir(BusEventos bus) {
        bus.suscribir(PartidaIniciada.class, e -> refrescarBarra(e.partida()));
        bus.suscribir(VictoriaDeclarada.class, e -> alVictoria(e.partida()));
        bus.suscribir(PartidaTerminada.class, e -> alTerminar(e.partida(), e.resultado()));
    }

    /**
     * Da la victoria a la partida de {@code dungeonId}: guarda el récord, anuncia y abre la ventana
     * de botín. Falla si no hay partida activa o ya se decidió su final (victoria o derrota).
     */
    public boolean declararVictoria(String dungeonId) {
        return cierres.de(dungeonId).isEmpty() && registro.declararVictoria(dungeonId).isPresent();
    }

    /** Victoria de la partida activa cuya zona contiene la posición donde cayó el jefe. */
    public boolean jefeCaido(double x, double y, double z) {
        return registro.partidas().stream()
                .filter(p -> p.activa() && p.dungeon().zona().contiene(x, y, z))
                .findFirst()
                .map(p -> declararVictoria(p.dungeon().id()))
                .orElse(false);
    }

    /** Derrota de la partida de {@code dungeonId}: aviso y salida a los pocos segundos. */
    public void derrota(MinecraftServer s, String dungeonId) {
        registro.partida(dungeonId).ifPresent(p -> programarCierre(s, p, ResultadoPartida.DERROTA));
    }

    /** Entrega al conectarse lo que quedó pendiente de una victoria. */
    public void alConectarse(ServerPlayer jugador) {
        recompensas.alConectarse(jugador);
    }

    /** Una vez por segundo: cierres vencidos, tiempo y bossbar de cada partida, y AFK. */
    public void tick(MinecraftServer s) {
        long ahora = System.currentTimeMillis();
        for (Cierre cierre : cierres.vencidos(ahora)) {
            partidas.terminar(s, cierre.dungeon(), cierre.resultado());
        }
        for (Partida partida : List.copyOf(registro.partidas())) {
            avanzar(s, partida, ahora);
        }
        vigilarAfk(s, ahora / 1000);
    }

    private void avanzar(MinecraftServer s, Partida partida, long ahora) {
        if (!partida.activa()) {
            return;
        }
        Seguimiento seg = seguimiento(partida);
        long transcurrido = Math.max(0, (ahora - partida.inicioMillis()) / 1000);
        if (cierres.de(partida.dungeon().id()).isEmpty()) {
            Avance avance = seg.reloj.avanzar(transcurrido);
            avance.avisoMinutos().ifPresent(m -> avisarTiempo(s, partida, seg, m, ahora));
            if (avance.agotado()) {
                programarCierre(s, partida, ResultadoPartida.TIEMPO_AGOTADO);
            }
        }
        mostrarBarra(s, partida, seg, transcurrido, ahora);
    }

    private void avisarTiempo(
            MinecraftServer s, Partida partida, Seguimiento seg, int minutos, long ahora) {
        seg.avisoMinutos = minutos;
        seg.avisoHastaMillis = ahora + AVISO_VISIBLE_MILLIS;
        ChatFin.alGrupo(s, partida, TextosFin.avisoTiempo(minutos), ChatFormatting.GOLD);
    }

    private boolean programarCierre(
            MinecraftServer s, Partida partida, ResultadoPartida resultado) {
        int espera = partida.config().fin().salidaDerrotaSegundos();
        long cuando = System.currentTimeMillis() + espera * 1000L;
        if (partida.victoriaDeclarada()
                || !cierres.programar(partida.dungeon().id(), resultado, cuando)) {
            return false;
        }
        String aviso =
                resultado == ResultadoPartida.TIEMPO_AGOTADO
                        ? TextosFin.avisoTiempoAgotado(espera)
                        : TextosFin.avisoDerrota(espera);
        ChatFin.alGrupo(s, partida, aviso, ChatFormatting.RED);
        DolfinCreepers.LOGGER.info(
                "event=dungeon_closing dungeon={} result={} exitInSeconds={} match={}",
                partida.dungeon().id(),
                resultado,
                espera,
                partida.id());
        return true;
    }

    private void alVictoria(Partida partida) {
        MinecraftServer s = server.get();
        if (s == null) {
            return;
        }
        long ahora = System.currentTimeMillis();
        int ventana = partida.config().fin().ventanaBotinSegundos();
        cierres.programar(
                partida.dungeon().id(), ResultadoPartida.VICTORIA, ahora + ventana * 1000L);
        Marca marca = ReglasFin.marca(partida, ahora - partida.inicioMillis(), ahora);
        boolean record =
                historial
                        .records()
                        .esRecord(marca.dungeon(), marca.dificultad(), marca.tiempoMillis());
        Map<UUID, Premios> premios = registrarVictoria(marca);
        anunciarVictoria(s, partida, marca, record);
        ChatFin.alGrupo(s, partida, TextosFin.avisoBotin(ventana), ChatFormatting.GREEN);
        premios.forEach((jugador, p) -> recompensas.otorgar(s, partida, jugador, p));
        DolfinCreepers.LOGGER.info(
                "event=dungeon_victory dungeon={} difficulty={} timeMs={} record={} members={}"
                        + " match={}",
                marca.dungeon(),
                marca.dificultad().id(),
                marca.tiempoMillis(),
                record,
                marca.miembros().size(),
                partida.id());
        refrescarBarra(partida);
    }

    /**
     * Calcula los premios de cada ganador (antes de contar esta victoria), guarda la marca y marca
     * la dungeon como completada.
     */
    private Map<UUID, Premios> registrarVictoria(Marca marca) {
        Map<UUID, Premios> premios = new HashMap<>();
        for (Marca.Integrante m : marca.miembros()) {
            premios.put(
                    m.id(),
                    ReglasFin.premios(
                            historial.records(), m.id(), marca.dungeon(), marca.dificultad()));
            historial.progreso().marcarCompletada(m.id(), marca.dungeon());
        }
        historial.records().anadir(marca);
        historial.guardar();
        return premios;
    }

    private void anunciarVictoria(MinecraftServer s, Partida partida, Marca marca, boolean record) {
        List<String> nombres = marca.miembros().stream().map(Marca.Integrante::nombre).toList();
        String dungeon = partida.dungeon().nombre();
        ChatFin.atodos(
                s,
                TextosFin.anuncioVictoria(
                        nombres, dungeon, marca.dificultad(), marca.tiempoMillis() / 1000),
                ChatFormatting.GREEN);
        if (record) {
            ChatFin.atodos(
                    s,
                    TextosFin.anuncioRecord(dungeon, marca.dificultad(), marca.tiempoMillis()),
                    ChatFormatting.GOLD);
        }
    }

    private void alTerminar(Partida partida, ResultadoPartida resultado) {
        String dungeon = partida.dungeon().id();
        barras.quitar(dungeon);
        seguimientos.remove(dungeon);
        cierres.descartar(dungeon);
        long ahora = System.currentTimeMillis();
        List<UUID> sujetos = ReglasFin.sujetosACooldown(partida, resultado);
        long fin = ReglasFin.finDeCooldown(partida, ahora);
        sujetos.forEach(u -> historial.progreso().iniciarCooldown(u, dungeon, fin));
        historial.progreso().limpiarVencidos(ahora);
        if (!sujetos.isEmpty()) {
            historial.guardar();
        }
    }

    private Seguimiento seguimiento(Partida partida) {
        return seguimientos.computeIfAbsent(
                partida.dungeon().id(),
                id -> new Seguimiento(new RelojPartida(partida.limiteMinutos())));
    }

    private void refrescarBarra(Partida partida) {
        MinecraftServer s = server.get();
        if (s != null) {
            long ahora = System.currentTimeMillis();
            long transcurrido = Math.max(0, (ahora - partida.inicioMillis()) / 1000);
            mostrarBarra(s, partida, seguimiento(partida), transcurrido, ahora);
        }
    }

    private void mostrarBarra(
            MinecraftServer s, Partida partida, Seguimiento seg, long transcurrido, long ahora) {
        Optional<Cierre> cierre = cierres.de(partida.dungeon().id());
        if (cierre.isPresent()) {
            long restante = Math.max(0, cierre.get().cuandoMillis() - ahora);
            long total = Math.max(1, ventanaTotalMillis(partida, cierre.get().resultado()));
            boolean victoria = cierre.get().resultado() == ResultadoPartida.VICTORIA;
            barras.mostrar(
                    s,
                    partida,
                    TextosFin.barraCierre(
                            partida.dungeon().nombre(), victoria, (restante + 999) / 1000),
                    (float) restante / total,
                    victoria ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.RED);
            return;
        }
        String titulo =
                TextosFin.barraJugando(
                        partida.dungeon().nombre(),
                        partida.dificultad(),
                        transcurrido,
                        partida.limiteMinutos());
        if (ahora < seg.avisoHastaMillis) {
            titulo = TextosFin.barraConAviso(titulo, seg.avisoMinutos);
        }
        long restante = seg.reloj.restanteSegundos(transcurrido);
        barras.mostrar(
                s,
                partida,
                titulo,
                (float) restante / (partida.limiteMinutos() * 60f),
                colorSegunRestante(restante));
    }

    private static long ventanaTotalMillis(Partida partida, ResultadoPartida resultado) {
        return 1000L
                * (resultado == ResultadoPartida.VICTORIA
                        ? partida.config().fin().ventanaBotinSegundos()
                        : partida.config().fin().salidaDerrotaSegundos());
    }

    private static BossEvent.BossBarColor colorSegunRestante(long restanteSegundos) {
        if (restanteSegundos <= SEGUNDOS_ROJO) {
            return BossEvent.BossBarColor.RED;
        }
        return restanteSegundos <= SEGUNDOS_AMARILLO
                ? BossEvent.BossBarColor.YELLOW
                : BossEvent.BossBarColor.GREEN;
    }

    /**
     * Saca como con {@code /dungeon leave} a quien lleva {@code afkSegundos} sin moverse ni mirar.
     * Solo se vigila a los miembros vivos y conectados que están en la dimensión; en hardcore
     * pierden sus objetos sin ban (#16), salvo si ya se declaró la victoria.
     */
    private void vigilarAfk(MinecraftServer s, long ahoraSegundos) {
        Map<UUID, Partida> vigilados = new HashMap<>();
        for (Partida partida : registro.partidas()) {
            for (Miembro miembro : partida.miembros()) {
                ServerPlayer jugador = s.getPlayerList().getPlayer(miembro.id());
                if (miembro.estado() == EstadoMiembro.VIVO
                        && jugador != null
                        && DimensionDungeons.es(jugador.level())) {
                    vigilados.put(miembro.id(), partida);
                    afk.observar(miembro.id(), huella(jugador), ahoraSegundos);
                }
            }
        }
        afk.retener(vigilados.keySet());
        List<UUID> inactivos = new ArrayList<>();
        for (Partida partida : List.copyOf(vigilados.values())) {
            int limite = partida.config().partida().afkSegundos();
            afk.vencidos(ahoraSegundos, limite).stream()
                    .filter(u -> vigilados.get(u) == partida)
                    .forEach(inactivos::add);
        }
        inactivos.forEach(u -> expulsarPorAfk(s, vigilados.get(u), u));
    }

    private void expulsarPorAfk(MinecraftServer s, Partida partida, UUID id) {
        ServerPlayer jugador = s.getPlayerList().getPlayer(id);
        if (jugador == null || !partidas.salir(jugador, MotivoSalida.AFK)) {
            return;
        }
        jugador.sendSystemMessage(
                Component.literal("Saliste de la dungeon por inactividad")
                        .withStyle(ChatFormatting.RED));
        ChatFin.alGrupo(
                s,
                partida,
                partida.miembro(id).map(Miembro::nombre).orElse("Un jugador")
                        + " salió de la dungeon por inactividad",
                ChatFormatting.YELLOW);
        DolfinCreepers.LOGGER.info(
                "event=dungeon_afk_exit player={} dungeon={} match={}",
                jugador.getName().getString(),
                partida.dungeon().id(),
                partida.id());
    }

    private static ActividadAfk.Huella huella(ServerPlayer jugador) {
        return new ActividadAfk.Huella(
                jugador.getX(),
                jugador.getY(),
                jugador.getZ(),
                jugador.getYRot(),
                jugador.getXRot());
    }
}
