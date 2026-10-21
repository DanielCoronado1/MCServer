package com.dolfin.creepers.dungeon.fila;

import com.dolfin.creepers.dungeon.modelo.Dificultad;
import com.dolfin.creepers.dungeon.modelo.EstadoDungeon;
import com.dolfin.creepers.dungeon.modelo.RegistroDungeons;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Máquina de estados pura de las filas de espera y sus ready checks (#14). Cada dungeon tiene una
 * fila de {@link Entrada}s; cuando la dungeon está {@code LIBRE} el primer grupo recibe un ready
 * check y tiene {@code readyCheckSegundos} para que todos sus miembros respondan. Si todos están
 * listos la entrada se completa ({@link Efecto.Completa}) y quien llama empieza la partida; si
 * alguien rechaza, no responde a tiempo o el grupo entero se desconecta, el grupo sale y le toca al
 * siguiente.
 *
 * <p>No toca el juego: cada operación devuelve los {@link Efecto}s que quien llama debe aplicar
 * (avisos, liberar ocupaciones, empezar la partida). Los miembros de una entrada se consultan al
 * {@link Entorno} cada vez, así que si el grupo cambia en la fila la entrada lo refleja. Tiempos en
 * ticks del servidor.
 */
public final class Filas {
    /** Lo que las filas necesitan saber del resto del sistema. */
    public interface Entorno {
        /** Estado de la dungeon; vacío si ya no existe. */
        Optional<EstadoDungeon> estado(String dungeon);

        /** Miembros actuales del grupo del jugador (solo él si no tiene grupo). */
        List<UUID> miembros(UUID jugador);

        boolean enLinea(UUID jugador);

        /** Plazo del ready check. */
        int readyCheckSegundos();
    }

    /**
     * Un grupo esperando. {@code solicitante} es quien pidió entrar y da identidad a la entrada;
     * sus compañeros son los miembros actuales de su grupo.
     */
    public record Entrada(UUID solicitante, Dificultad dificultad) {}

    /** Por qué un grupo sale de la fila. */
    public enum Motivo {
        RECHAZO,
        SIN_RESPUESTA,
        SIN_CONEXION,
        CANCELADA,
        CERRADA
    }

    /** Consecuencias de una operación, en orden. */
    public sealed interface Efecto {
        /** Empieza el ready check; {@code pendientes} aún no han respondido. */
        record ReadyCheckIniciado(
                String dungeon,
                Entrada entrada,
                long venceTick,
                Set<UUID> miembros,
                Set<UUID> pendientes)
                implements Efecto {}

        /** Un miembro dijo que está listo; faltan {@code faltan}. */
        record MiembroListo(
                String dungeon, Entrada entrada, UUID jugador, int faltan, Set<UUID> miembros)
                implements Efecto {}

        /** El grupo avanzó al puesto {@code posicion} de {@code total}. */
        record PosicionCambiada(
                String dungeon, Entrada entrada, int posicion, int total, Set<UUID> miembros)
                implements Efecto {}

        /** El grupo salió de la fila; {@code culpable} es quien rechazó o canceló, si lo hay. */
        record Retirada(
                String dungeon,
                Entrada entrada,
                Motivo motivo,
                Optional<UUID> culpable,
                Set<UUID> miembros)
                implements Efecto {}

        /** Todos listos: el grupo sale de la fila y debe empezar la partida. */
        record Completa(String dungeon, Entrada entrada, Set<UUID> miembros) implements Efecto {}
    }

    private static final class ReadyCheck {
        final Entrada entrada;
        final long vence;
        final Set<UUID> listos = new HashSet<>();

        ReadyCheck(Entrada entrada, long vence) {
            this.entrada = entrada;
            this.vence = vence;
        }
    }

    private static final class Cola {
        final List<Entrada> espera = new ArrayList<>();
        ReadyCheck check;
    }

    private final Entorno entorno;
    private final Map<String, Cola> colas = new LinkedHashMap<>();

    public Filas(Entorno entorno) {
        this.entorno = entorno;
    }

    /**
     * Pone a un grupo al final de la fila de la dungeon y avanza lo que se pueda (si estaba libre y
     * vacía, el ready check empieza ya).
     *
     * @param solicitanteListo cuenta como listo el solicitante; se usa cuando entra directo en una
     *     dungeon libre, para que un jugador solo no tenga que confirmar lo que acaba de pedir
     */
    public List<Efecto> unirse(
            String dungeon, Entrada entrada, boolean solicitanteListo, long tick) {
        return aplicar(
                tick,
                solicitanteListo ? entrada.solicitante() : null,
                efectos -> colas.computeIfAbsent(dungeon, d -> new Cola()).espera.add(entrada));
    }

    /** Un miembro responde al ready check; rechazar saca a todo el grupo. */
    public List<Efecto> responder(UUID jugador, boolean listo, long tick) {
        return aplicar(tick, null, efectos -> registrarRespuesta(jugador, listo, efectos));
    }

    /** El grupo del jugador (como solicitante o compañero) sale de la fila. */
    public List<Efecto> salir(UUID jugador, long tick) {
        return aplicar(tick, null, efectos -> retirarDe(jugador, efectos));
    }

    /** Avanza plazos, desconexiones y ready checks; llamar al menos una vez por segundo. */
    public List<Efecto> tick(long tick) {
        return aplicar(tick, null, efectos -> {});
    }

    /** Olvida todas las filas y ready checks (servidor nuevo en el mismo proceso). */
    public void limpiar() {
        colas.clear();
    }

    /** Dungeon y entrada en la que está el jugador, ya sea solicitante o compañero. */
    public Optional<Ubicacion> ubicacion(UUID jugador) {
        for (var entrada : colas.entrySet()) {
            Optional<Entrada> propia = entradaDe(entrada.getValue(), jugador);
            if (propia.isPresent()) {
                return Optional.of(new Ubicacion(entrada.getKey(), propia.get()));
            }
        }
        return Optional.empty();
    }

    /** Dónde espera un grupo. */
    public record Ubicacion(String dungeon, Entrada entrada) {}

    /** Puesto del jugador en su fila (1 = el siguiente). */
    public OptionalInt posicion(UUID jugador) {
        return ubicacion(jugador)
                .map(u -> OptionalInt.of(colas.get(u.dungeon()).espera.indexOf(u.entrada()) + 1))
                .orElse(OptionalInt.empty());
    }

    /** Grupos esperando en la dungeon. */
    public int grupos(String dungeon) {
        Cola cola = colas.get(dungeon);
        return cola == null ? 0 : cola.espera.size();
    }

    /** Hay un ready check en curso que incluye al jugador, lo haya respondido o no. */
    public boolean tieneReadyCheck(UUID jugador) {
        return colas.values().stream()
                .anyMatch(
                        c ->
                                c.check != null
                                        && entorno.miembros(c.check.entrada.solicitante())
                                                .contains(jugador));
    }

    private Optional<Entrada> entradaDe(Cola cola, UUID jugador) {
        return cola.espera.stream()
                .filter(
                        e ->
                                e.solicitante().equals(jugador)
                                        || entorno.miembros(e.solicitante()).contains(jugador))
                .findFirst();
    }

    /**
     * Ejecuta una mutación de las filas y deja el estado asentado: purga grupos desconectados,
     * cierra dungeons deshabilitadas, resuelve el ready check en curso y empieza el del siguiente
     * grupo si la dungeon está libre. Los puestos que avanzan se avisan una sola vez al final.
     */
    private List<Efecto> aplicar(
            long tick, UUID solicitanteListo, Consumer<List<Efecto>> mutacion) {
        Map<String, Map<Entrada, Integer>> antes = posiciones();
        List<Efecto> efectos = new ArrayList<>();
        mutacion.accept(efectos);
        for (var entrada : List.copyOf(colas.entrySet())) {
            procesarCola(entrada.getKey(), entrada.getValue(), tick, efectos, solicitanteListo);
        }
        avisarAvances(antes, efectos);
        return efectos;
    }

    private void registrarRespuesta(UUID jugador, boolean listo, List<Efecto> efectos) {
        for (var entrada : colas.entrySet()) {
            ReadyCheck check = entrada.getValue().check;
            if (check != null && entorno.miembros(check.entrada.solicitante()).contains(jugador)) {
                if (listo) {
                    marcarListo(entrada.getKey(), check, jugador, efectos);
                } else {
                    retirar(entrada.getKey(), check.entrada, Motivo.RECHAZO, jugador, efectos);
                }
                return;
            }
        }
    }

    private void retirarDe(UUID jugador, List<Efecto> efectos) {
        for (var entrada : colas.entrySet()) {
            Optional<Entrada> propia = entradaDe(entrada.getValue(), jugador);
            if (propia.isPresent()) {
                retirar(entrada.getKey(), propia.get(), Motivo.CANCELADA, jugador, efectos);
                return;
            }
        }
    }

    private void procesarCola(
            String dungeon, Cola cola, long tick, List<Efecto> efectos, UUID solicitanteListo) {
        Optional<EstadoDungeon> estado = entorno.estado(dungeon);
        if (estado.isEmpty() || estado.get() == EstadoDungeon.DESHABILITADA) {
            for (Entrada e : List.copyOf(cola.espera)) {
                retirar(dungeon, e, Motivo.CERRADA, null, efectos);
            }
            return;
        }
        for (Entrada e : List.copyOf(cola.espera)) {
            if (todosDesconectados(e)) {
                retirar(dungeon, e, Motivo.SIN_CONEXION, null, efectos);
            }
        }
        boolean cambio = true;
        while (cambio && !cola.espera.isEmpty()) {
            cambio =
                    cola.check == null
                            ? iniciarCheck(
                                    dungeon, cola, estado.get(), tick, efectos, solicitanteListo)
                            : resolverCheck(dungeon, cola, estado.get(), tick, efectos);
        }
    }

    /** Empieza el ready check del primer grupo si la dungeon está libre. */
    private boolean iniciarCheck(
            String dungeon,
            Cola cola,
            EstadoDungeon estado,
            long tick,
            List<Efecto> efectos,
            UUID solicitanteListo) {
        if (estado != EstadoDungeon.LIBRE) {
            return false;
        }
        Entrada primera = cola.espera.get(0);
        long vence =
                tick + (long) entorno.readyCheckSegundos() * RegistroDungeons.TICKS_POR_SEGUNDO;
        cola.check = new ReadyCheck(primera, vence);
        boolean preListo = primera.solicitante().equals(solicitanteListo);
        if (preListo) {
            cola.check.listos.add(solicitanteListo);
        }
        Set<UUID> miembros = new LinkedHashSet<>(entorno.miembros(primera.solicitante()));
        Set<UUID> pendientes = new LinkedHashSet<>(miembros);
        pendientes.removeAll(cola.check.listos);
        efectos.add(new Efecto.ReadyCheckIniciado(dungeon, primera, vence, miembros, pendientes));
        return true;
    }

    /** Completa, expira o cancela el ready check en curso; devuelve si cambió algo. */
    private boolean resolverCheck(
            String dungeon, Cola cola, EstadoDungeon estado, long tick, List<Efecto> efectos) {
        ReadyCheck check = cola.check;
        if (estado != EstadoDungeon.LIBRE) {
            cola.check = null;
            return false;
        }
        Set<UUID> miembros = new LinkedHashSet<>(entorno.miembros(check.entrada.solicitante()));
        if (check.listos.containsAll(miembros)) {
            cola.check = null;
            cola.espera.remove(check.entrada);
            efectos.add(new Efecto.Completa(dungeon, check.entrada, miembros));
            // La partida aún no ha empezado: el siguiente grupo espera a la próxima llamada.
            return false;
        }
        if (tick >= check.vence) {
            retirar(dungeon, check.entrada, Motivo.SIN_RESPUESTA, null, efectos);
            return true;
        }
        return false;
    }

    private void marcarListo(String dungeon, ReadyCheck check, UUID jugador, List<Efecto> efectos) {
        if (!check.listos.add(jugador)) {
            return;
        }
        Set<UUID> miembros = new LinkedHashSet<>(entorno.miembros(check.entrada.solicitante()));
        int faltan = (int) miembros.stream().filter(m -> !check.listos.contains(m)).count();
        efectos.add(new Efecto.MiembroListo(dungeon, check.entrada, jugador, faltan, miembros));
    }

    private void retirar(
            String dungeon, Entrada entrada, Motivo motivo, UUID culpable, List<Efecto> efectos) {
        Cola cola = colas.get(dungeon);
        if (cola == null || !cola.espera.remove(entrada)) {
            return;
        }
        if (cola.check != null && cola.check.entrada.equals(entrada)) {
            cola.check = null;
        }
        Set<UUID> miembros = new LinkedHashSet<>(entorno.miembros(entrada.solicitante()));
        miembros.add(entrada.solicitante());
        efectos.add(
                new Efecto.Retirada(
                        dungeon, entrada, motivo, Optional.ofNullable(culpable), miembros));
    }

    private boolean todosDesconectados(Entrada entrada) {
        return entorno.miembros(entrada.solicitante()).stream().noneMatch(entorno::enLinea);
    }

    private Map<String, Map<Entrada, Integer>> posiciones() {
        Map<String, Map<Entrada, Integer>> mapa = new LinkedHashMap<>();
        colas.forEach(
                (dungeon, cola) -> {
                    Map<Entrada, Integer> puestos = new LinkedHashMap<>();
                    for (int i = 0; i < cola.espera.size(); i++) {
                        puestos.put(cola.espera.get(i), i + 1);
                    }
                    mapa.put(dungeon, puestos);
                });
        return mapa;
    }

    /** Avisa a quien ya estaba esperando y ahora tiene un puesto menor. */
    private void avisarAvances(Map<String, Map<Entrada, Integer>> antes, List<Efecto> efectos) {
        antes.forEach(
                (dungeon, puestosAntes) -> {
                    List<Entrada> espera = colas.get(dungeon).espera;
                    for (int i = 0; i < espera.size(); i++) {
                        Integer puestoAntes = puestosAntes.get(espera.get(i));
                        if (puestoAntes != null && puestoAntes > i + 1) {
                            Entrada entrada = espera.get(i);
                            Set<UUID> miembros =
                                    new LinkedHashSet<>(entorno.miembros(entrada.solicitante()));
                            efectos.add(
                                    new Efecto.PosicionCambiada(
                                            dungeon, entrada, i + 1, espera.size(), miembros));
                        }
                    }
                });
    }
}
