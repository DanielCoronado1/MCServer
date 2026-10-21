package com.dolfin.creepers.dungeon.fin;

import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.Entity;

/**
 * Punto de enganche entre el jefe de una dungeon (#20) y la victoria (#17). Para que su muerte dé
 * la victoria basta con marcar la entidad al invocarla:
 *
 * <pre>{@code JefeDungeon.marcar(jefe);}</pre>
 *
 * Al morir una entidad marcada dentro de la dimensión de dungeons se declara la victoria de la
 * partida activa cuya zona contiene el punto de su muerte. Quien prefiera otro disparador (un
 * mecanismo, un evento propio) puede llamar directamente a {@link
 * FinDePartida#declararVictoria(String)}. Una entidad marcada que muere fuera de una zona con
 * partida activa se ignora.
 */
public final class JefeDungeon {
    /** Etiqueta de entidad ({@code /tag}) que marca al jefe cuya muerte da la victoria. */
    public static final String ETIQUETA = "dolfincreepers.jefe_dungeon";

    private JefeDungeon() {}

    /** Marca a {@code jefe} para que su muerte dé la victoria. */
    public static void marcar(Entity jefe) {
        jefe.addTag(ETIQUETA);
    }

    public static boolean esJefe(Entity entidad) {
        return entidad.entityTags().contains(ETIQUETA);
    }

    /** Escucha la muerte de entidades marcadas y declara la victoria en {@code fin}. */
    public static void registrar(FinDePartida fin) {
        ServerLivingEntityEvents.AFTER_DEATH.register(
                (entidad, fuente) -> {
                    if (esJefe(entidad) && DimensionDungeons.es(entidad.level())) {
                        fin.jefeCaido(entidad.getX(), entidad.getY(), entidad.getZ());
                    }
                });
    }
}
