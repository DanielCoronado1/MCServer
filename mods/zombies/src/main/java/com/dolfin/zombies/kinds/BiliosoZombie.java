package com.dolfin.zombies.kinds;

import com.dolfin.zombies.ConfiguredZombie;
import com.dolfin.zombies.ZombieKind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;

/** Heavy and hard to push; releases a nausea/poison cloud on death that spares zombies. */
public class BiliosoZombie extends ConfiguredZombie {
    private CombatParams.Bilioso params;
    private boolean clouded;

    public BiliosoZombie(EntityType<? extends Zombie> type, Level level) { super(type, level, ZombieKind.BILIOSO); }

    private CombatParams.Bilioso params() {
        var p = params;
        if (p == null) params = p = CombatParams.Bilioso.from(settings());
        return p;
    }

    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); params = null; }

    /** Called from the super constructor and on spawn: must not touch subclass fields. */
    @Override public void applyStats() {
        super.applyStats();
        var knockback = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) knockback.setBaseValue(CombatParams.Bilioso.from(settings()).resistenciaEmpuje());
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server && !clouded && abilityEnabled(CombatParams.Bilioso.ABILITY)) {
            clouded = true;
            com.dolfin.zombies.ZombieSounds.play(this,com.dolfin.zombies.ZombieSounds.BILIOSO_BURST);
            BiliousClouds.spawn(server, getX(), getY(), getZ(), params(), getUUID());
            Abilities.debug(this, "bilioso_death", "cause={} radius={} ticks={}", source.getMsgId(), params().radioNube(), params().duracionNube());
        }
    }
}
