package com.dolfin.zombies.kinds;

import com.dolfin.zombies.ConfiguredZombie;
import com.dolfin.zombies.ZombieKind;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import java.util.EnumSet;
import java.util.List;

/** Drains hunger/saturation on hit and runs to food a player dropped before continuing the chase. */
public class HambrientoZombie extends ConfiguredZombie {
    private CombatParams.Hambriento params;

    public HambrientoZombie(EntityType<? extends Zombie> type, Level level) { super(type, level, ZombieKind.HAMBRIENTO); }

    CombatParams.Hambriento params() {
        var p = params;
        if (p == null) params = p = CombatParams.Hambriento.from(settings());
        return p;
    }

    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); params = null; }

    @Override protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new FoodDistractionGoal(this));
    }

    boolean hungerEnabled() { return abilityEnabled(CombatParams.Hambriento.ABILITY); }

    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof Player player && hungerEnabled()) {
            var p = params();
            var food = player.getFoodData();
            int foodBefore = food.getFoodLevel();
            float satBefore = food.getSaturationLevel();
            food.setFoodLevel(Math.max(0, foodBefore - p.hambrePorGolpe()));
            food.setSaturation(Math.max(0f, satBefore - p.saturacionPorGolpe()));
            if (p.efectoHambreTicks() > 0) player.addEffect(new MobEffectInstance(MobEffects.HUNGER, p.efectoHambreTicks(), 0));
            Abilities.debug(this, "hambriento_hit", "target={} food={}->{} saturation={}->{} hungerTicks={}", player.getName().getString(),
                foodBefore, food.getFoodLevel(), satBefore, food.getSaturationLevel(), p.efectoHambreTicks());
        }
        return hit;
    }

    /** Walks to the nearest food item thrown by a player and eats one unit of it. Throttled scan, bounded radius. */
    static final class FoodDistractionGoal extends Goal {
        private static final int MAX_CHASE_TICKS = 100, REPATH_TICKS = 10;
        private final HambrientoZombie zombie;
        private ItemEntity food;
        private int scanDelay, chaseTicks;

        FoodDistractionGoal(HambrientoZombie zombie) {
            this.zombie = zombie;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean active() { return zombie.hungerEnabled() && zombie.params().distraidoPorComida(); }

        private static boolean isPlayerFood(ItemEntity e) {
            return e.isAlive() && !e.getItem().isEmpty() && e.getItem().has(DataComponents.FOOD) && e.getOwner() instanceof Player;
        }

        @Override public boolean canUse() {
            if (!active() || !(zombie.level() instanceof ServerLevel)) return false;
            if (--scanDelay > 0) return false;
            scanDelay = 15 + zombie.getRandom().nextInt(10);
            double radius = zombie.params().radioComida();
            List<ItemEntity> items = zombie.level().getEntitiesOfClass(ItemEntity.class, zombie.getBoundingBox().inflate(radius, 3, radius), HambrientoZombie.FoodDistractionGoal::isPlayerFood);
            if (items.isEmpty()) return false;
            double[] d = new double[items.size()];
            boolean[] ok = new boolean[items.size()];
            for (int i = 0; i < d.length; i++) { d[i] = zombie.distanceToSqr(items.get(i)); ok[i] = true; }
            int idx = FoodChoice.nearest(d, ok, radius * radius);
            if (idx < 0) return false;
            food = items.get(idx);
            return true;
        }

        @Override public boolean canContinueToUse() { return active() && food != null && isPlayerFood(food) && chaseTicks < MAX_CHASE_TICKS; }

        @Override public void start() {
            chaseTicks = 0;
            Abilities.debug(zombie, "hambriento_food_target", "item={} owner={} distance={}", food.getItem().getItem(), food.getOwner() == null ? "none" : food.getOwner().getName().getString(), Math.sqrt(zombie.distanceToSqr(food)));
            zombie.getNavigation().moveTo(food, 1.2);
        }

        @Override public void tick() {
            chaseTicks++;
            zombie.getLookControl().setLookAt(food);
            if (zombie.distanceToSqr(food) <= 2.25) {
                var stack = food.getItem();
                Abilities.debug(zombie, "hambriento_food_eaten", "item={} remaining={}", stack.getItem(), stack.getCount() - 1);
                stack.shrink(1);
                if (stack.isEmpty()) food.discard(); else food.setItem(stack);
                zombie.heal(2f);
                zombie.level().playSound(null, zombie.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.HOSTILE, 1f, 1f);
                food = null;
                return;
            }
            if (chaseTicks % REPATH_TICKS == 0) zombie.getNavigation().moveTo(food, 1.2);
        }

        @Override public void stop() {
            if (food != null && chaseTicks >= MAX_CHASE_TICKS) Abilities.debug(zombie, "hambriento_food_giveup", "ticks={}", chaseTicks);
            food = null;
            zombie.getNavigation().stop();
        }
    }
}
