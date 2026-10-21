package com.dolfin.creepers.dungeon.eventos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dolfin.creepers.dungeon.eventos.EventoDungeon.DungeonCambioEstado;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.JugadorRestaurado;
import com.dolfin.creepers.dungeon.modelo.EstadoDungeon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class BusEventosTest {
    final List<String> registro = new ArrayList<>();
    final List<RuntimeException> errores = new ArrayList<>();
    final BusEventos bus = new BusEventos((e, error) -> errores.add(error));

    static final DungeonCambioEstado CAMBIO =
            new DungeonCambioEstado("a", EstadoDungeon.LIBRE, EstadoDungeon.EN_PARTIDA);
    static final JugadorRestaurado RESTAURADO = new JugadorRestaurado(UUID.randomUUID(), true);

    @Test
    void entregaPorTipoEnOrdenDeSuscripcion() {
        bus.suscribir(DungeonCambioEstado.class, e -> registro.add("cambio1"));
        bus.suscribir(EventoDungeon.class, e -> registro.add("todos"));
        bus.suscribir(JugadorRestaurado.class, e -> registro.add("restaurado"));
        bus.publicar(CAMBIO);
        assertEquals(List.of("cambio1", "todos"), registro);
    }

    @Test
    void losEventosAnidadosSeEntreganDespuesDelActual() {
        bus.suscribir(
                DungeonCambioEstado.class,
                e -> {
                    registro.add("cambio-a");
                    bus.publicar(RESTAURADO);
                });
        bus.suscribir(DungeonCambioEstado.class, e -> registro.add("cambio-b"));
        bus.suscribir(JugadorRestaurado.class, e -> registro.add("restaurado"));
        bus.publicar(CAMBIO);
        assertEquals(List.of("cambio-a", "cambio-b", "restaurado"), registro);
    }

    @Test
    void unOyenteQueFallaNoBloqueaALosDemas() {
        bus.suscribir(
                DungeonCambioEstado.class,
                e -> {
                    throw new IllegalStateException("roto");
                });
        bus.suscribir(DungeonCambioEstado.class, e -> registro.add("sigue"));
        bus.publicar(CAMBIO);
        bus.publicar(CAMBIO);
        assertEquals(List.of("sigue", "sigue"), registro);
        assertEquals(2, errores.size());
    }
}
