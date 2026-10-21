package com.dolfin.creepers.dungeon.plantilla;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * Reparte tareas largas (guardar y reiniciar plantillas) entre ticks con un presupuesto común de
 * bloques por tick. Sin hilos ni Minecraft: el servidor llama a {@link #tick()} al final de cada
 * tick y las pruebas lo llaman a mano.
 *
 * <p>Reglas:
 *
 * <ul>
 *   <li>Una tarea por clave (el id de la dungeon): no se guarda y reinicia la misma a la vez.
 *   <li>Las tareas avanzan en orden de llegada y comparten el presupuesto.
 *   <li>Un paso que no cabe en lo que queda del presupuesto espera al tick siguiente, salvo que sea
 *       el primero del tick: así un paso mayor que el presupuesto también avanza.
 *   <li>Un paso que devuelve {@code false} (por ejemplo, chunks aún cargando) no gasta presupuesto
 *       y se reintenta el tick siguiente; mientras, avanzan las demás tareas.
 *   <li>Si un paso lanza una excepción, la tarea termina con ese error y se descartan sus pasos.
 *   <li>Si un aviso de fin lanza una excepción, se pasa al manejador de errores y los demás avisos
 *       siguen: nada de esto llega al tick del servidor.
 * </ul>
 */
public final class EjecutorPorTicks {
    /** Una unidad de trabajo indivisible. */
    public interface Paso {
        /** Coste aproximado en bloques; 0 para pasos que solo comprueban algo. */
        int coste();

        /** Hace el trabajo; {@code false} si aún no puede y debe reintentarse el tick siguiente. */
        boolean ejecutar();

        /** Paso que siempre se completa al ejecutarse. */
        static Paso de(int coste, Runnable trabajo) {
            return new Paso() {
                @Override
                public int coste() {
                    return coste;
                }

                @Override
                public boolean ejecutar() {
                    trabajo.run();
                    return true;
                }
            };
        }

        /** Paso gratuito que espera hasta que {@code listo} sea cierto. */
        static Paso esperar(BooleanSupplier listo) {
            return new Paso() {
                @Override
                public int coste() {
                    return 0;
                }

                @Override
                public boolean ejecutar() {
                    return listo.getAsBoolean();
                }
            };
        }
    }

    /** Recibe el final de una tarea: vacío si acabó bien, o el error que la cortó. */
    @FunctionalInterface
    public interface AlFinalizar {
        void finalizada(Optional<RuntimeException> error);
    }

    private record Tarea(Deque<Paso> pasos, AlFinalizar alFinalizar) {}

    private final int presupuesto;
    private final BiConsumer<String, RuntimeException> errorEnAviso;
    private final Map<String, Tarea> tareas = new LinkedHashMap<>();

    /**
     * @param presupuesto bloques por tick entre todas las tareas
     * @param errorEnAviso recibe la clave y la excepción de un {@link AlFinalizar} que falla
     */
    public EjecutorPorTicks(int presupuesto, BiConsumer<String, RuntimeException> errorEnAviso) {
        if (presupuesto < 1) {
            throw new IllegalArgumentException("presupuesto no positivo");
        }
        this.presupuesto = presupuesto;
        this.errorEnAviso = errorEnAviso;
    }

    /**
     * Añade una tarea al final de la cola. Devuelve {@code false} sin encolar si ya hay una con la
     * misma clave. {@code alFinalizar} se llama una vez desde {@link #tick()}, con la tarea ya
     * fuera de la cola, salvo que antes se llame a {@link #cancelarTodo()}.
     */
    public boolean encolar(String clave, List<Paso> pasos, AlFinalizar alFinalizar) {
        if (tareas.containsKey(clave)) {
            return false;
        }
        tareas.put(clave, new Tarea(new ArrayDeque<>(pasos), alFinalizar));
        return true;
    }

    /** Hay una tarea en curso con esa clave. */
    public boolean ocupada(String clave) {
        return tareas.containsKey(clave);
    }

    /** No queda ninguna tarea. */
    public boolean vacio() {
        return tareas.isEmpty();
    }

    public Set<String> claves() {
        return Set.copyOf(tareas.keySet());
    }

    /** Avanza las tareas hasta agotar el presupuesto del tick y avisa a las que terminan. */
    public void tick() {
        int restante = presupuesto;
        boolean primerPaso = true;
        List<Map.Entry<String, Optional<RuntimeException>>> terminadas = new ArrayList<>();
        for (Map.Entry<String, Tarea> entrada : List.copyOf(tareas.entrySet())) {
            Deque<Paso> pasos = entrada.getValue().pasos();
            try {
                while (!pasos.isEmpty()) {
                    Paso paso = pasos.peek();
                    if (!primerPaso && paso.coste() > restante) {
                        break;
                    }
                    if (!paso.ejecutar()) {
                        break;
                    }
                    pasos.poll();
                    restante -= paso.coste();
                    primerPaso = false;
                }
                if (pasos.isEmpty()) {
                    terminadas.add(Map.entry(entrada.getKey(), Optional.empty()));
                }
            } catch (RuntimeException e) {
                terminadas.add(Map.entry(entrada.getKey(), Optional.of(e)));
            }
            if (!primerPaso && restante <= 0) {
                break;
            }
        }
        for (Map.Entry<String, Optional<RuntimeException>> fin : terminadas) {
            Tarea tarea = tareas.remove(fin.getKey());
            try {
                tarea.alFinalizar().finalizada(fin.getValue());
            } catch (RuntimeException e) {
                errorEnAviso.accept(fin.getKey(), e);
            }
        }
    }

    /**
     * Descarta todas las tareas sin avisar a nadie (el servidor se detiene: lo pendiente se
     * recupera al arrancar). Devuelve las claves descartadas.
     */
    public Set<String> cancelarTodo() {
        Set<String> claves = claves();
        tareas.clear();
        return claves;
    }
}
