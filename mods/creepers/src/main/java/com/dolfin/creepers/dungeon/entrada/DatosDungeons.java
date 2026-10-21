package com.dolfin.creepers.dungeon.entrada;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.dungeon.eventos.EventoDungeon.DungeonCambioEstado;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que debe sobrevivir a una caída del servidor, guardado en el overworld: el retorno de cada
 * jugador que entró a una dungeon (hasta que se le restaura) y las dungeons que tenían partida o
 * reinicio en curso (para reiniciarlas al arrancar, #19). Las partidas en sí viven solo en memoria:
 * tras una caída nadie muere ni pierde nada, solo vuelve a su sitio.
 */
public final class DatosDungeons extends SavedData {
    public static final Codec<DatosDungeons> CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Codec.unboundedMap(
                                                            UUIDUtil.STRING_CODEC,
                                                            RetornoGuardado.CODEC)
                                                    .optionalFieldOf("retornos", Map.of())
                                                    .forGetter(d -> d.retornos),
                                            Codec.STRING
                                                    .listOf()
                                                    .optionalFieldOf(
                                                            "pendientesDeReinicio", List.of())
                                                    .forGetter(
                                                            d ->
                                                                    List.copyOf(
                                                                            d.pendientesDeReinicio)))
                                    .apply(i, DatosDungeons::new));

    public static final SavedDataType<DatosDungeons> TIPO =
            new SavedDataType<>(DolfinCreepers.id("dungeons"), DatosDungeons::new, CODEC, null);

    private final Map<UUID, RetornoGuardado> retornos;
    private final Set<String> pendientesDeReinicio;

    public DatosDungeons() {
        this(Map.of(), List.of());
    }

    private DatosDungeons(Map<UUID, RetornoGuardado> retornos, List<String> pendientes) {
        this.retornos = new HashMap<>(retornos);
        this.pendientesDeReinicio = new LinkedHashSet<>(pendientes);
    }

    /** Carga (o crea) los datos del mundo. */
    public static DatosDungeons de(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TIPO);
    }

    /**
     * Escribe en disco ya, bloqueando: se usa tras guardar retornos de entrada, para que una caída
     * inmediata no deje a nadie sin posición guardada.
     */
    public static void guardarYa(MinecraftServer server) {
        server.overworld().getDataStorage().saveAndJoin();
    }

    /** Programa una escritura asíncrona (tras restaurar a alguien). */
    public static void guardarPronto(MinecraftServer server) {
        server.overworld().getDataStorage().scheduleSave();
    }

    public Optional<RetornoGuardado> retorno(UUID jugador) {
        return Optional.ofNullable(retornos.get(jugador));
    }

    /** Guarda el retorno solo si no había uno: nunca se pisa la posición original. */
    public void guardarRetornoSiFalta(UUID jugador, RetornoGuardado retorno) {
        if (retornos.putIfAbsent(jugador, retorno) == null) {
            setDirty();
        }
    }

    public Optional<RetornoGuardado> quitarRetorno(UUID jugador) {
        RetornoGuardado quitado = retornos.remove(jugador);
        if (quitado != null) {
            setDirty();
        }
        return Optional.ofNullable(quitado);
    }

    public Set<UUID> jugadoresConRetorno() {
        return Set.copyOf(retornos.keySet());
    }

    public List<String> pendientesDeReinicio() {
        return List.copyOf(pendientesDeReinicio);
    }

    /** Olvida una dungeon pendiente que ya no existe en la config. */
    public void descartarPendiente(String dungeon) {
        if (pendientesDeReinicio.remove(dungeon)) {
            setDirty();
        }
    }

    /** Refleja en disco los estados que exigen reinicio tras una caída. */
    public void registrar(DungeonCambioEstado cambio) {
        boolean cambiado =
                cambio.nuevo().requiereReinicioTrasCaida()
                        ? pendientesDeReinicio.add(cambio.dungeon())
                        : pendientesDeReinicio.remove(cambio.dungeon());
        if (cambiado) {
            setDirty();
        }
    }
}
