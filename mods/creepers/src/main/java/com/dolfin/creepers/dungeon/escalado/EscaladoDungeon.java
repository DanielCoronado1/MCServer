package com.dolfin.creepers.dungeon.escalado;

import com.dolfin.creepers.dungeon.fin.JefeDungeon;
import com.dolfin.creepers.dungeon.modelo.Partida;
import com.dolfin.creepers.dungeon.plantilla.AjusteAparicion;
import com.dolfin.creepers.dungeon.plantilla.DatosMarcador;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Aplica el escalado de vida y daño a los mobs que aparecen en la dungeon (#18). Usa un
 * AttributeModifier con id fijo {@code dolfincreepers:dungeon_escala} que reemplaza cualquier valor
 * anterior para evitar acumulación accidental.
 */
public final class EscaladoDungeon implements AjusteAparicion {
    public static final Identifier MODIFIER_ID =
            Identifier.fromNamespaceAndPath("dolfincreepers", "dungeon_escala");

    @Override
    public int cantidad(Partida partida, DatosMarcador marcador) {
        return CalculoEscalado.cantidadMobs(partida, marcador);
    }

    @Override
    public void ajustar(Partida partida, DatosMarcador marcador, Entity entidad) {
        escalar(partida, entidad);
    }

    /**
     * Escala los atributos de vida y daño de la entidad según la dificultad y jugadores. Marca a
     * los mobs como persistentes para evitar despawn.
     */
    public static void escalar(Partida partida, Entity entidad) {
        if (!(entidad instanceof LivingEntity living)) {
            return;
        }

        boolean esJefe = JefeDungeon.esJefe(living);
        double multVida =
                esJefe
                        ? CalculoEscalado.multiplicadorVidaJefe(partida)
                        : CalculoEscalado.multiplicadorVidaMob(partida);
        double multDanio = CalculoEscalado.multiplicadorDanioMob(partida);

        // Aplica modificador de vida máxima
        AttributeInstance attrVida = living.getAttribute(Attributes.MAX_HEALTH);
        if (attrVida != null && Math.abs(multVida - 1.0) > 1.0e-5) {
            AttributeModifier modVida =
                    new AttributeModifier(
                            MODIFIER_ID,
                            multVida - 1.0,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            attrVida.addOrReplacePermanentModifier(modVida);
            living.setHealth(living.getMaxHealth());
        }

        // Aplica modificador de daño de ataque
        AttributeInstance attrDanio = living.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attrDanio != null && Math.abs(multDanio - 1.0) > 1.0e-5) {
            AttributeModifier modDanio =
                    new AttributeModifier(
                            MODIFIER_ID,
                            multDanio - 1.0,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            attrDanio.addOrReplacePermanentModifier(modDanio);
        }

        // Mobs persistentes para que no desaparezcan por distancia
        if (entidad instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
    }
}
