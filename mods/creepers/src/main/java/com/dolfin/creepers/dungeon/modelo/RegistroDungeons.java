package com.dolfin.creepers.dungeon.modelo;

import com.dolfin.creepers.dungeon.config.DungeonsConfig;
import com.dolfin.creepers.dungeon.eventos.BusEventos;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.CheckpointAlcanzado;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.DungeonCambioEstado;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorDesconectado;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorEspectador;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorMurio;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorReconectado;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorSalio;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.PartidaIniciada;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.PartidaTerminada;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Estado central y puro (sin Minecraft) del sistema de dungeons: estado de cada dungeon, partidas
 * en curso y qué ocupa cada jugador. Garantiza una sola partida por dungeon y que un jugador esté
 * como mucho en una partida o en una fila. Cada cambio publica su {@code EventoDungeon} en el bus
 * después de aplicarse. Se usa solo desde el hilo del servidor.
 *
 * <p>Los tiempos son ticks del servidor que pasa quien llama, así las pruebas controlan el reloj.
 */
public final class RegistroDungeons {
    /** Ticks por segundo del servidor. */
    public static final int TICKS_POR_SEGUNDO = 20;

    /**
     * Jugador que quiere empezar una partida.
     *
     * @param modoDeJuegoValido está en supervivencia o aventura (no creativo ni espectador)
     */
    public record Candidato(UUID id, String nombre, boolean modoDeJuegoValido) {}

    /** Lo que ocupa a un jugador: una partida o un puesto en la fila (#14) de una dungeon. */
    public record Ocupacion(String dungeon, Tipo tipo) {
        public enum Tipo {
            PARTIDA,
            FILA
        }
    }

    /** Por qué no se puede empezar una partida. */
    public enum TipoRechazo {
        SISTEMA_DESACTIVADO,
        DUNGEON_DESCONOCIDA,
        DUNGEON_DESHABILITADA,
        DUNGEON_OCUPADA,
        DIFICULTAD_NO_PERMITIDA,
        TAMANIO_GRUPO,
        JUGADOR_REPETIDO,
        MODO_DE_JUEGO,
        JUGADOR_OCUPADO
    }

    /** Un motivo de rechazo; {@code jugador} es el nombre del culpable si lo hay. */
    public record Rechazo(TipoRechazo tipo, Optional<String> jugador) {
        static Rechazo deDungeon(TipoRechazo tipo) {
            return new Rechazo(tipo, Optional.empty());
        }

        static Rechazo deJugador(TipoRechazo tipo, String nombre) {
            return new Rechazo(tipo, Optional.of(nombre));
        }
    }

    /** Resultado de {@link #iniciarPartida}. */
    public sealed interface ResultadoInicio {
        record Iniciada(Partida partida) implements ResultadoInicio {}

        record Rechazada(List<Rechazo> rechazos) implements ResultadoInicio {}
    }

    /** Miembro que sigue participando, junto a su partida. */
    private record MiembroEnPartida(Partida partida, Miembro miembro) {}

    private final BusEventos bus;
    private DungeonsConfig.Snapshot config;
    private final Map<String, EstadoDungeon> estados = new LinkedHashMap<>();
    private final Map<String, Partida> partidas = new HashMap<>();
    private final Map<UUID, Ocupacion> ocupaciones = new HashMap<>();

    public RegistroDungeons(BusEventos bus, DungeonsConfig.Snapshot config) {
        this.bus = bus;
        aplicarConfig(config);
    }

    /**
     * Olvida partidas, filas y estados (servidor nuevo en el mismo proceso) y aplica {@code nueva}.
     * No publica eventos.
     */
    public void limpiar(DungeonsConfig.Snapshot nueva) {
        partidas.clear();
        ocupaciones.clear();
        estados.clear();
        aplicarConfig(nueva);
    }

    /**
     * Aplica una config recargada. Las dungeons nuevas quedan {@code LIBRE} (o {@code
     * DESHABILITADA} si {@code habilitada} es false); las que tienen partida o reinicio en curso no
     * cambian hasta terminar. Las partidas en curso conservan su propia copia de config.
     */
    public void aplicarConfig(DungeonsConfig.Snapshot nueva) {
        this.config = nueva;
        Set<String> definidas = new HashSet<>();
        for (DefinicionDungeon d : nueva.dungeons()) {
            definidas.add(d.id());
            EstadoDungeon actual = estados.get(d.id());
            if (actual == null) {
                estados.put(d.id(), estadoEnReposo(d));
            } else if (!ocupada(actual)) {
                cambiarEstado(d.id(), estadoEnReposo(d));
            }
        }
        estados.entrySet().removeIf(e -> !definidas.contains(e.getKey()) && !ocupada(e.getValue()));
    }

    /** Comprueba si se puede empezar una partida, sin cambiar nada. */
    public List<Rechazo> validarInicio(
            String dungeonId, Dificultad dificultad, List<Candidato> candidatos) {
        Optional<Rechazo> deDungeon = rechazoDeDungeon(dungeonId, dificultad, candidatos.size());
        if (deDungeon.isPresent()) {
            return List.of(deDungeon.get());
        }
        return rechazosDeJugadores(dungeonId, candidatos);
    }

    /**
     * Empieza una partida si {@link #validarInicio} no pone pegas: la dungeon pasa a {@code
     * EN_PARTIDA}, cada candidato queda ocupado por ella (dejando su fila) y se publica {@code
     * PartidaIniciada}. Los permisos, requisitos y cooldowns (#14) se validan antes de llamar.
     */
    public ResultadoInicio iniciarPartida(
            String dungeonId,
            Dificultad dificultad,
            List<Candidato> candidatos,
            long tick,
            long millis) {
        List<Rechazo> rechazos = validarInicio(dungeonId, dificultad, candidatos);
        if (!rechazos.isEmpty()) {
            return new ResultadoInicio.Rechazada(rechazos);
        }
        DefinicionDungeon definicion = config.dungeon(dungeonId).orElseThrow();
        Partida partida = new Partida(definicion, dificultad, config, candidatos, tick, millis);
        partidas.put(dungeonId, partida);
        for (Candidato c : candidatos) {
            ocupaciones.put(c.id(), new Ocupacion(dungeonId, Ocupacion.Tipo.PARTIDA));
        }
        cambiarEstado(dungeonId, EstadoDungeon.EN_PARTIDA);
        bus.publicar(new PartidaIniciada(partida));
        return new ResultadoInicio.Iniciada(partida);
    }

    /**
     * Termina la partida de {@code dungeonId}: los miembros que seguían pasan a {@code FUERA}
     * ({@code JugadorSalio} con {@link MotivoSalida#FIN_DE_PARTIDA}), se publica {@code
     * PartidaTerminada} y la dungeon pasa a {@code REINICIANDO} hasta {@link #finalizarReinicio}.
     */
    public Optional<Partida> terminarPartida(String dungeonId, ResultadoPartida resultado) {
        Partida partida = partidas.get(dungeonId);
        if (partida == null) {
            return Optional.empty();
        }
        for (Miembro m : partida.miembros()) {
            if (m.estado().participa()) {
                quitarDePartida(partida, m, MotivoSalida.FIN_DE_PARTIDA);
            }
        }
        partida.terminar(resultado);
        partidas.remove(dungeonId);
        bus.publicar(new PartidaTerminada(partida, resultado));
        cambiarEstado(dungeonId, EstadoDungeon.REINICIANDO);
        return Optional.of(partida);
    }

    /**
     * Pone una dungeon sin partida en {@code REINICIANDO} (arranque tras caída, {@code reset} de
     * admin). Falla si hay una partida en curso: hay que terminarla antes.
     */
    public boolean marcarReiniciando(String dungeonId) {
        if (partidas.containsKey(dungeonId) || !config.dungeon(dungeonId).isPresent()) {
            return false;
        }
        cambiarEstado(dungeonId, EstadoDungeon.REINICIANDO);
        return true;
    }

    /** Fin del reinicio (#19): la dungeon vuelve a {@code LIBRE} o {@code DESHABILITADA}. */
    public void finalizarReinicio(String dungeonId) {
        if (estados.get(dungeonId) != EstadoDungeon.REINICIANDO) {
            return;
        }
        Optional<DefinicionDungeon> definicion = config.dungeon(dungeonId);
        if (definicion.isEmpty()) {
            estados.remove(dungeonId);
            bus.publicar(
                    new DungeonCambioEstado(
                            dungeonId, EstadoDungeon.REINICIANDO, EstadoDungeon.DESHABILITADA));
            return;
        }
        cambiarEstado(dungeonId, estadoEnReposo(definicion.get()));
    }

    /** Saca a un miembro de su partida ({@code FUERA}) y publica {@code JugadorSalio}. */
    public boolean sacarMiembro(UUID jugador, MotivoSalida motivo) {
        return miembroParticipante(jugador)
                .map(
                        mp -> {
                            quitarDePartida(mp.partida(), mp.miembro(), motivo);
                            return true;
                        })
                .orElse(false);
    }

    /**
     * Marca a un miembro como {@code DESCONECTADO} con plazo de {@code reconexionSegundos} y
     * publica {@code JugadorDesconectado}.
     */
    public Optional<Partida> desconectar(UUID jugador, long tick) {
        return miembroParticipante(jugador)
                .filter(mp -> mp.miembro().estado() != EstadoMiembro.DESCONECTADO)
                .map(
                        mp -> {
                            int segundos = mp.partida().config().partida().reconexionSegundos();
                            mp.miembro().desconectar(tick + (long) segundos * TICKS_POR_SEGUNDO);
                            bus.publicar(new JugadorDesconectado(mp.partida(), jugador, segundos));
                            return mp.partida();
                        });
    }

    /**
     * Devuelve a un miembro desconectado su estado anterior y publica {@code JugadorReconectado}.
     */
    public Optional<Partida> reconectar(UUID jugador) {
        return miembroParticipante(jugador)
                .filter(mp -> mp.miembro().estado() == EstadoMiembro.DESCONECTADO)
                .map(
                        mp -> {
                            mp.miembro().reconectar();
                            bus.publicar(new JugadorReconectado(mp.partida(), jugador));
                            return mp.partida();
                        });
    }

    /**
     * Saca a los desconectados cuyo plazo venció. En hardcore con {@code
     * desconexionHardcoreEsMuerte} antes se publica {@code JugadorMurio} con causa {@code
     * DESCONEXION}, que #16 convierte en castigo.
     */
    public void vencerDesconexiones(long tick) {
        for (Partida partida : List.copyOf(partidas.values())) {
            for (Miembro m : List.copyOf(partida.miembros())) {
                if (m.estado() == EstadoMiembro.DESCONECTADO && m.plazoReconexionTick() <= tick) {
                    if (desconexionEsMuerte(partida)) {
                        registrarMuerte(m.id(), CausaMuerte.DESCONEXION);
                    }
                    quitarDePartida(partida, m, MotivoSalida.DESCONEXION);
                }
            }
        }
    }

    /**
     * Resta una vida (si son limitadas) y publica {@code JugadorMurio}. No cambia el estado del
     * miembro: #16 decide si pasa a espectador o sale.
     *
     * @return vidas restantes ({@link Miembro#VIDAS_ILIMITADAS} si no hay límite), o vacío si no
     *     participa en ninguna partida
     */
    public OptionalInt registrarMuerte(UUID jugador, CausaMuerte causa) {
        return miembroParticipante(jugador)
                .map(
                        mp -> {
                            int vidas = mp.miembro().perderVida();
                            bus.publicar(new JugadorMurio(mp.partida(), jugador, causa, vidas));
                            return OptionalInt.of(vidas);
                        })
                .orElse(OptionalInt.empty());
    }

    /** Pasa a un miembro vivo a {@code ESPECTADOR} y publica {@code JugadorEspectador}. */
    public boolean pasarAEspectador(UUID jugador) {
        return miembroParticipante(jugador)
                .filter(mp -> mp.miembro().estado() == EstadoMiembro.VIVO)
                .map(
                        mp -> {
                            mp.miembro().estado(EstadoMiembro.ESPECTADOR);
                            bus.publicar(new JugadorEspectador(mp.partida(), jugador));
                            return true;
                        })
                .orElse(false);
    }

    /** Guarda el checkpoint {@code indice} si es posterior al actual. */
    public boolean alcanzarCheckpoint(UUID jugador, int indice) {
        return miembroParticipante(jugador)
                .filter(mp -> indice > mp.miembro().checkpoint())
                .filter(mp -> indice < mp.partida().dungeon().checkpoints().size())
                .map(
                        mp -> {
                            mp.miembro().checkpoint(indice);
                            bus.publicar(new CheckpointAlcanzado(mp.partida(), jugador, indice));
                            return true;
                        })
                .orElse(false);
    }

    /** Ocupa a un jugador libre con un puesto en la fila de una dungeon (#14). */
    public boolean entrarEnFila(UUID jugador, String dungeonId) {
        if (ocupaciones.containsKey(jugador) || !estados.containsKey(dungeonId)) {
            return false;
        }
        ocupaciones.put(jugador, new Ocupacion(dungeonId, Ocupacion.Tipo.FILA));
        return true;
    }

    /** Libera el puesto en la fila de un jugador. */
    public boolean salirDeFila(UUID jugador) {
        Ocupacion ocupacion = ocupaciones.get(jugador);
        if (ocupacion == null || ocupacion.tipo() != Ocupacion.Tipo.FILA) {
            return false;
        }
        ocupaciones.remove(jugador);
        return true;
    }

    public DungeonsConfig.Snapshot config() {
        return config;
    }

    /** Estado de una dungeon definida (o con partida/reinicio en curso tras quitarla). */
    public Optional<EstadoDungeon> estado(String dungeonId) {
        return Optional.ofNullable(estados.get(dungeonId));
    }

    /** Estados de todas las dungeons conocidas, en orden de config. */
    public Map<String, EstadoDungeon> estados() {
        return Collections.unmodifiableMap(estados);
    }

    public Optional<Partida> partida(String dungeonId) {
        return Optional.ofNullable(partidas.get(dungeonId));
    }

    public Collection<Partida> partidas() {
        return List.copyOf(partidas.values());
    }

    /** Partida activa en la que {@code jugador} sigue participando (no está {@code FUERA}). */
    public Optional<Partida> partidaDe(UUID jugador) {
        return miembroParticipante(jugador).map(MiembroEnPartida::partida);
    }

    public Optional<Ocupacion> ocupacion(UUID jugador) {
        return Optional.ofNullable(ocupaciones.get(jugador));
    }

    private Optional<MiembroEnPartida> miembroParticipante(UUID jugador) {
        Ocupacion ocupacion = ocupaciones.get(jugador);
        if (ocupacion == null || ocupacion.tipo() != Ocupacion.Tipo.PARTIDA) {
            return Optional.empty();
        }
        Partida partida = partidas.get(ocupacion.dungeon());
        return Optional.ofNullable(partida)
                .flatMap(p -> p.miembro(jugador).map(m -> new MiembroEnPartida(p, m)))
                .filter(mp -> mp.miembro().estado().participa());
    }

    private void quitarDePartida(Partida partida, Miembro miembro, MotivoSalida motivo) {
        miembro.estado(EstadoMiembro.FUERA);
        ocupaciones.remove(miembro.id());
        bus.publicar(new JugadorSalio(partida, miembro.id(), motivo));
    }

    private Optional<Rechazo> rechazoDeDungeon(
            String dungeonId, Dificultad dificultad, int jugadores) {
        if (!config.general().activo()) {
            return Optional.of(Rechazo.deDungeon(TipoRechazo.SISTEMA_DESACTIVADO));
        }
        Optional<DefinicionDungeon> definicion = config.dungeon(dungeonId);
        if (definicion.isEmpty()) {
            return Optional.of(Rechazo.deDungeon(TipoRechazo.DUNGEON_DESCONOCIDA));
        }
        EstadoDungeon estado = estados.get(dungeonId);
        if (estado == EstadoDungeon.DESHABILITADA) {
            return Optional.of(Rechazo.deDungeon(TipoRechazo.DUNGEON_DESHABILITADA));
        }
        if (estado != EstadoDungeon.LIBRE) {
            return Optional.of(Rechazo.deDungeon(TipoRechazo.DUNGEON_OCUPADA));
        }
        DefinicionDungeon d = definicion.get();
        if (!d.dificultades().contains(dificultad)) {
            return Optional.of(Rechazo.deDungeon(TipoRechazo.DIFICULTAD_NO_PERMITIDA));
        }
        if (jugadores < d.minJugadores() || jugadores > d.maxJugadores()) {
            return Optional.of(Rechazo.deDungeon(TipoRechazo.TAMANIO_GRUPO));
        }
        return Optional.empty();
    }

    private List<Rechazo> rechazosDeJugadores(String dungeonId, List<Candidato> candidatos) {
        List<Rechazo> rechazos = new ArrayList<>();
        Set<UUID> vistos = new HashSet<>();
        for (Candidato c : candidatos) {
            if (!vistos.add(c.id())) {
                rechazos.add(Rechazo.deJugador(TipoRechazo.JUGADOR_REPETIDO, c.nombre()));
            } else if (!c.modoDeJuegoValido()) {
                rechazos.add(Rechazo.deJugador(TipoRechazo.MODO_DE_JUEGO, c.nombre()));
            } else if (ocupadoEnOtraCosa(c.id(), dungeonId)) {
                rechazos.add(Rechazo.deJugador(TipoRechazo.JUGADOR_OCUPADO, c.nombre()));
            }
        }
        return rechazos;
    }

    /** Ocupado por algo distinto de la fila de esta misma dungeon (de la que pasa a la partida). */
    private boolean ocupadoEnOtraCosa(UUID jugador, String dungeonId) {
        Ocupacion ocupacion = ocupaciones.get(jugador);
        return ocupacion != null
                && !(ocupacion.tipo() == Ocupacion.Tipo.FILA
                        && ocupacion.dungeon().equals(dungeonId));
    }

    private boolean desconexionEsMuerte(Partida partida) {
        return partida.dificultad() == Dificultad.HARDCORE
                && partida.config().partida().desconexionHardcoreEsMuerte();
    }

    private static boolean ocupada(EstadoDungeon estado) {
        return estado == EstadoDungeon.EN_PARTIDA || estado == EstadoDungeon.REINICIANDO;
    }

    private static EstadoDungeon estadoEnReposo(DefinicionDungeon definicion) {
        return definicion.habilitada() ? EstadoDungeon.LIBRE : EstadoDungeon.DESHABILITADA;
    }

    private void cambiarEstado(String dungeonId, EstadoDungeon nuevo) {
        EstadoDungeon anterior = estados.put(dungeonId, nuevo);
        if (anterior != null && anterior != nuevo) {
            bus.publicar(new DungeonCambioEstado(dungeonId, anterior, nuevo));
        }
    }
}
