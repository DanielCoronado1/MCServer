package com.dolfin.zombies.kinds;

import com.dolfin.zombies.*;
import com.dolfin.zombies.custody.*;
import com.dolfin.zombies.custody.CustodyLedger.Holder;
import com.google.gson.*;

import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.tags.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;

import java.util.*;

/**
 * Issue #7: Ladrón zombie (`ladron_zombie`). Steals a single item from a player's hand/hotbar on
 * melee hit, displays it in main hand, flees at `velocidadHuida`, never despawns or burns in sun
 * while carrying an item, and drops the stolen item after `minutosParaSoltar` (or on death),
 * alerting the owner.
 */
public final class LadronZombie extends ConfiguredZombie {
    private ItemAccounting.Packet<ItemStack> stolenPacket;
    private long stealDeadlineTick = -1;
    private boolean released;

    public LadronZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level, ZombieKind.LADRON);
        setCanBreakDoors(false);
    }

    public boolean carryingStolenItem() {
        return stolenPacket != null && !stolenPacket.items().isEmpty() && !released;
    }

    public Optional<ItemAccounting.Packet<ItemStack>> stolenPacket() {
        return Optional.ofNullable(stolenPacket);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(
                1,
                new AvoidEntityGoal<>(
                        this, Player.class, 16f, 1.0, 1.25, living -> carryingStolenItem()) {
                    @Override
                    public boolean canUse() {
                        return carryingStolenItem() && super.canUse();
                    }

                    @Override
                    public boolean canContinueToUse() {
                        return carryingStolenItem() && super.canContinueToUse();
                    }
                });
    }

    @Override
    public boolean canBreakDoors() {
        return false;
    }

    @Override
    public void setCanBreakDoors(boolean canBreak) {
        super.setCanBreakDoors(false);
    }

    @Override
    protected boolean isSunSensitive() {
        return !carryingStolenItem() && super.isSunSensitive();
    }

    @Override
    public boolean removeWhenFarAway(double distSq) {
        return !carryingStolenItem() && super.removeWhenFarAway(distSq);
    }

    @Override
    public boolean isPersistenceRequired() {
        return carryingStolenItem() || super.isPersistenceRequired();
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof ServerPlayer player && !carryingStolenItem()) {
            trySteal(level, player);
        }
        return hit;
    }

    private void trySteal(ServerLevel level, ServerPlayer player) {
        if (!abilityEnabled("robar")) {
            DolfinZombies.LOGGER.debug(
                    "event=ladron_steal_skipped uuid={} player={} reason=ability_disabled",
                    getUUID(),
                    player.getUUID());
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            DolfinZombies.LOGGER.debug(
                    "event=ladron_steal_skipped uuid={} player={} reason=gamemode",
                    getUUID(),
                    player.getUUID());
            return;
        }
        JsonObject cfg = robarConfig();
        double chance = cfg.get("probabilidadRobo").getAsDouble();
        double roll = level.getRandom().nextDouble();
        if (roll >= chance) {
            DolfinZombies.LOGGER.debug(
                    "event=ladron_steal_skipped uuid={} player={} reason=roll chance={} roll={}",
                    getUUID(),
                    player.getUUID(),
                    chance,
                    roll);
            return;
        }
        List<String> blacklist = blacklist(cfg);
        boolean hotbarOnly = cfg.get("soloHotbar").getAsBoolean();
        var inv = player.getInventory();
        int mainSize = inv.getNonEquipmentItems().size();
        List<Boolean> eligible = new ArrayList<>(mainSize);
        for (int i = 0; i < mainSize; i++) {
            ItemStack s = inv.getItem(i);
            eligible.add(
                    !s.isEmpty()
                            && !ItemAccounting.matchesSelector(
                                    BuiltInRegistries.ITEM.getKey(s.getItem()).toString(),
                                    tag ->
                                            s.is(
                                                    TagKey.create(
                                                            Registries.ITEM,
                                                            Identifier.parse(tag))),
                                    blacklist));
        }
        int slot =
                ItemAccounting.stealSlot(
                        eligible,
                        inv.getSelectedSlot(),
                        9,
                        hotbarOnly,
                        bound -> level.getRandom().nextInt(bound));
        if (slot < 0) {
            DolfinZombies.LOGGER.debug(
                    "event=ladron_steal_skipped uuid={} player={} reason=no_eligible_item",
                    getUUID(),
                    player.getUUID());
            return;
        }
        ItemStack targetStack = inv.getItem(slot);
        if (targetStack.isEmpty()) return;
        ItemStack copy = targetStack.copy();
        UUID custodyId = UUID.randomUUID();
        var packet =
                new ItemAccounting.Packet<>(
                        custodyId, player.getUUID(), player.getName().getString(), List.of(copy));
        var mgr = CustodyManager.get(level.getServer());
        var openRes =
                mgr.openCustody(
                        packet,
                        Holder.THIEF,
                        getUUID(),
                        kind().id(),
                        level,
                        blockPosition(),
                        "ladron_steal");
        if (!openRes.ok()) {
            DolfinZombies.LOGGER.warn(
                    "event=ladron_steal_rejected uuid={} player={} reason={}",
                    getUUID(),
                    player.getUUID(),
                    openRes.reason());
            return;
        }
        inv.setItem(slot, ItemStack.EMPTY);
        inv.setChanged();
        this.stolenPacket = packet;
        this.released = false;
        int minutes = Math.max(1, cfg.get("minutosParaSoltar").getAsInt());
        this.stealDeadlineTick = level.getGameTime() + minutes * 1200L;
        equipVisualAndFleeSpeed(copy, cfg.get("velocidadHuida").getAsDouble());
        setTarget(null);
        playStealSound();
        CustodyManager.notifyOwner(
                level.getServer(),
                player.getUUID(),
                Component.translatable(
                        "message.dolfinzombies.ladron_stole",
                        copy.getHoverName(),
                        blockPosition().getX(),
                        blockPosition().getY(),
                        blockPosition().getZ()));
    }

    private void equipVisualAndFleeSpeed(ItemStack visual, double fleeSpeed) {
        setPersistenceRequired();
        setCanBreakDoors(false);
        setItemSlot(EquipmentSlot.MAINHAND, visual.copy());
        setDropChance(EquipmentSlot.MAINHAND, 0f);
        var speedAttr = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(.23 * fleeSpeed);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!carryingStolenItem()) return;
        if (getTarget() != null) setTarget(null);
        if (tickCount % 20 == 0) {
            CustodyManager.get(level.getServer())
                    .trackEntity(stolenPacket.custody(), level, blockPosition());
        }
        if (stealDeadlineTick > 0 && level.getGameTime() >= stealDeadlineTick) {
            releaseStolen(level, false, "timeout_10m", true);
        }
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl && carryingStolenItem()) {
            boolean unsafe =
                    source.is(DamageTypes.FELL_OUT_OF_WORLD)
                            || source.is(DamageTypes.LAVA)
                            || source.is(DamageTypeTags.IS_FIRE)
                            || isInLava()
                            || getY() < sl.getMinY() + 1;
            releaseStolen(sl, unsafe, "ladron_death", false);
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sl && carryingStolenItem() && reason.shouldDestroy()) {
            boolean unsafe =
                    reason == RemovalReason.KILLED && (isInLava() || getY() < sl.getMinY() + 1);
            releaseStolen(
                    sl, unsafe, "ladron_removed_" + reason.name().toLowerCase(Locale.ROOT), false);
        }
        super.remove(reason);
    }

    private void releaseStolen(
            ServerLevel level, boolean unsafe, String reason, boolean notifyTimeout) {
        if (!carryingStolenItem()) return;
        var packet = this.stolenPacket;
        this.released = true;
        this.stolenPacket = null;
        this.stealDeadlineTick = -1;
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        double normalSpeed = settings().get("velocidad").getAsDouble();
        var speedAttr = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(.23 * normalSpeed);
        CustodyManager.get(level.getServer())
                .releaseToGroundOrMailbox(
                        packet, Holder.THIEF, level, blockPosition(), unsafe, reason);
        if (notifyTimeout && !unsafe && !packet.items().isEmpty()) {
            CustodyManager.notifyOwner(
                    level.getServer(),
                    packet.owner(),
                    Component.translatable(
                            "message.dolfinzombies.ladron_dropped",
                            packet.items().getFirst().getHoverName(),
                            blockPosition().getX(),
                            blockPosition().getY(),
                            blockPosition().getZ()));
        }
    }

    private JsonObject robarConfig() {
        var hab = settings().getAsJsonObject("habilidades");
        return hab != null && hab.has("robar")
                ? hab.getAsJsonObject("robar")
                : ZombieKind.LADRON
                        .defaults()
                        .getAsJsonObject("habilidades")
                        .getAsJsonObject("robar");
    }

    private static List<String> blacklist(JsonObject cfg) {
        if (!cfg.has("listaNegra") || !cfg.get("listaNegra").isJsonArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonElement e : cfg.getAsJsonArray("listaNegra"))
            if (e.isJsonPrimitive()) out.add(e.getAsString());
        return out;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (carryingStolenItem()) {
            out.store("DolfinStolenPacket", CustodyManager.PACKET_CODEC, stolenPacket);
            out.putLong("DolfinStealDeadline", stealDeadlineTick);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        setCanBreakDoors(false);
        in.read("DolfinStolenPacket", CustodyManager.PACKET_CODEC)
                .ifPresent(
                        pkt -> {
                            this.stolenPacket = pkt;
                            this.released = false;
                            this.stealDeadlineTick = in.getLongOr("DolfinStealDeadline", -1L);
                            if (!pkt.items().isEmpty()) {
                                equipVisualAndFleeSpeed(
                                        pkt.items().getFirst(),
                                        robarConfig().get("velocidadHuida").getAsDouble());
                            }
                            DolfinZombies.LOGGER.info(
                                    "event=ladron_custody_restored uuid={} custody={} owner={}"
                                            + " items={}",
                                    getUUID(),
                                    pkt.custody(),
                                    pkt.owner(),
                                    ItemAccounting.countSummary(
                                            CustodyManager.STACKS, pkt.items()));
                        });
    }
}
