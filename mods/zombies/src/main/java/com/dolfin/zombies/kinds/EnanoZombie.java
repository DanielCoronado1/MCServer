package com.dolfin.zombies.kinds;

import com.dolfin.zombies.ConfiguredZombie;
import com.dolfin.zombies.ZombieKind;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;

/** Small body: 0.5 x 0.9 hitbox by default (fits a one-block-high gap). */
public class EnanoZombie extends ConfiguredZombie {
    // Deliberately no initializer: getDefaultDimensions runs inside the Entity constructor and
    // applyStats inside
    // ConfiguredZombie's, both before this class' field initializers, which would otherwise
    // overwrite the value.
    private CombatParams.Enano params;

    public EnanoZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level, ZombieKind.ENANO);
    }

    @Override
    public void applyStats() {
        super.applyStats();
        params = CombatParams.Enano.from(settings());
        refreshDimensions();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        params = CombatParams.Enano.from(settings());
        refreshDimensions();
    }

    /**
     * Final box = ancho x alto after the SCALE attribute (= escala) is applied, so the base is
     * divided by it.
     */
    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        var p = params == null ? CombatParams.Enano.DEFAULT : params;
        if (!abilityOn()) return super.getDefaultDimensions(pose);
        return EntityDimensions.scalable(
                (float) (p.ancho() / p.escala()), (float) (p.alto() / p.escala()));
    }

    private boolean abilityOn() {
        // Client-side instances have no config: keep the small body there, matching the server
        // default.
        return level() == null
                || level().isClientSide()
                || abilityEnabled(CombatParams.Enano.ABILITY);
    }
}
