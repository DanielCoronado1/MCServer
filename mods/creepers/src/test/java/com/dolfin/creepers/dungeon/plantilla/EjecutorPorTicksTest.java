package com.dolfin.creepers.dungeon.plantilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dolfin.creepers.dungeon.plantilla.EjecutorPorTicks.Paso;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

class EjecutorPorTicksTest {
    private final List<String> hechos = new ArrayList<>();
    private final List<String> finales = new ArrayList<>();

    private final List<String> fallosDeAviso = new ArrayList<>();

    private EjecutorPorTicks nuevo(int presupuesto) {
        return new EjecutorPorTicks(
                presupuesto, (clave, e) -> fallosDeAviso.add(clave + ":" + e.getMessage()));
    }

    private Paso paso(String nombre, int coste) {
        return Paso.de(coste, () -> hechos.add(nombre));
    }

    private EjecutorPorTicks.AlFinalizar fin(String clave) {
        return error -> finales.add(clave + error.map(e -> ":" + e.getMessage()).orElse(""));
    }

    @Test
    void reparteLosPasosSegunElPresupuesto() {
        EjecutorPorTicks ejecutor = nuevo(100);
        ejecutor.encolar(
                "a", List.of(paso("1", 60), paso("2", 40), paso("3", 50), paso("4", 50)), fin("a"));

        ejecutor.tick();
        assertEquals(List.of("1", "2"), hechos);
        ejecutor.tick();
        assertEquals(List.of("1", "2", "3", "4"), hechos);
        assertEquals(List.of("a"), finales);
        assertFalse(ejecutor.ocupada("a"));
    }

    @Test
    void unPasoMayorQueElPresupuestoAvanzaSoloEnSuTick() {
        EjecutorPorTicks ejecutor = nuevo(100);
        ejecutor.encolar("a", List.of(paso("grande", 500), paso("chico", 1)), fin("a"));

        ejecutor.tick();
        assertEquals(List.of("grande"), hechos);
        ejecutor.tick();
        assertEquals(List.of("grande", "chico"), hechos);
    }

    @Test
    void lasTareasCompartenElPresupuestoEnOrdenDeLlegada() {
        EjecutorPorTicks ejecutor = nuevo(100);
        ejecutor.encolar("a", List.of(paso("a1", 70), paso("a2", 70)), fin("a"));
        ejecutor.encolar("b", List.of(paso("b1", 30)), fin("b"));

        ejecutor.tick();
        assertEquals(List.of("a1", "b1"), hechos);
        assertEquals(List.of("b"), finales);
        ejecutor.tick();
        assertEquals(List.of("a1", "b1", "a2"), hechos);
        assertEquals(List.of("b", "a"), finales);
    }

    @Test
    void unPasoQueEsperaNoBloqueaALasDemas() {
        EjecutorPorTicks ejecutor = nuevo(100);
        AtomicBoolean listo = new AtomicBoolean(false);
        ejecutor.encolar("a", List.of(Paso.esperar(listo::get), paso("a1", 10)), fin("a"));
        ejecutor.encolar("b", List.of(paso("b1", 10)), fin("b"));

        ejecutor.tick();
        ejecutor.tick();
        assertEquals(List.of("b1"), hechos);
        assertTrue(ejecutor.ocupada("a"));

        listo.set(true);
        ejecutor.tick();
        assertEquals(List.of("b1", "a1"), hechos);
        assertEquals(List.of("b", "a"), finales);
    }

    @Test
    void unaClaveSoloAdmiteUnaTarea() {
        EjecutorPorTicks ejecutor = nuevo(100);
        assertTrue(ejecutor.encolar("a", List.of(paso("1", 1)), fin("a")));
        assertFalse(ejecutor.encolar("a", List.of(paso("2", 1)), fin("a")));
        ejecutor.tick();
        assertEquals(List.of("1"), hechos);
        assertTrue(ejecutor.encolar("a", List.of(paso("3", 1)), fin("a")));
    }

    @Test
    void unErrorTerminaLaTareaYDescartaSusPasos() {
        EjecutorPorTicks ejecutor = nuevo(100);
        Paso roto =
                Paso.de(
                        1,
                        () -> {
                            throw new IllegalStateException("roto");
                        });
        ejecutor.encolar("a", List.of(paso("1", 1), roto, paso("2", 1)), fin("a"));
        ejecutor.encolar("b", List.of(paso("b1", 1)), fin("b"));

        ejecutor.tick();
        assertEquals(List.of("1", "b1"), hechos);
        assertEquals(List.of("a:roto", "b"), finales);
        assertFalse(ejecutor.ocupada("a"));
    }

    @Test
    void tareaSinPasosTerminaEnElSiguienteTick() {
        EjecutorPorTicks ejecutor = nuevo(1);
        ejecutor.encolar("a", List.of(), fin("a"));
        ejecutor.tick();
        assertEquals(List.of("a"), finales);
    }

    @Test
    void alFinalizarPuedeEncolarOtraTarea() {
        EjecutorPorTicks ejecutor = nuevo(100);
        ejecutor.encolar(
                "a",
                List.of(paso("a1", 1)),
                error -> ejecutor.encolar("a", List.of(paso("a2", 1)), fin("a")));
        ejecutor.tick();
        ejecutor.tick();
        assertEquals(List.of("a1", "a2"), hechos);
    }

    @Test
    void cancelarTodoDescartaSinAvisar() {
        EjecutorPorTicks ejecutor = nuevo(100);
        ejecutor.encolar("a", List.of(paso("1", 1)), fin("a"));
        assertEquals(Set.of("a"), ejecutor.cancelarTodo());
        ejecutor.tick();
        assertTrue(hechos.isEmpty());
        assertTrue(finales.isEmpty());
    }

    @Test
    void unAvisoQueFallaNoCortaALosDemas() {
        EjecutorPorTicks ejecutor = nuevo(100);
        ejecutor.encolar(
                "a",
                List.of(paso("a1", 1)),
                error -> {
                    throw new IllegalStateException("aviso");
                });
        ejecutor.encolar("b", List.of(paso("b1", 1)), fin("b"));
        ejecutor.tick();
        assertEquals(List.of("a:aviso"), fallosDeAviso);
        assertEquals(List.of("b"), finales);
        assertTrue(ejecutor.vacio());
    }
}
