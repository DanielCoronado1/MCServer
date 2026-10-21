package com.dolfin.creepers.dungeon.reglas;

import com.dolfin.creepers.dungeon.mundo.DimensionDungeons;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Ningún jugador hace daño a otro dentro de la dimensión de dungeons: ni directo, ni con flechas,
 * tridentes, pociones arrojadizas, nubes, fuego de proyectiles, TNT, rayos de canalización o
 * mascotas. El daño se cancela con {@code ALLOW_DAMAGE} y los efectos dañinos con el mixin {@code
 * EfectosDungeonMixin}, que llama a {@link #permiteEfecto}; el daño de mobs y del entorno no se
 * toca.
 */
final class ReglasDanio {
    private ReglasDanio() {}

    static void registrar() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (victima, fuente, cantidad) -> permiteDanio(victima, fuente));
    }

    /** {@code false} si otro jugador causa este daño a un jugador dentro de la dimensión. */
    static boolean permiteDanio(LivingEntity victima, DamageSource fuente) {
        if (!(victima instanceof ServerPlayer jugador) || !DimensionDungeons.es(jugador.level())) {
            return true;
        }
        return !DecisionesReglas.bloqueaDanio(jugador.getUUID(), responsable(fuente));
    }

    /**
     * {@code false} si el efecto es dañino, lo causa otro jugador (poción, nube, flecha con efecto)
     * y la víctima es un jugador dentro de la dimensión.
     *
     * @param origen la entidad que aplica el efecto; puede ser {@code null}
     */
    static boolean permiteEfecto(LivingEntity victima, MobEffectInstance efecto, Entity origen) {
        if (!(victima instanceof ServerPlayer jugador) || !DimensionDungeons.es(jugador.level())) {
            return true;
        }
        boolean daninoso = efecto.getEffect().value().getCategory() == MobEffectCategory.HARMFUL;
        return !DecisionesReglas.bloqueaEfecto(
                daninoso, jugador.getUUID(), jugadorResponsable(origen));
    }

    private static Optional<UUID> responsable(DamageSource fuente) {
        return jugadorResponsable(fuente.getEntity())
                .or(() -> jugadorResponsable(fuente.getDirectEntity()));
    }

    private static Optional<UUID> jugadorResponsable(Entity origen) {
        return Propietarios.jugadorResponsable(origen, e -> e instanceof Player, ReglasDanio::dueno)
                .map(Entity::getUUID);
    }

    /** Dueño directo: tirador, amo, quien encendió el TNT o causó el rayo. */
    private static Entity dueno(Entity entidad) {
        return switch (entidad) {
            case TraceableEntity rastreable -> rastreable.getOwner();
            case OwnableEntity propia -> propia.getOwner();
            case LightningBolt rayo -> rayo.getCause();
            default -> null;
        };
    }
}
