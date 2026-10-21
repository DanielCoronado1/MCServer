package com.dolfin.creepers.dungeon.fin;

import com.dolfin.creepers.dungeon.modelo.ResultadoPartida;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cierres de partida programados (#17), puros: la ventana de botín tras la victoria y la espera
 * antes de salir tras una derrota. Una dungeon tiene como mucho uno; el primero que se programa
 * gana, así una derrota no pisa una victoria ni al revés.
 */
public final class CierresPendientes {
    /** Terminar la partida de {@code dungeon} con {@code resultado} a partir de {@code cuando}. */
    public record Cierre(String dungeon, ResultadoPartida resultado, long cuandoMillis) {}

    private final Map<String, Cierre> cierres = new LinkedHashMap<>();

    /**
     * @return false si la dungeon ya tenía un cierre programado
     */
    public boolean programar(String dungeon, ResultadoPartida resultado, long cuandoMillis) {
        return cierres.putIfAbsent(dungeon, new Cierre(dungeon, resultado, cuandoMillis)) == null;
    }

    public Optional<Cierre> de(String dungeon) {
        return Optional.ofNullable(cierres.get(dungeon));
    }

    /** Cierres que ya vencieron; los quita de la lista. */
    public List<Cierre> vencidos(long ahoraMillis) {
        List<Cierre> vencidos = new ArrayList<>();
        cierres.values().removeIf(c -> c.cuandoMillis() <= ahoraMillis && vencidos.add(c));
        return vencidos;
    }

    public void descartar(String dungeon) {
        cierres.remove(dungeon);
    }
}
