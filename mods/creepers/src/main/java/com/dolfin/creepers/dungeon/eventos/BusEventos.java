package com.dolfin.creepers.dungeon.eventos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Bus síncrono de {@link EventoDungeon} para el hilo del servidor. Los oyentes se llaman en orden
 * de suscripción. Un evento publicado desde un oyente se encola y se entrega cuando termina el
 * actual, así todos los oyentes ven los eventos en el mismo orden. Una excepción en un oyente se
 * informa y no impide que los demás reciban el evento.
 */
public final class BusEventos {
    private record Suscripcion<E extends EventoDungeon>(Class<E> tipo, Consumer<? super E> oyente) {
        void entregar(EventoDungeon evento) {
            if (tipo.isInstance(evento)) {
                oyente.accept(tipo.cast(evento));
            }
        }
    }

    private final List<Suscripcion<?>> suscripciones = new ArrayList<>();
    private final Deque<EventoDungeon> pendientes = new ArrayDeque<>();
    private final BiConsumer<EventoDungeon, RuntimeException> errores;
    private boolean despachando;

    /** {@code errores} recibe cada excepción lanzada por un oyente. */
    public BusEventos(BiConsumer<EventoDungeon, RuntimeException> errores) {
        this.errores = errores;
    }

    /**
     * Suscribe {@code oyente} a los eventos de tipo {@code tipo} (usa {@code EventoDungeon.class}
     * para recibirlos todos).
     */
    public <E extends EventoDungeon> void suscribir(Class<E> tipo, Consumer<? super E> oyente) {
        suscripciones.add(new Suscripcion<>(tipo, oyente));
    }

    /** Entrega {@code evento} a sus oyentes, o lo encola si ya se está entregando otro. */
    public void publicar(EventoDungeon evento) {
        pendientes.addLast(evento);
        if (despachando) {
            return;
        }
        despachando = true;
        try {
            while (!pendientes.isEmpty()) {
                entregar(pendientes.removeFirst());
            }
        } finally {
            despachando = false;
        }
    }

    private void entregar(EventoDungeon evento) {
        for (Suscripcion<?> suscripcion : List.copyOf(suscripciones)) {
            try {
                suscripcion.entregar(evento);
            } catch (RuntimeException e) {
                errores.accept(evento, e);
            }
        }
    }
}
