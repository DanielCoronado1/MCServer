package com.dolfin.zombies.kinds;

import com.dolfin.zombies.ConfiguredZombie;
import com.dolfin.zombies.DolfinZombies;
import com.dolfin.zombies.ZombieKind;
import com.dolfin.zombies.griefing.BlockGuard;
import com.dolfin.zombies.griefing.GuardDecision.Reason;
import com.dolfin.zombies.griefing.RateLimiter;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;

import java.util.HashSet;
import java.util.Set;

/**
 * Granjero (#4): wanders to farms with a hoe ("horca"). With {@code destruirCultivos} on it
 * tramples farmland into dirt (popping the crop); with {@code atacarAnimales} on it hunts the
 * listed animals. Both default off. Farm search samples a bounded set of columns at most every 20
 * ticks.
 */
public class GranjeroZombie extends ConfiguredZombie {
    static final String CROPS = "destruirCultivos", ANIMALS = "atacarAnimales";
    static final int SCAN_INTERVAL = 20, COLUMNS_PER_SCAN = 24, MAX_RADIUS = 32, GIVE_UP_SCANS = 15;
    private final RateLimiter limiter = new RateLimiter(1200);
    private Params params;
    private BlockPos farmPos;
    private int farmScans;

    public GranjeroZombie(EntityType<ConfiguredZombie> type, Level level) {
        super(type, level, ZombieKind.GRANJERO);
    }

    record Params(int radio, int maxPorMinuto, Set<EntityType<?>> animales) {}

    private Params params() {
        if (params == null) {
            JsonObject h = settings().getAsJsonObject("habilidades");
            JsonObject c = h.has(CROPS) ? h.getAsJsonObject(CROPS) : new JsonObject(),
                    a = h.has(ANIMALS) ? h.getAsJsonObject(ANIMALS) : new JsonObject();
            int radio =
                    (int)
                            Math.min(
                                    MAX_RADIUS,
                                    c.has("radioBusqueda")
                                            ? c.get("radioBusqueda").getAsDouble()
                                            : 24);
            Set<EntityType<?>> types = new HashSet<>();
            if (a.has("animales"))
                for (var e : a.getAsJsonArray("animales")) {
                    var id = Identifier.tryParse(e.getAsString());
                    if (id != null)
                        BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(types::add);
                }
            params =
                    new Params(
                            Math.max(1, radio),
                            c.has("maxBloquesPorMinuto")
                                    ? c.get("maxBloquesPorMinuto").getAsInt()
                                    : 30,
                            Set.copyOf(types));
        }
        return params;
    }

    @Override
    protected void populateDefaultEquipmentSlots(
            RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
        setDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Predicate runs later (after construction), so referencing instance state is safe here.
        targetSelector.addGoal(
                4,
                new NearestAttackableTargetGoal<>(
                        this, Animal.class, 10, true, false, this::wantsAnimal));
    }

    private boolean wantsAnimal(LivingEntity target, ServerLevel level) {
        if (!abilityEnabled(ANIMALS) || !params().animales().contains(target.getType()))
            return false;
        return BlockGuard.puedeAtacar(level, target.blockPosition(), this, "attack_animal");
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (target instanceof Animal animal && !wantsAnimal(animal, level)) {
            setTarget(null);
            return false;
        }
        return super.doHurtTarget(level, target);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!com.dolfin.zombies.ZombieTickPolicy.scanDue(kind(), tickCount, getId())) return;
        // Live switch: drop an animal target the moment the ability is turned off.
        if (getTarget() instanceof Animal a && !wantsAnimal(a, level)) {
            setTarget(null);
            DolfinZombies.LOGGER.debug(
                    "event=granjero_drop_target id={} uuid={} target={}",
                    kind().id(),
                    getUUID(),
                    a.getType());
        }
        if (getTarget() != null) {
            farmPos = null;
            return;
        }
        if (farmPos == null) {
            farmPos = findFarmland(level);
            farmScans = 0;
            return;
        }
        if (!level.isLoaded(farmPos)
                || !(level.getBlockState(farmPos).getBlock() instanceof FarmlandBlock)
                || ++farmScans > GIVE_UP_SCANS) {
            farmPos = null;
            return;
        }
        if (distanceToSqr(farmPos.getX() + .5, farmPos.getY() + 1, farmPos.getZ() + .5) > 2.5) {
            getNavigation()
                    .moveTo(farmPos.getX() + .5, farmPos.getY() + 1, farmPos.getZ() + .5, 1.0);
            return;
        }
        BlockPos pos = farmPos;
        farmPos = null;
        if (abilityEnabled(CROPS)) trample(level, pos);
    }

    private void trample(ServerLevel level, BlockPos pos) {
        Params p = params();
        if (!limiter.canAcquire(level.getGameTime(), p.maxPorMinuto())) {
            BlockGuard.rejected(this, pos, "trample", Reason.RATE_LIMIT);
            return;
        }
        if (!BlockGuard.puedeModificar(level, pos, this, "trample")) return;
        BlockPos above = pos.above();
        if (!level.getBlockState(above).isAir())
            BlockGuard.tryBreak(level, this, above, true, "trample_crop");
        if (BlockGuard.tryChange(level, this, pos, Blocks.DIRT.defaultBlockState(), "trample"))
            limiter.tryAcquire(level.getGameTime(), p.maxPorMinuto());
    }

    /**
     * Samples {@link #COLUMNS_PER_SCAN} random loaded columns (feet y±2) inside {@code
     * radioBusqueda}.
     */
    private BlockPos findFarmland(ServerLevel level) {
        int r = params().radio();
        BlockPos feet = blockPosition();
        var mutable = new BlockPos.MutableBlockPos();
        for (int i = 0; i < COLUMNS_PER_SCAN; i++) {
            int x = feet.getX() + getRandom().nextInt(2 * r + 1) - r,
                    z = feet.getZ() + getRandom().nextInt(2 * r + 1) - r;
            mutable.set(x, feet.getY(), z);
            if (!level.isLoaded(mutable)) continue;
            for (int dy = -2; dy <= 2; dy++) {
                mutable.setY(feet.getY() + dy);
                if (level.getBlockState(mutable).getBlock() instanceof FarmlandBlock) {
                    DolfinZombies.LOGGER.debug(
                            "event=granjero_farm_found id={} uuid={} pos={} destruir={}",
                            kind().id(),
                            getUUID(),
                            mutable.toShortString(),
                            abilityEnabled(CROPS));
                    return mutable.immutable();
                }
            }
        }
        return null;
    }
}
