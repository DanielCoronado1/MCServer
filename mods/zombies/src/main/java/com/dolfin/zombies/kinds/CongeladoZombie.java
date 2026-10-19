package com.dolfin.zombies.kinds;

import com.dolfin.zombies.ConfiguredZombie;
import com.dolfin.zombies.DolfinZombies;
import com.dolfin.zombies.ZombieKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;

/** Slows and chills what it hits, is immune to freezing and leaves ice where it dies. */
public class CongeladoZombie extends ConfiguredZombie {
    private CombatParams.Congelado params;
    private boolean iced;

    public CongeladoZombie(EntityType<? extends Zombie> type, Level level) { super(type, level, ZombieKind.CONGELADO); }

    private CombatParams.Congelado params() {
        var p = params;
        if (p == null) params = p = CombatParams.Congelado.from(settings());
        return p;
    }

    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); params = null; }

    /** Powder-snow freezing never builds up on it. */
    @Override public boolean canFreeze() { return false; }

    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living && abilityEnabled(CombatParams.Congelado.ABILITY)) {
            var p = params();
            boolean slowed = p.lentitudTicks() > 0 && living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, p.lentitudTicks(), p.nivelLentitud() - 1));
            int frozenBefore = living.getTicksFrozen();
            // canFreeze() is false for leather-armored (freeze immune) targets, which is the intended counter.
            boolean froze = p.congelacionPolvo() && living.canFreeze();
            if (froze) living.setTicksFrozen(Math.max(frozenBefore, p.congelacionTicks()));
            Abilities.debug(this, "congelado_hit", "target={} slowApplied={} slowTicks={} slowAmp={} freezeRequested={} freezeApplied={} frozenTicks={}->{} ",
                living.getType().toShortString(), slowed, p.lentitudTicks(), p.nivelLentitud() - 1, p.congelacionPolvo(), froze, frozenBefore, living.getTicksFrozen());
        }
        return hit;
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server && !iced && abilityEnabled(CombatParams.Congelado.ABILITY)) {
            iced = true;
            placeIce(server);
        }
    }

    private void placeIce(ServerLevel level) {
        var p = params();
        // TODO(#4): replace with the shared block-change guard (claims/Ledger/mobGriefing) once it lands on this branch.
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            Abilities.debug(this, "congelado_ice_skipped", "reason=mobGriefing_false");
            return;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(p.bloqueAlMorir())).orElse(null);
        if (block == null || block == Blocks.AIR) {
            DolfinZombies.LOGGER.warn("event=congelado_ice_invalid id={} block={} fallback=minecraft:ice", kind().id(), p.bloqueAlMorir());
            block = Blocks.ICE;
        }
        int r = p.radioHielo(), placed = 0;
        BlockPos origin = blockPosition();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + 1) continue;
                BlockPos pos = origin.offset(dx, 0, dz);
                var here = level.getBlockState(pos);
                if (!(here.isAir() || (here.canBeReplaced() && here.getFluidState().isEmpty()))) continue;
                if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
                if (level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL)) placed++;
            }
        }
        Abilities.debug(this, "congelado_ice", "block={} radius={} placed={} pos={}", p.bloqueAlMorir(), r, placed, origin);
    }
}
