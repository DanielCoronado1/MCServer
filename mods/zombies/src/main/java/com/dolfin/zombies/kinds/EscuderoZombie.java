package com.dolfin.zombies.kinds;

import com.dolfin.zombies.ConfiguredZombie;
import com.dolfin.zombies.ZombieKind;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;

/** Blocks everything arriving inside a frontal cone; axes switch the shield off for a while. */
public class EscuderoZombie extends ConfiguredZombie {
    private CombatParams.Escudero params;
    private long shieldDisabledUntil;

    public EscuderoZombie(EntityType<? extends Zombie> type, Level level) { super(type, level, ZombieKind.ESCUDERO); }

    private CombatParams.Escudero params() {
        var p = params;
        if (p == null) params = p = CombatParams.Escudero.from(settings());
        return p;
    }

    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); params = null; }

    @Override protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        super.populateDefaultEquipmentSlots(random, difficulty);
        equipShield();
    }

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData group) {
        var result = super.finalizeSpawn(level, difficulty, reason, group);
        equipShield();
        params = null;
        return result;
    }

    private void equipShield() {
        if (getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        setDropChance(EquipmentSlot.OFFHAND, 0f); // the shield drop is rolled with probabilidadSoltarEscudo instead
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (amount > 0 && blocks(level, source, amount)) return false;
        return super.hurtServer(level, source, amount);
    }

    /** True when the hit is absorbed. Every outcome is logged at DEBUG with the decisive values. */
    private boolean blocks(ServerLevel level, DamageSource source, float amount) {
        var p = params();
        boolean bypass = source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        Vec3 src = source.getSourcePosition();
        boolean projectile = source.getDirectEntity() instanceof Projectile;
        boolean disabled = level.getGameTime() < shieldDisabledUntil;
        double angle = src == null ? Double.NaN : ShieldMath.angleBetween(getYRot(), getX(), getZ(), src.x, src.z);
        boolean inCone = src != null && ShieldMath.inCone(getYRot(), getX(), getZ(), src.x, src.z, p.anguloBloqueo());
        var decision = ShieldMath.decide(abilityEnabled(CombatParams.Escudero.ABILITY), bypass, src != null, projectile,
            p.bloqueaProyectiles(), disabled, inCone);
        Abilities.debug(this, "escudero_block", "decision={} damage={} type={} attacker={} projectile={} angle={} cone={} disabledTicksLeft={}",
            decision, amount, source.getMsgId(), source.getEntity() == null ? "none" : source.getEntity().getType().toShortString(),
            projectile, Double.isNaN(angle) ? "n/a" : Math.round(angle), p.anguloBloqueo(), Math.max(0, shieldDisabledUntil - level.getGameTime()));
        if (decision != ShieldMath.Decision.BLOCK) return false;

        level.broadcastEntityEvent(this, (byte) 29); // vanilla shield-block sound
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        level.sendParticles(ParticleTypes.CRIT, getX() + look.x * 0.6, getY(0.6), getZ() + look.z * 0.6, 6, 0.2, 0.2, 0.2, 0.1);
        if (!projectile && source.getEntity() instanceof LivingEntity attacker && attacker.getMainHandItem().is(ItemTags.AXES) && p.hachaSegundos() > 0) {
            shieldDisabledUntil = level.getGameTime() + Math.round(p.hachaSegundos() * 20);
            level.broadcastEntityEvent(this, (byte) 30); // vanilla shield-break sound
            Abilities.debug(this, "escudero_shield_disabled", "attacker={} seconds={} until={}", attacker.getType().toShortString(), p.hachaSegundos(), shieldDisabledUntil);
        }
        return true;
    }

    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        var p = params();
        if (!recentlyHit || !abilityEnabled(CombatParams.Escudero.ABILITY)) return;
        double roll = random.nextDouble();
        boolean drop = roll < p.probabilidadSoltarEscudo();
        Abilities.debug(this, "escudero_shield_drop", "roll={} chance={} dropped={}", roll, p.probabilidadSoltarEscudo(), drop);
        if (drop) {
            var shield = new ItemStack(Items.SHIELD);
            shield.setDamageValue((int) (shield.getMaxDamage() * (0.4 + random.nextDouble() * 0.5)));
            spawnAtLocation(level, shield);
        }
    }

    public long shieldDisabledUntil() { return shieldDisabledUntil; }
}
