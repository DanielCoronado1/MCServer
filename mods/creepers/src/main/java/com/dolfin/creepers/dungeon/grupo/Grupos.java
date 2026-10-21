package com.dolfin.creepers.dungeon.grupo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Grupos de jugadores de las dungeons (la {@code /party} propia, decisión P1 de la épica #10: sin
 * depender de Open Parties and Claims, que no emite eventos ni permite congelar el grupo durante
 * una partida). Puro, sin Minecraft, en el hilo del servidor; los tiempos son milisegundos que pasa
 * quien llama.
 *
 * <p>Reglas: crea el grupo quien invita y es su líder; solo el líder invita y expulsa; si el líder
 * sale, el liderazgo pasa al siguiente miembro conectado (o al siguiente); el grupo se disuelve
 * cuando queda vacío. Con algún miembro en una partida el grupo queda congelado: no se invita, no
 * se expulsa y nadie sale. Un miembro desconectado sigue {@link #DESCONEXION_MS} en el grupo (el
 * plazo no corre mientras el grupo está congelado). Los grupos solo viven en memoria.
 */
public final class Grupos {
    /** Plazo para aceptar una invitación. */
    public static final long INVITACION_MS = 60_000;

    /** Tiempo que un miembro desconectado conserva su sitio en el grupo. */
    public static final long DESCONEXION_MS = 5 * 60_000;

    /** Máximo de miembros de un grupo; cada dungeon impone además su propio rango. */
    public static final int MAX_MIEMBROS = 8;

    /** Lo que el grupo necesita saber del resto del sistema. */
    public interface Contexto {
        /** El jugador sigue participando en una partida. */
        boolean enPartida(UUID jugador);

        /** El jugador ocupa una partida o una fila. */
        boolean ocupado(UUID jugador);

        /** El jugador debe responder ahora a un ready check. */
        boolean enReadyCheck(UUID jugador);
    }

    /** Por qué falló una operación. */
    public enum Error {
        YA_EN_GRUPO,
        SIN_GRUPO,
        NO_ES_LIDER,
        A_UNO_MISMO,
        EN_PARTIDA,
        EN_READY_CHECK,
        GRUPO_LLENO,
        OBJETIVO_EN_GRUPO,
        OBJETIVO_OCUPADO,
        OBJETIVO_NO_ESTA,
        OCUPADO,
        SIN_INVITACION
    }

    /** Cómo dejó un jugador el grupo. */
    public enum MotivoSalida {
        VOLUNTARIO,
        EXPULSADO,
        DESCONEXION
    }

    /** Cambios que se notifican a los oyentes, ya aplicados; llevan copias de los miembros. */
    public sealed interface Evento {
        record Creado(UUID grupo, UUID lider) implements Evento {}

        record Invitado(UUID grupo, UUID invitador, UUID objetivo, long venceMs)
                implements Evento {}

        record InvitacionVencida(UUID grupo, UUID invitador, UUID objetivo) implements Evento {}

        record InvitacionRechazada(UUID grupo, UUID invitador, UUID objetivo) implements Evento {}

        record Unido(UUID grupo, UUID jugador, List<UUID> miembros) implements Evento {}

        /** {@code miembros} son los que quedan; {@code jugador} ya no está entre ellos. */
        record Salio(UUID grupo, UUID jugador, MotivoSalida motivo, List<UUID> miembros)
                implements Evento {}

        record NuevoLider(UUID grupo, UUID lider, List<UUID> miembros) implements Evento {}

        record Disuelto(UUID grupo) implements Evento {}
    }

    private record Invitacion(UUID grupo, UUID invitador, long venceMs) {}

    private final Contexto contexto;
    private final Map<UUID, Grupo> grupos = new HashMap<>();
    private final Map<UUID, Grupo> deJugador = new HashMap<>();
    private final Map<UUID, Invitacion> invitaciones = new HashMap<>();
    private final Map<UUID, Long> desconectados = new HashMap<>();
    private final List<Consumer<Evento>> oyentes = new ArrayList<>();

    public Grupos(Contexto contexto) {
        this.contexto = contexto;
    }

    /** Recibe cada {@link Evento}, en orden, después de aplicar el cambio. */
    public void suscribir(Consumer<Evento> oyente) {
        oyentes.add(oyente);
    }

    /** Crea un grupo de uno con {@code jugador} de líder. */
    public Optional<Error> crear(UUID jugador) {
        if (deJugador.containsKey(jugador)) {
            return Optional.of(Error.YA_EN_GRUPO);
        }
        crearGrupo(jugador);
        return Optional.empty();
    }

    /**
     * El líder invita a {@code objetivo}; si {@code lider} no tenía grupo se crea uno. Una
     * invitación nueva sustituye a la anterior del mismo invitado.
     */
    public Optional<Error> invitar(UUID lider, UUID objetivo, long ahoraMs) {
        Optional<Error> error = validarInvitacion(lider, objetivo);
        if (error.isPresent()) {
            return error;
        }
        Grupo grupo = deJugador.containsKey(lider) ? deJugador.get(lider) : crearGrupo(lider);
        long vence = ahoraMs + INVITACION_MS;
        invitaciones.put(objetivo, new Invitacion(grupo.id(), lider, vence));
        publicar(new Evento.Invitado(grupo.id(), lider, objetivo, vence));
        return Optional.empty();
    }

    /** El invitado entra en el grupo que lo invitó, si la invitación no venció. */
    public Optional<Error> aceptar(UUID jugador, long ahoraMs) {
        Invitacion invitacion = invitacionVigente(jugador, ahoraMs);
        if (invitacion == null) {
            return Optional.of(Error.SIN_INVITACION);
        }
        Grupo grupo = grupos.get(invitacion.grupo());
        Optional<Error> error = validarAceptacion(jugador, grupo);
        if (error.isPresent()) {
            return error;
        }
        invitaciones.remove(jugador);
        grupo.anadir(jugador);
        deJugador.put(jugador, grupo);
        publicar(new Evento.Unido(grupo.id(), jugador, List.copyOf(grupo.miembros())));
        return Optional.empty();
    }

    /** El invitado rechaza su invitación pendiente. */
    public Optional<Error> rechazar(UUID jugador, long ahoraMs) {
        Invitacion invitacion = invitacionVigente(jugador, ahoraMs);
        if (invitacion == null) {
            return Optional.of(Error.SIN_INVITACION);
        }
        invitaciones.remove(jugador);
        publicar(
                new Evento.InvitacionRechazada(
                        invitacion.grupo(), invitacion.invitador(), jugador));
        return Optional.empty();
    }

    /** El líder expulsa a otro miembro. */
    public Optional<Error> expulsar(UUID lider, UUID objetivo) {
        Grupo grupo = deJugador.get(lider);
        if (grupo == null) {
            return Optional.of(Error.SIN_GRUPO);
        }
        if (!grupo.lider().equals(lider)) {
            return Optional.of(Error.NO_ES_LIDER);
        }
        if (lider.equals(objetivo)) {
            return Optional.of(Error.A_UNO_MISMO);
        }
        if (!grupo.contiene(objetivo)) {
            return Optional.of(Error.OBJETIVO_NO_ESTA);
        }
        if (congelado(grupo)) {
            return Optional.of(Error.EN_PARTIDA);
        }
        quitar(grupo, objetivo, MotivoSalida.EXPULSADO);
        return Optional.empty();
    }

    /** Un miembro deja el grupo; si era el líder, el liderazgo pasa al siguiente. */
    public Optional<Error> salir(UUID jugador) {
        Grupo grupo = deJugador.get(jugador);
        if (grupo == null) {
            return Optional.of(Error.SIN_GRUPO);
        }
        if (congelado(grupo)) {
            return Optional.of(Error.EN_PARTIDA);
        }
        quitar(grupo, jugador, MotivoSalida.VOLUNTARIO);
        return Optional.empty();
    }

    /** Anota que el jugador se desconectó; empieza a correr su plazo en el grupo. */
    public void desconectar(UUID jugador, long ahoraMs) {
        if (deJugador.containsKey(jugador)) {
            desconectados.putIfAbsent(jugador, ahoraMs);
        }
    }

    /** El jugador volvió: conserva su sitio. */
    public void conectar(UUID jugador) {
        desconectados.remove(jugador);
    }

    /**
     * Expira invitaciones y echa del grupo a quien lleva {@link #DESCONEXION_MS} desconectado. Si
     * el grupo está congelado por una partida, el plazo se reinicia: cuenta desde que termine.
     */
    public void vencer(long ahoraMs) {
        for (var entrada : List.copyOf(invitaciones.entrySet())) {
            Invitacion i = entrada.getValue();
            if (i.venceMs() <= ahoraMs) {
                invitaciones.remove(entrada.getKey());
                publicar(new Evento.InvitacionVencida(i.grupo(), i.invitador(), entrada.getKey()));
            }
        }
        for (var entrada : List.copyOf(desconectados.entrySet())) {
            if (ahoraMs - entrada.getValue() >= DESCONEXION_MS) {
                vencerDesconectado(entrada.getKey(), ahoraMs);
            }
        }
    }

    /** Olvida grupos, invitaciones y desconexiones (servidor nuevo en el mismo proceso). */
    public void limpiar() {
        grupos.clear();
        deJugador.clear();
        invitaciones.clear();
        desconectados.clear();
    }

    public Optional<Grupo> de(UUID jugador) {
        return Optional.ofNullable(deJugador.get(jugador));
    }

    /** Miembros del grupo del jugador, o solo él si no tiene grupo (un grupo de uno). */
    public List<UUID> miembrosDe(UUID jugador) {
        Grupo grupo = deJugador.get(jugador);
        return grupo == null ? List.of(jugador) : List.copyOf(grupo.miembros());
    }

    /** El jugador no tiene grupo o es su líder: puede pedir entrar a una dungeon. */
    public boolean puedeDirigir(UUID jugador) {
        Grupo grupo = deJugador.get(jugador);
        return grupo == null || grupo.lider().equals(jugador);
    }

    public boolean estaDesconectado(UUID jugador) {
        return desconectados.containsKey(jugador);
    }

    /** Algún miembro está en una partida: el grupo no admite cambios. */
    public boolean congelado(Grupo grupo) {
        return grupo.miembros().stream().anyMatch(contexto::enPartida);
    }

    private Optional<Error> validarInvitacion(UUID lider, UUID objetivo) {
        Grupo grupo = deJugador.get(lider);
        if (lider.equals(objetivo)) {
            return Optional.of(Error.A_UNO_MISMO);
        }
        if (grupo != null) {
            if (!grupo.lider().equals(lider)) {
                return Optional.of(Error.NO_ES_LIDER);
            }
            Optional<Error> bloqueo = bloqueoDeGrupo(grupo);
            if (bloqueo.isPresent()) {
                return bloqueo;
            }
            if (grupo.miembros().size() >= MAX_MIEMBROS) {
                return Optional.of(Error.GRUPO_LLENO);
            }
        } else if (contexto.enPartida(lider)) {
            return Optional.of(Error.EN_PARTIDA);
        }
        if (deJugador.containsKey(objetivo)) {
            return Optional.of(Error.OBJETIVO_EN_GRUPO);
        }
        return contexto.ocupado(objetivo) ? Optional.of(Error.OBJETIVO_OCUPADO) : Optional.empty();
    }

    private Optional<Error> validarAceptacion(UUID jugador, Grupo grupo) {
        if (grupo == null) {
            return Optional.of(Error.SIN_INVITACION);
        }
        if (deJugador.containsKey(jugador)) {
            return Optional.of(Error.YA_EN_GRUPO);
        }
        if (contexto.ocupado(jugador)) {
            return Optional.of(Error.OCUPADO);
        }
        if (grupo.miembros().size() >= MAX_MIEMBROS) {
            return Optional.of(Error.GRUPO_LLENO);
        }
        return bloqueoDeGrupo(grupo);
    }

    /** Un grupo en partida o en ready check no admite invitaciones ni nuevos miembros. */
    private Optional<Error> bloqueoDeGrupo(Grupo grupo) {
        if (congelado(grupo)) {
            return Optional.of(Error.EN_PARTIDA);
        }
        boolean enReadyCheck = grupo.miembros().stream().anyMatch(contexto::enReadyCheck);
        return enReadyCheck ? Optional.of(Error.EN_READY_CHECK) : Optional.empty();
    }

    private Invitacion invitacionVigente(UUID jugador, long ahoraMs) {
        Invitacion invitacion = invitaciones.get(jugador);
        return invitacion != null && invitacion.venceMs() > ahoraMs ? invitacion : null;
    }

    private void vencerDesconectado(UUID jugador, long ahoraMs) {
        Grupo grupo = deJugador.get(jugador);
        if (grupo == null) {
            desconectados.remove(jugador);
        } else if (congelado(grupo)) {
            desconectados.put(jugador, ahoraMs);
        } else {
            quitar(grupo, jugador, MotivoSalida.DESCONEXION);
        }
    }

    private Grupo crearGrupo(UUID lider) {
        Grupo grupo = new Grupo(lider);
        grupos.put(grupo.id(), grupo);
        deJugador.put(lider, grupo);
        publicar(new Evento.Creado(grupo.id(), lider));
        return grupo;
    }

    /** Saca al jugador, traspasa el liderazgo si hace falta y disuelve el grupo vacío. */
    private void quitar(Grupo grupo, UUID jugador, MotivoSalida motivo) {
        boolean eraLider = grupo.lider().equals(jugador);
        grupo.quitar(jugador);
        deJugador.remove(jugador);
        desconectados.remove(jugador);
        publicar(new Evento.Salio(grupo.id(), jugador, motivo, List.copyOf(grupo.miembros())));
        if (grupo.miembros().isEmpty()) {
            grupos.remove(grupo.id());
            invitaciones.values().removeIf(i -> i.grupo().equals(grupo.id()));
            publicar(new Evento.Disuelto(grupo.id()));
        } else if (eraLider) {
            grupo.lider(siguienteLider(grupo));
            publicar(
                    new Evento.NuevoLider(
                            grupo.id(), grupo.lider(), List.copyOf(grupo.miembros())));
        }
    }

    /** El primer miembro conectado en orden de entrada; si no hay ninguno, el primero. */
    private UUID siguienteLider(Grupo grupo) {
        return grupo.miembros().stream()
                .filter(m -> !desconectados.containsKey(m))
                .findFirst()
                .orElse(grupo.miembros().get(0));
    }

    private void publicar(Evento evento) {
        for (Consumer<Evento> oyente : List.copyOf(oyentes)) {
            oyente.accept(evento);
        }
    }
}
