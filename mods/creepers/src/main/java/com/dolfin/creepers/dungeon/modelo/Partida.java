package com.dolfin.creepers.dungeon.modelo;

import com.dolfin.creepers.dungeon.config.DungeonsConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Una partida en curso o recién terminada. Guarda una copia inmutable de la config y de la
 * definición con las que empezó: una recarga no la cambia. Solo {@link RegistroDungeons} la
 * modifica.
 */
public final class Partida {
    private final UUID id = UUID.randomUUID();
    private final DefinicionDungeon dungeon;
    private final Dificultad dificultad;
    private final DungeonsConfig.Snapshot config;
    private final long inicioTick;
    private final long inicioMillis;
    private final int jugadoresIniciales;
    private final Map<UUID, Miembro> miembros = new LinkedHashMap<>();
    private ResultadoPartida resultado;
    private boolean victoriaDeclarada;

    Partida(
            DefinicionDungeon dungeon,
            Dificultad dificultad,
            DungeonsConfig.Snapshot config,
            List<RegistroDungeons.Candidato> candidatos,
            long inicioTick,
            long inicioMillis) {
        this.dungeon = dungeon;
        this.dificultad = dificultad;
        this.config = config;
        this.inicioTick = inicioTick;
        this.inicioMillis = inicioMillis;
        this.jugadoresIniciales = candidatos.size();
        int vidas = parametros().vidas();
        for (RegistroDungeons.Candidato c : candidatos) {
            miembros.put(
                    c.id(),
                    new Miembro(c.id(), c.nombre(), vidas == 0 ? Miembro.VIDAS_ILIMITADAS : vidas));
        }
    }

    public UUID id() {
        return id;
    }

    public DefinicionDungeon dungeon() {
        return dungeon;
    }

    public Dificultad dificultad() {
        return dificultad;
    }

    /** Config vigente al empezar; usar esta y no la actual para todo lo de la partida. */
    public DungeonsConfig.Snapshot config() {
        return config;
    }

    public DungeonsConfig.ParametrosDificultad parametros() {
        return config.dificultades().de(dificultad);
    }

    /** Tick del servidor al empezar. */
    public long inicioTick() {
        return inicioTick;
    }

    /** Hora real al empezar (epoch ms), para mostrar duración y guardar récords. */
    public long inicioMillis() {
        return inicioMillis;
    }

    /** Jugadores al empezar; fija el escalado aunque luego alguien muera o salga (#18). */
    public int jugadoresIniciales() {
        return jugadoresIniciales;
    }

    /** Límite de duración: el propio de la dungeon o {@code partida.limiteMinutos}. */
    public int limiteMinutos() {
        return dungeon.limiteMinutos() > 0
                ? dungeon.limiteMinutos()
                : config.partida().limiteMinutos();
    }

    public boolean activa() {
        return resultado == null;
    }

    /**
     * El jefe cayó y el grupo ganó, pero la partida sigue abierta durante la ventana de botín
     * (#17). Desde entonces no hay derrota por bajas ni castigos de hardcore.
     */
    public boolean victoriaDeclarada() {
        return victoriaDeclarada;
    }

    /** Resultado una vez terminada. */
    public Optional<ResultadoPartida> resultado() {
        return Optional.ofNullable(resultado);
    }

    public Optional<Miembro> miembro(UUID jugador) {
        return Optional.ofNullable(miembros.get(jugador));
    }

    /** Todos los miembros en orden de entrada, incluidos los que ya están fuera. */
    public Collection<Miembro> miembros() {
        return Collections.unmodifiableCollection(miembros.values());
    }

    /** Hay miembros que aún no están {@code FUERA}. */
    public boolean hayParticipantes() {
        return miembros.values().stream().anyMatch(m -> m.estado().participa());
    }

    /** Hay miembros vivos o desconectados que podrían volver vivos (#17 decide la derrota). */
    public boolean hayVivosOPorVolver() {
        return miembros.values().stream()
                .anyMatch(
                        m ->
                                m.estado() == EstadoMiembro.VIVO
                                        || (m.estado() == EstadoMiembro.DESCONECTADO
                                                && m.estadoAlDesconectar() == EstadoMiembro.VIVO));
    }

    /** Punto donde debe aparecer {@code jugador}: su último checkpoint o la entrada. */
    public Punto puntoDeReaparicion(UUID jugador) {
        return dungeon.puntoDeCheckpoint(
                miembro(jugador).map(Miembro::checkpoint).orElse(Miembro.SIN_CHECKPOINT));
    }

    void declararVictoria() {
        victoriaDeclarada = true;
    }

    void terminar(ResultadoPartida resultado) {
        this.resultado = resultado;
    }
}
