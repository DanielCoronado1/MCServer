package com.dolfin.zombies.kinds;

import com.dolfin.zombies.*;
import com.dolfin.zombies.custody.CustodyState;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;

import java.util.*;

/**
 * Issue #7: Mimo zombie (`mimo_zombie`). Never spawns naturally. Appears near a player's death site
 * within `minutosVentana` (unless killed by Contagiado), bearing the dead player's skin profile,
 * visible name, 1.5x player max health, and unobtainable copied armor (without enchantments when
 * `copiarEncantamientos` is false). Mimics player animations (crouching, looking around, arm
 * swings).
 */
public final class MimoZombie extends ConfiguredZombie {
    private static final EntityDataAccessor<String> DATA_MIMIC_NAME =
            SynchedEntityData.defineId(MimoZombie.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_MIMIC_UUID =
            SynchedEntityData.defineId(MimoZombie.class, EntityDataSerializers.STRING);

    private UUID mimicPlayerUuid;
    private String mimicPlayerName = "";
    private double sourcePlayerHealth = 20;
    private int crouchTicks;

    public MimoZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level, ZombieKind.MIMO);
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MIMIC_NAME, "");
        builder.define(DATA_MIMIC_UUID, "");
    }

    public Optional<ResolvableProfile> mimicProfile() {
        String uuidStr = entityData.get(DATA_MIMIC_UUID);
        String name = entityData.get(DATA_MIMIC_NAME);
        if (uuidStr.isEmpty() && name.isEmpty()) return Optional.empty();
        try {
            if (!uuidStr.isEmpty())
                return Optional.of(ResolvableProfile.createUnresolved(UUID.fromString(uuidStr)));
        } catch (IllegalArgumentException ignored) {
        }
        return name.isEmpty()
                ? Optional.empty()
                : Optional.of(ResolvableProfile.createUnresolved(name));
    }

    /**
     * Copies the player's 4 armor slots, stripping enchantments unless {@code keepEnchantments} is
     * true.
     */
    public static List<ItemStack> copyPlayerArmor(ServerPlayer player, boolean keepEnchantments) {
        EquipmentSlot[] slots = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
        };
        List<ItemStack> out = new ArrayList<>(4);
        for (EquipmentSlot slot : slots) {
            ItemStack s = player.getItemBySlot(slot);
            if (s.isEmpty()) {
                out.add(ItemStack.EMPTY);
            } else {
                ItemStack copy = s.copy();
                if (!keepEnchantments) {
                    copy.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                }
                out.add(copy);
            }
        }
        return out;
    }

    public void configureFromDeath(
            UUID playerUuid, String playerName, double playerMaxHealth, List<ItemStack> armor) {
        this.mimicPlayerUuid = playerUuid;
        this.mimicPlayerName = playerName == null ? "" : playerName;
        if (playerUuid != null) setPlayerAppearance(ResolvableProfile.createUnresolved(playerUuid));
        else if (!this.mimicPlayerName.isEmpty())
            setPlayerAppearance(ResolvableProfile.createUnresolved(this.mimicPlayerName));
        this.sourcePlayerHealth = playerMaxHealth;
        this.entityData.set(DATA_MIMIC_UUID, playerUuid == null ? "" : playerUuid.toString());
        this.entityData.set(DATA_MIMIC_NAME, this.mimicPlayerName);
        if (!this.mimicPlayerName.isEmpty()) {
            setCustomName(Component.literal(this.mimicPlayerName));
            setCustomNameVisible(true);
        }
        double targetHealth =
                CustodyState.mimoHealth(playerMaxHealth, settings().get("vida").getAsDouble());
        if (BloodMoon.active() && level() instanceof ServerLevel sl && BloodMoon.active(sl)) {
            targetHealth *=
                    settings().getAsJsonObject("lunaDeSangre").get("bonusStats").getAsDouble();
        }
        var maxHealthAttr = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) maxHealthAttr.setBaseValue(targetHealth);
        setHealth((float) targetHealth);

        EquipmentSlot[] slots = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
        };
        for (int i = 0; i < slots.length; i++) {
            ItemStack piece = armor != null && i < armor.size() ? armor.get(i) : ItemStack.EMPTY;
            setItemSlot(slots[i], piece == null ? ItemStack.EMPTY : piece.copy());
            setDropChance(slots[i], 0f);
        }
        setDropChance(EquipmentSlot.MAINHAND, 0f);
        setDropChance(EquipmentSlot.OFFHAND, 0f);
    }

    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty,
            EntitySpawnReason reason,
            SpawnGroupData group) {
        var res = super.finalizeSpawn(level, difficulty, reason, group);
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0f);
        if (mimicPlayerUuid == null) {
            var nearest = level.getNearestPlayer(getX(), getY(), getZ(), 64, p -> !p.isSpectator());
            if (nearest instanceof ServerPlayer sp) {
                boolean copyEnch =
                        settings()
                                .getAsJsonObject("habilidades")
                                .getAsJsonObject("aparicionPorMuerte")
                                .get("copiarEncantamientos")
                                .getAsBoolean();
                configureFromDeath(
                        sp.getUUID(),
                        sp.getGameProfile().name(),
                        sp.getMaxHealth(),
                        copyPlayerArmor(sp, copyEnch));
                setPlayerAppearance(ResolvableProfile.createResolved(sp.getGameProfile()));
            }
        }
        return res;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0f);
        if (getTarget() == null) {
            if (crouchTicks > 0) {
                crouchTicks--;
                setShiftKeyDown(true);
                setPose(Pose.CROUCHING);
            } else {
                setShiftKeyDown(false);
                if (getPose() == Pose.CROUCHING) setPose(Pose.STANDING);
                if (tickCount % 45 == 0 && level.getRandom().nextFloat() < .35f) {
                    crouchTicks = 15 + level.getRandom().nextInt(25);
                }
            }
            if (tickCount % 30 == 0 && level.getRandom().nextFloat() < .4f) {
                swing(
                        InteractionHand.MAIN_HAND,
                        net.minecraft.world.item.component.SwingAnimation.DEFAULT,
                        true);
            }
        } else if (isShiftKeyDown()) {
            crouchTicks = 0;
            setShiftKeyDown(false);
            if (getPose() == Pose.CROUCHING) setPose(Pose.STANDING);
        }
    }

    /** Mimo armor and equipment can never be obtained by killing or shearing/disarming it. */
    @Override
    protected void dropEquipment(ServerLevel level) {}

    @Override
    public void dropPreservedEquipment(ServerLevel level) {}

    @Override
    public Set<EquipmentSlot> dropPreservedEquipment(
            ServerLevel level, java.util.function.Predicate<ItemStack> filter) {
        return Set.of();
    }

    @Override
    protected void dropCustomDeathLoot(
            ServerLevel level, DamageSource source, boolean recentlyHit) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (mimicPlayerUuid != null) {
            out.putString("DolfinMimicUuid", mimicPlayerUuid.toString());
            out.putString("DolfinMimicName", mimicPlayerName);
            out.putDouble("DolfinMimicSourceHealth", sourcePlayerHealth);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0f);
        in.getString("DolfinMimicUuid")
                .ifPresent(
                        raw -> {
                            try {
                                this.mimicPlayerUuid = UUID.fromString(raw);
                                this.mimicPlayerName = in.getStringOr("DolfinMimicName", "");
                                this.sourcePlayerHealth =
                                        in.getDoubleOr("DolfinMimicSourceHealth", 20.0);
                                this.entityData.set(DATA_MIMIC_UUID, raw);
                                this.entityData.set(DATA_MIMIC_NAME, this.mimicPlayerName);
                                if (!hasPlayerAppearance())
                                    setPlayerAppearance(
                                            ResolvableProfile.createUnresolved(
                                                    this.mimicPlayerUuid));
                            } catch (IllegalArgumentException ignored) {
                            }
                        });
    }
}
