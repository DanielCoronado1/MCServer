package com.dolfin.zombies.kinds;

import com.dolfin.zombies.*;
import com.dolfin.zombies.custody.*;
import com.dolfin.zombies.custody.CustodyLedger.Holder;
import com.google.gson.JsonObject;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.*;

import java.util.*;

/**
 * Issue #7: Contagiado zombie (`contagiado_zombie`). Applies the `dolfinzombies:infeccion` effect
 * on melee hit. When an infected player dies, spawns a converted Contagiado bearing the player's
 * name, skin profile, equipped armor, and full inventory in persisted custody. Upon the converted
 * zombie's death, places a protected single/double chest (or falls back to ground/mailbox if
 * blocked/disabled).
 */
public final class ContagiadoZombie extends ConfiguredZombie {
    private static final EntityDataAccessor<String> DATA_CONVERTED_NAME =
            SynchedEntityData.defineId(ContagiadoZombie.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_CONVERTED_UUID =
            SynchedEntityData.defineId(ContagiadoZombie.class, EntityDataSerializers.STRING);

    private ItemAccounting.Packet<ItemStack> custodyPacket;
    private UUID convertedOwner;
    private String convertedOwnerName = "";
    private boolean released;

    public ContagiadoZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level, ZombieKind.CONTAGIADO);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CONVERTED_NAME, "");
        builder.define(DATA_CONVERTED_UUID, "");
    }

    public boolean isConverted() {
        return convertedOwner != null || !entityData.get(DATA_CONVERTED_UUID).isEmpty();
    }

    public boolean holdingCustody() {
        return custodyPacket != null && !custodyPacket.items().isEmpty() && !released;
    }

    public Optional<ResolvableProfile> convertedProfile() {
        String uuidStr = entityData.get(DATA_CONVERTED_UUID);
        String name = entityData.get(DATA_CONVERTED_NAME);
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

    @Override
    protected boolean isSunSensitive() {
        return !isConverted() && !holdingCustody() && super.isSunSensitive();
    }

    @Override
    public boolean removeWhenFarAway(double distSq) {
        return !isConverted() && !holdingCustody() && super.removeWhenFarAway(distSq);
    }

    @Override
    public boolean isPersistenceRequired() {
        return isConverted() || holdingCustody() || super.isPersistenceRequired();
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof ServerPlayer player) {
            tryInfect(level, player);
        }
        return hit;
    }

    private void tryInfect(ServerLevel level, ServerPlayer player) {
        if (!infectionEnabled()) {
            DolfinZombies.LOGGER.debug(
                    "event=contagiado_infect_skipped uuid={} player={} reason=ability_disabled",
                    getUUID(),
                    player.getUUID());
            return;
        }
        if (player.isCreative() || player.isSpectator()) return;
        JsonObject cfg = infectionConfig();
        double chance = cfg.get("probabilidadInfeccion").getAsDouble();
        double roll = level.getRandom().nextDouble();
        if (roll >= chance) {
            DolfinZombies.LOGGER.debug(
                    "event=contagiado_infect_skipped uuid={} player={} reason=roll chance={}"
                            + " roll={}",
                    getUUID(),
                    player.getUUID(),
                    chance,
                    roll);
            return;
        }
        int duration = cfg.get("duracionInfeccion").getAsInt();
        InfectionEffect.infect(player, duration);
    }

    /**
     * Called from ServerPlayer.die Mixin BEFORE vanilla `dropAllDeathLoot` when a player dies while
     * infected. Captures the player's inventory (unless keepInventory is true) and spawns the
     * converted Contagiado.
     */
    public static void handleInfectedPlayerDeath(ServerPlayer player, DamageSource source) {
        if (player.isSpectator() || player.isCreative() || !InfectionEffect.isInfected(player))
            return;
        ServerLevel level = player.level();
        InfectionEffect.clearTracking(player.getUUID());
        var snap = DolfinZombies.config().current();
        if (!snap.enabled(ZombieKind.CONTAGIADO)
                || !conversionEnabled(snap.zombie(ZombieKind.CONTAGIADO))) {
            DolfinZombies.LOGGER.info(
                    "event=contagiado_conversion_skipped player={} reason=ability_disabled",
                    player.getUUID());
            return;
        }
        var entity =
                DolfinZombies.type(ZombieKind.CONTAGIADO)
                        .create(level, EntitySpawnReason.CONVERSION);
        if (!(entity instanceof ContagiadoZombie converted)) return;
        converted.snapTo(
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        converted.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(player.blockPosition()),
                EntitySpawnReason.CONVERSION,
                null);

        boolean keepInv = level.getGameRules().get(GameRules.KEEP_INVENTORY);
        List<ItemStack> visualArmor =
                List.of(
                        player.getItemBySlot(EquipmentSlot.FEET).copy(),
                        player.getItemBySlot(EquipmentSlot.LEGS).copy(),
                        player.getItemBySlot(EquipmentSlot.CHEST).copy(),
                        player.getItemBySlot(EquipmentSlot.HEAD).copy(),
                        player.getItemBySlot(EquipmentSlot.MAINHAND).copy(),
                        player.getItemBySlot(EquipmentSlot.OFFHAND).copy());
        ItemAccounting.Packet<ItemStack> packet = null;
        List<Integer> capturedSlots = List.of();
        Inventory inv = player.getInventory();
        if (!keepInv) {
            int size = inv.getContainerSize();
            List<ItemStack> snapshot = new ArrayList<>(size);
            for (int i = 0; i < size; i++) snapshot.add(inv.getItem(i));
            capturedSlots =
                    ItemAccounting.capture(
                            CustodyManager.STACKS,
                            snapshot,
                            s ->
                                    EnchantmentHelper.has(
                                            s, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP));
            if (!capturedSlots.isEmpty()) {
                List<ItemStack> held = new ArrayList<>(capturedSlots.size());
                for (int idx : capturedSlots) held.add(snapshot.get(idx).copy());
                packet =
                        new ItemAccounting.Packet<>(
                                UUID.randomUUID(),
                                player.getUUID(),
                                player.getName().getString(),
                                held);
            }
        }
        converted.configureConverted(
                player.getUUID(), player.getName().getString(), visualArmor, packet);
        converted.setPlayerAppearance(ResolvableProfile.createResolved(player.getGameProfile()));
        if (!level.addFreshEntity(converted)) {
            DolfinZombies.LOGGER.error(
                    "event=contagiado_conversion_spawn_failed player={} pos={}",
                    player.getUUID(),
                    player.blockPosition());
            return;
        }
        if (packet != null) {
            var mgr = CustodyManager.get(level.getServer());
            var res =
                    mgr.openCustody(
                            packet,
                            Holder.INFECTED,
                            converted.getUUID(),
                            ZombieKind.CONTAGIADO.id(),
                            level,
                            converted.blockPosition(),
                            "infected_player_death");
            if (res.ok()) {
                for (int idx : capturedSlots) inv.setItem(idx, ItemStack.EMPTY);
                inv.setChanged();
            } else {
                converted.custodyPacket = null;
                DolfinZombies.LOGGER.error(
                        "event=contagiado_custody_open_rejected player={} reason={}",
                        player.getUUID(),
                        res.reason());
            }
        }
        DolfinZombies.LOGGER.info(
                "event=contagiado_converted player={} playerName={} entity={} keepInventory={}"
                        + " items={}",
                player.getUUID(),
                player.getName().getString(),
                converted.getUUID(),
                keepInv,
                packet == null
                        ? "count:0,stacks:0"
                        : ItemAccounting.countSummary(CustodyManager.STACKS, packet.items()));
    }

    public void configureConverted(
            UUID ownerUuid,
            String ownerName,
            List<ItemStack> visualEquipment,
            ItemAccounting.Packet<ItemStack> packet) {
        this.convertedOwner = ownerUuid;
        this.convertedOwnerName = ownerName == null ? "" : ownerName;
        if (ownerUuid != null) setPlayerAppearance(ResolvableProfile.createUnresolved(ownerUuid));
        this.custodyPacket = packet;
        this.released = false;
        this.entityData.set(DATA_CONVERTED_UUID, ownerUuid == null ? "" : ownerUuid.toString());
        this.entityData.set(DATA_CONVERTED_NAME, this.convertedOwnerName);
        setPersistenceRequired();
        if (!this.convertedOwnerName.isEmpty()) {
            setCustomName(Component.literal(this.convertedOwnerName));
            setCustomNameVisible(true);
        }
        EquipmentSlot[] slots = {
            EquipmentSlot.FEET,
            EquipmentSlot.LEGS,
            EquipmentSlot.CHEST,
            EquipmentSlot.HEAD,
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND
        };
        for (int i = 0; i < slots.length && i < visualEquipment.size(); i++) {
            ItemStack eq = visualEquipment.get(i);
            if (eq != null && !eq.isEmpty()) {
                setItemSlot(slots[i], eq.copy());
            }
            setDropChance(slots[i], 0f);
        }
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        if (isConverted() || holdingCustody()) return;
        super.dropEquipment(level);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (holdingCustody() && tickCount % 20 == 0) {
            CustodyManager.get(level.getServer())
                    .trackEntity(custodyPacket.custody(), level, blockPosition());
        }
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl && holdingCustody()) {
            boolean unsafe =
                    source.is(DamageTypes.FELL_OUT_OF_WORLD)
                            || source.is(DamageTypes.LAVA)
                            || source.is(DamageTypeTags.IS_FIRE)
                            || isInLava()
                            || getY() < sl.getMinY() + 1;
            releaseCustodyOnDeath(sl, unsafe, "contagiado_killed");
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sl && holdingCustody() && reason.shouldDestroy()) {
            boolean unsafe =
                    reason == RemovalReason.KILLED && (isInLava() || getY() < sl.getMinY() + 1);
            releaseCustodyOnDeath(
                    sl, unsafe, "contagiado_removed_" + reason.name().toLowerCase(Locale.ROOT));
        }
        super.remove(reason);
    }

    private void releaseCustodyOnDeath(ServerLevel level, boolean unsafe, String reason) {
        if (!holdingCustody()) return;
        var packet = this.custodyPacket;
        this.released = true;
        this.custodyPacket = null;
        JsonObject chestCfg = chestConfig();
        boolean chestActive = chestEnabled() && chestCfg.get("activo").getAsBoolean();
        int protectMin =
                chestCfg.has("minutosProteccion")
                        ? chestCfg.get("minutosProteccion").getAsInt()
                        : 15;
        int radius = chestCfg.has("radioBusqueda") ? chestCfg.get("radioBusqueda").getAsInt() : 5;
        CustodyManager.get(level.getServer())
                .releaseToChestOrFallback(
                        packet,
                        level,
                        blockPosition(),
                        chestActive,
                        radius,
                        protectMin,
                        unsafe,
                        reason);
    }

    private boolean infectionEnabled() {
        var snap = DolfinZombies.config().current();
        if (!snap.enabled(ZombieKind.CONTAGIADO)) return false;
        var hab = snap.zombie(ZombieKind.CONTAGIADO).getAsJsonObject("habilidades");
        if (hab.has("infeccion") && hab.get("infeccion").isJsonObject()) {
            var inf = hab.getAsJsonObject("infeccion");
            if (inf.has("activo") && !inf.get("activo").getAsBoolean()) return false;
        }
        return snap.abilityEnabled(ZombieKind.CONTAGIADO, "convertirAlMorir");
    }

    private static boolean conversionEnabled(JsonObject zombieJson) {
        var hab = zombieJson.getAsJsonObject("habilidades");
        if (!hab.has("convertirAlMorir") || !hab.get("convertirAlMorir").isJsonObject())
            return false;
        var conv = hab.getAsJsonObject("convertirAlMorir");
        if (conv.has("activo") && !conv.get("activo").getAsBoolean()) return false;
        if (conv.has("convertirAlMorir") && conv.get("convertirAlMorir").isJsonObject()) {
            var nested = conv.getAsJsonObject("convertirAlMorir");
            if (nested.has("activo") && !nested.get("activo").getAsBoolean()) return false;
        }
        return true;
    }

    private boolean chestEnabled() {
        var snap = DolfinZombies.config().current();
        if (!snap.enabled(ZombieKind.CONTAGIADO)) return false;
        var hab = snap.zombie(ZombieKind.CONTAGIADO).getAsJsonObject("habilidades");
        if (hab.has("cofre") && hab.get("cofre").isJsonObject()) {
            var c = hab.getAsJsonObject("cofre");
            if (c.has("activo") && !c.get("activo").getAsBoolean()) return false;
        }
        var conv = hab.getAsJsonObject("convertirAlMorir");
        if (conv != null && conv.has("cofre") && conv.get("cofre").isJsonObject()) {
            var c = conv.getAsJsonObject("cofre");
            if (c.has("activo") && !c.get("activo").getAsBoolean()) return false;
        }
        return true;
    }

    private JsonObject infectionConfig() {
        var hab = settings().getAsJsonObject("habilidades");
        if (hab != null && hab.has("convertirAlMorir"))
            return hab.getAsJsonObject("convertirAlMorir");
        return ZombieKind.CONTAGIADO
                .defaults()
                .getAsJsonObject("habilidades")
                .getAsJsonObject("convertirAlMorir");
    }

    private JsonObject chestConfig() {
        var hab =
                DolfinZombies.config()
                        .current()
                        .zombie(ZombieKind.CONTAGIADO)
                        .getAsJsonObject("habilidades");
        if (hab.has("cofre") && hab.get("cofre").isJsonObject())
            return hab.getAsJsonObject("cofre");
        var conv = hab.getAsJsonObject("convertirAlMorir");
        if (conv != null && conv.has("cofre") && conv.get("cofre").isJsonObject())
            return conv.getAsJsonObject("cofre");
        return ZombieKind.CONTAGIADO
                .defaults()
                .getAsJsonObject("habilidades")
                .getAsJsonObject("cofre");
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (convertedOwner != null) {
            out.putString("DolfinConvertedOwner", convertedOwner.toString());
            out.putString("DolfinConvertedName", convertedOwnerName);
        }
        if (holdingCustody()) {
            out.store("DolfinCustodyPacket", CustodyManager.PACKET_CODEC, custodyPacket);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.getString("DolfinConvertedOwner")
                .ifPresent(
                        raw -> {
                            try {
                                this.convertedOwner = UUID.fromString(raw);
                                this.convertedOwnerName = in.getStringOr("DolfinConvertedName", "");
                                this.entityData.set(DATA_CONVERTED_UUID, raw);
                                this.entityData.set(DATA_CONVERTED_NAME, this.convertedOwnerName);
                                if (!hasPlayerAppearance())
                                    setPlayerAppearance(
                                            ResolvableProfile.createUnresolved(
                                                    this.convertedOwner));
                                setPersistenceRequired();
                                for (EquipmentSlot slot : EquipmentSlot.values())
                                    setDropChance(slot, 0f);
                            } catch (IllegalArgumentException ignored) {
                            }
                        });
        in.read("DolfinCustodyPacket", CustodyManager.PACKET_CODEC)
                .ifPresent(
                        pkt -> {
                            this.custodyPacket = pkt;
                            this.released = false;
                            setPersistenceRequired();
                            for (EquipmentSlot slot : EquipmentSlot.values())
                                setDropChance(slot, 0f);
                            DolfinZombies.LOGGER.info(
                                    "event=contagiado_custody_restored uuid={} custody={} owner={}"
                                            + " items={}",
                                    getUUID(),
                                    pkt.custody(),
                                    pkt.owner(),
                                    ItemAccounting.countSummary(
                                            CustodyManager.STACKS, pkt.items()));
                        });
    }
}
