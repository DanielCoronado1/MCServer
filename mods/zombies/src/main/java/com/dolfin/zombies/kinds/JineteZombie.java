package com.dolfin.zombies.kinds;

import com.dolfin.zombies.ConfiguredZombie;
import com.dolfin.zombies.DolfinZombies;
import com.dolfin.zombies.ZombieKind;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;

/**
 * Spawns on a weighted mount (zombie horse or spider by default). The vanilla rule that a Mob
 * passenger drives its Mob vehicle makes the mount follow the zombie's navigation, so the spider
 * climbs and the horse chases with it. When the mount dies the zombie is simply dismounted and
 * keeps fighting on foot.
 */
public class JineteZombie extends ConfiguredZombie {
    private CombatParams.Jinete params;
    private Entity lastVehicle;

    public JineteZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level, ZombieKind.JINETE);
    }

    private CombatParams.Jinete params() {
        var p = params;
        if (p == null) params = p = CombatParams.Jinete.from(settings());
        return p;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        params = null;
    }

    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor access,
            DifficultyInstance difficulty,
            EntitySpawnReason reason,
            SpawnGroupData group) {
        var result = super.finalizeSpawn(access, difficulty, reason, group);
        params = null;
        if (abilityEnabled(CombatParams.Jinete.ABILITY)) mount(access.getLevel());
        else Abilities.debug(this, "jinete_mount_skipped", "reason=ability_off");
        return result;
    }

    private void mount(ServerLevel level) {
        if (isPassenger()) { // vanilla baby-zombie chicken jockey rolled by the superclass: a
            // Jinete only rides its own mount
            var vanilla = getVehicle();
            stopRiding();
            if (vanilla != null) vanilla.discard();
            Abilities.debug(
                    this,
                    "jinete_vanilla_mount_removed",
                    "vehicle={}",
                    vanilla == null ? "none" : vanilla.getType().toShortString());
        }
        var p = params();
        String pick = WeightedPick.pick(p.monturas(), random.nextDouble());
        if (pick == null) {
            Abilities.debug(
                    this, "jinete_mount_skipped", "reason=no_weights monturas={}", p.monturas());
            return;
        }
        Identifier id = Identifier.tryParse(pick.contains(":") ? pick : "minecraft:" + pick);
        EntityType<?> type =
                id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        Entity created = type == null ? null : type.create(level, EntitySpawnReason.JOCKEY);
        if (!(created instanceof Mob mount)) {
            DolfinZombies.LOGGER.warn(
                    "event=jinete_mount_invalid id={} uuid={} mount={} reason={}",
                    kind().id(),
                    getUUID(),
                    pick,
                    created == null ? "unknown_or_not_creatable" : "not_a_mob");
            if (created != null) created.discard();
            return;
        }
        mount.snapTo(getX(), getY(), getZ(), getYRot(), 0f);
        var health = mount.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(p.vidaMontura());
        mount.setHealth(mount.getMaxHealth());
        startRiding(mount, false, false);
        level.addFreshEntity(mount);
        lastVehicle = mount;
        Abilities.debug(
                this,
                "jinete_mount",
                "mount={} mountUuid={} mountHealth={} weights={}",
                pick,
                mount.getUUID(),
                mount.getMaxHealth(),
                p.monturas());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (isPassenger()) {
            lastVehicle = getVehicle();
        } else if (lastVehicle != null) {
            Abilities.debug(
                    this,
                    "jinete_dismounted",
                    "mount={} mountAlive={} reason={}",
                    lastVehicle.getType().toShortString(),
                    lastVehicle.isAlive(),
                    lastVehicle.isAlive() ? "dismounted" : "mount_died_or_removed");
            lastVehicle = null;
        }
    }
}
