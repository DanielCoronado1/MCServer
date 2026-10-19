package com.dolfin.space;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * Planet Kepler dimension keys, low-gravity attribute modifier lifecycle, weather suppression,
 * and natural mob spawn filtering.
 */
public final class KeplerDimension {
    public static final Identifier ID = DolfinSpace.id("kepler");
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ID);
    public static final ResourceKey<DimensionType> DIMENSION_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, ID);
    public static final ResourceKey<LevelStem> LEVEL_STEM = ResourceKey.create(Registries.LEVEL_STEM, ID);
    public static final ResourceKey<NoiseGeneratorSettings> NOISE_SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS, ID);
    public static final ResourceKey<Biome> BIOME_KEPLER_PLAINS = ResourceKey.create(Registries.BIOME, DolfinSpace.id("kepler_plains"));

    public static final Identifier GRAVITY_MODIFIER_ID = DolfinSpace.id("kepler_gravity");
    public static final Identifier FALL_DAMAGE_MODIFIER_ID = DolfinSpace.id("kepler_fall_damage");
    public static final Identifier SAFE_FALL_MODIFIER_ID = DolfinSpace.id("kepler_safe_fall");

    private KeplerDimension() {}

    /** Registers server events for Kepler low gravity and weather control. */
    public static void init() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof LivingEntity living) {
                syncGravity(living, level, "entity_load");
            }
        });

        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
                syncGravity(player, destination, "dimension_change_from_" + origin.dimension().identifier()));

        ServerEntityLevelChangeEvents.AFTER_ENTITY_CHANGE_LEVEL.register((originalEntity, newEntity, origin, destination) -> {
            if (originalEntity instanceof LivingEntity oldLiving) {
                removeGravityModifiers(oldLiving, "entity_dimension_leave");
            }
            if (newEntity instanceof LivingEntity newLiving) {
                syncGravity(newLiving, destination, "entity_dimension_enter");
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            syncGravity(player, player.level(), "player_join");
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                removeGravityModifiers(handler.getPlayer(), "player_disconnect"));

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) ->
                removeGravityModifiers(entity, "entity_death"));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            removeGravityModifiers(oldPlayer, "player_respawn_old");
            syncGravity(newPlayer, newPlayer.level(), "player_respawn_new");
        });

        ServerTickEvents.START_LEVEL_TICK.register(KeplerDimension::tickKeplerLevel);
    }

    /** Synchronizes Kepler low-gravity attribute modifiers on a living entity for its current level. */
    public static void syncGravity(LivingEntity entity, ServerLevel level, String reason) {
        SpaceConfig.Snapshot snapshot = DolfinSpace.config().current();
        if (!snapshot.general().activo() || !entity.isAlive() || !isKepler(level.dimension())) {
            removeGravityModifiers(entity, reason);
            return;
        }
        applyGravityModifiers(entity, snapshot.dimension(), reason);
    }

    /** Applies or updates Kepler low-gravity, fall-damage, and safe-fall modifiers on the entity. */
    public static void applyGravityModifiers(LivingEntity entity, SpaceConfig.DimensionSection dim, String reason) {
        double gravityAmount = gravityModifierAmount(dim.multiplicadorGravedad());
        double fallDamageAmount = fallDamageModifierAmount(dim.multiplicadorDanioCaida());
        double safeFallAmount = safeFallModifierAmount(dim.multiplicadorDistanciaSeguraCaida());

        boolean changed = updateTransientModifier(entity, Attributes.GRAVITY, GRAVITY_MODIFIER_ID, gravityAmount);
        changed |= updateTransientModifier(entity, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_DAMAGE_MODIFIER_ID, fallDamageAmount);
        changed |= updateTransientModifier(entity, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_MODIFIER_ID, safeFallAmount);

        if (changed) {
            if (entity instanceof ServerPlayer player) {
                DolfinSpace.LOGGER.info(
                        "event=kepler_gravity_applied player={} uuid={} reason={} gravityMultiplier={} fallDamageMultiplier={} safeFallMultiplier={}",
                        player.getName().getString(),
                        player.getUUID(),
                        reason,
                        dim.multiplicadorGravedad(),
                        dim.multiplicadorDanioCaida(),
                        dim.multiplicadorDistanciaSeguraCaida()
                );
            } else {
                DolfinSpace.LOGGER.debug(
                        "event=kepler_gravity_applied entity={} uuid={} reason={} gravityMultiplier={}",
                        entity.getType(),
                        entity.getUUID(),
                        reason,
                        dim.multiplicadorGravedad()
                );
            }
        }
    }

    /** Removes all Kepler gravity and fall modifiers from the entity if present. */
    public static void removeGravityModifiers(LivingEntity entity, String reason) {
        boolean removed = removeModifier(entity, Attributes.GRAVITY, GRAVITY_MODIFIER_ID);
        removed |= removeModifier(entity, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_DAMAGE_MODIFIER_ID);
        removed |= removeModifier(entity, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_MODIFIER_ID);

        if (removed) {
            if (entity instanceof ServerPlayer player) {
                DolfinSpace.LOGGER.info(
                        "event=kepler_gravity_removed player={} uuid={} reason={} dimension={}",
                        player.getName().getString(),
                        player.getUUID(),
                        reason,
                        player.level().dimension().identifier()
                );
            } else {
                DolfinSpace.LOGGER.debug(
                        "event=kepler_gravity_removed entity={} uuid={} reason={}",
                        entity.getType(),
                        entity.getUUID(),
                        reason
                );
            }
        }
    }

    /** Returns true if the given level key matches the configured Kepler dimension. */
    public static boolean isKepler(ResourceKey<Level> dimension) {
        return dimension != null && isKeplerId(dimension.identifier().toString());
    }

    /** Returns true if the given dimension identifier string refers to Kepler. */
    public static boolean isKeplerId(String dimensionId) {
        if (dimensionId == null) {
            return false;
        }
        SpaceConfig cfg = DolfinSpace.config();
        String configured = cfg != null ? cfg.current().dimension().idDimension() : ID.toString();
        return ID.toString().equals(dimensionId) || configured.equals(dimensionId);
    }

    /** Returns true if an entity type identifier is allowed to spawn naturally on Kepler. */
    public static boolean isAllowedKeplerNaturalSpawnId(String entityTypeId) {
        return entityTypeId != null && entityTypeId.startsWith(DolfinSpace.MOD_ID + ":");
    }

    /** Pure calculation of the {@code ADD_MULTIPLIED_TOTAL} amount for a gravity multiplier. */
    public static double gravityModifierAmount(double multiplier) {
        double clamped = Math.clamp(multiplier, 0.05, 2.0);
        return clamped - 1.0;
    }

    /** Pure calculation of the {@code ADD_MULTIPLIED_TOTAL} amount for a fall-damage multiplier. */
    public static double fallDamageModifierAmount(double multiplier) {
        double clamped = Math.clamp(multiplier, 0.0, 2.0);
        return clamped - 1.0;
    }

    /** Pure calculation of the {@code ADD_MULTIPLIED_TOTAL} amount for a safe-fall distance multiplier. */
    public static double safeFallModifierAmount(double multiplier) {
        double clamped = Math.clamp(multiplier, 1.0, 10.0);
        return clamped - 1.0;
    }

    private static void tickKeplerLevel(ServerLevel level) {
        if (!isKepler(level.dimension())) {
            return;
        }
        SpaceConfig.Snapshot snapshot = DolfinSpace.config().current();
        if (snapshot.dimension().sinLluvia() && (level.isRaining() || level.isThundering())) {
            level.resetWeatherCycle();
            DolfinSpace.LOGGER.info("event=kepler_weather_cleared dimension={}", level.dimension().identifier());
        }
        for (ServerPlayer player : level.players()) {
            if (player.isAlive()) {
                applyGravityModifiers(player, snapshot.dimension(), "tick_sync");
            }
        }
    }

    private static boolean updateTransientModifier(
            LivingEntity entity,
            Holder<Attribute> attribute,
            Identifier modifierId,
            double amount
    ) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return false;
        }
        AttributeModifier existing = instance.getModifier(modifierId);
        if (existing != null && Math.abs(existing.amount() - amount) < 1.0E-6) {
            return false;
        }
        instance.addOrUpdateTransientModifier(
                new AttributeModifier(modifierId, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
        );
        return true;
    }

    private static boolean removeModifier(
            LivingEntity entity,
            Holder<Attribute> attribute,
            Identifier modifierId
    ) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null || !instance.hasModifier(modifierId)) {
            return false;
        }
        return instance.removeModifier(modifierId);
    }
}
