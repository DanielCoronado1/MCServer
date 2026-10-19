package com.dolfin.zombies.kinds;

import com.dolfin.zombies.ConfiguredZombie;
import com.dolfin.zombies.ZombieKind;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;

/**
 * Slows and chills what it hits, is immune to freezing and leaves ice where it dies (via {@link
 * com.dolfin.zombies.griefing.CongeladoIce}).
 */
public class CongeladoZombie extends ConfiguredZombie {
    private CombatParams.Congelado params;

    public CongeladoZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level, ZombieKind.CONGELADO);
    }

    private CombatParams.Congelado params() {
        var p = params;
        if (p == null) params = p = CombatParams.Congelado.from(settings());
        return p;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        params = null;
    }

    /** Powder-snow freezing never builds up on it. */
    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit
                && target instanceof LivingEntity living
                && abilityEnabled(CombatParams.Congelado.ABILITY)) {
            var p = params();
            boolean slowed =
                    p.lentitudTicks() > 0
                            && living.addEffect(
                                    new MobEffectInstance(
                                            MobEffects.SLOWNESS,
                                            p.lentitudTicks(),
                                            p.nivelLentitud() - 1));
            int frozenBefore = living.getTicksFrozen();
            // canFreeze() is false for leather-armored (freeze immune) targets, which is the
            // intended counter.
            boolean froze = p.congelacionPolvo() && living.canFreeze();
            if (froze) living.setTicksFrozen(Math.max(frozenBefore, p.congelacionTicks()));
            Abilities.debug(
                    this,
                    "congelado_hit",
                    "target={} slowApplied={} slowTicks={} slowAmp={} freezeRequested={}"
                            + " freezeApplied={} frozenTicks={}->{} ",
                    living.getType().toShortString(),
                    slowed,
                    p.lentitudTicks(),
                    p.nivelLentitud() - 1,
                    p.congelacionPolvo(),
                    froze,
                    frozenBefore,
                    living.getTicksFrozen());
        }
        return hit;
    }
}
