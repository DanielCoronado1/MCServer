package com.dolfin.creepers;

import com.dolfin.creepers.dungeon.Dungeons;
import com.dolfin.creepers.dungeon.jefe.ReyZombie;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

/** Registra las variantes de creeper: tipo de criatura, estadisticas y donde aparecen. */
public class DolfinCreepers implements ModInitializer {
    public static final String MOD_ID = "dolfincreepers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Map<Variante, EntityType<VarianteCreeper>> TIPOS =
            new EnumMap<>(Variante.class);
    public static final EntityType<ReyZombie> REY_ZOMBIE = registrarReyZombie();

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(REY_ZOMBIE, ReyZombie.crearAtributos());
        for (Variante variante : Variante.values()) {
            EntityType<VarianteCreeper> tipo = registrar(variante);
            TIPOS.put(variante, tipo);
            FabricDefaultAttributeRegistry.register(tipo, atributos(variante));
            SpawnPlacements.register(
                    tipo,
                    SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Monster::checkMonsterSpawnRules);
            if (variante.peso > 0) {
                BiomeModifications.addSpawn(
                        dondeAparece(variante),
                        MobCategory.MONSTER,
                        tipo,
                        variante.peso,
                        variante.grupoMin,
                        variante.grupoMax);
            }
        }
        LOGGER.info("DolfinCreepers: {} variantes de creeper registradas", TIPOS.size());
        Dungeons.init();
    }

    public static EntityType<VarianteCreeper> tipo(Variante variante) {
        return TIPOS.get(variante);
    }

    public static Identifier id(String ruta) {
        return Identifier.fromNamespaceAndPath(MOD_ID, ruta);
    }

    private static EntityType<ReyZombie> registrarReyZombie() {
        ResourceKey<EntityType<?>> clave =
                ResourceKey.create(Registries.ENTITY_TYPE, id("rey_zombie"));
        EntityType.Builder<ReyZombie> constructor =
                EntityType.Builder.of(ReyZombie::new, MobCategory.MONSTER);
        // La caja del diseño: el doble de alto que un zombie (escala 1.6 del modelo del skin).
        constructor.sized(1.2f, 3.8f).clientTrackingRange(10);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, clave, constructor.build(clave));
    }

    @SuppressWarnings("unchecked")
    private static EntityType<VarianteCreeper> registrar(Variante variante) {
        ResourceKey<EntityType<?>> clave =
                ResourceKey.create(Registries.ENTITY_TYPE, id(variante.id()));
        EntityType.Builder<VarianteCreeper> constructor =
                variante == Variante.REY
                        ? EntityType.Builder.of(
                                (tipo, mundo) -> new ReyCreeper(tipo, mundo), MobCategory.MONSTER)
                        : EntityType.Builder.of(
                                (tipo, mundo) -> new VarianteCreeper(tipo, mundo, variante),
                                MobCategory.MONSTER);
        // Caja a escala 1; el atributo SCALE de la variante la agranda o la achica.
        switch (variante) {
            case MINI -> constructor.sized(0.6f, 1.1f); // cabezón y de patas cortas
            case GIGANTE -> constructor.sized(1.0f, 2.3f); // patas de araña abiertas
            case REY -> constructor.sized(0.7f, 2.3f); // corona y capa
            default -> constructor.sized(0.6f, 1.7f);
        }
        constructor.clientTrackingRange(8);
        if (variante == Variante.FUEGO) {
            constructor.fireImmune();
        }
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, clave, constructor.build(clave));
    }

    private static AttributeSupplier.Builder atributos(Variante variante) {
        AttributeSupplier.Builder atributos =
                Creeper.createAttributes()
                        .add(Attributes.MAX_HEALTH, variante.vida)
                        .add(Attributes.SCALE, variante.escala)
                        .add(Attributes.MOVEMENT_SPEED, 0.25 * variante.velocidad);
        if (variante == Variante.REY) {
            atributos
                    .add(Attributes.ARMOR, 8)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 1)
                    .add(Attributes.FOLLOW_RANGE, 40);
        }
        return atributos;
    }

    /** Biomas donde aparece cada variante de forma natural. */
    private static Predicate<BiomeSelectionContext> dondeAparece(Variante variante) {
        return switch (variante) {
            case FUEGO ->
                    BiomeSelectors.foundInTheNether()
                            .or(
                                    BiomeSelectors.includeByKey(
                                            Biomes.DESERT,
                                            Biomes.BADLANDS,
                                            Biomes.ERODED_BADLANDS,
                                            Biomes.WOODED_BADLANDS,
                                            Biomes.SAVANNA_PLATEAU));
            case HIELO ->
                    BiomeSelectors.includeByKey(
                            Biomes.SNOWY_PLAINS,
                            Biomes.ICE_SPIKES,
                            Biomes.SNOWY_TAIGA,
                            Biomes.FROZEN_PEAKS,
                            Biomes.JAGGED_PEAKS,
                            Biomes.SNOWY_SLOPES,
                            Biomes.GROVE,
                            Biomes.FROZEN_RIVER,
                            Biomes.SNOWY_BEACH,
                            Biomes.FROZEN_OCEAN,
                            Biomes.DEEP_FROZEN_OCEAN);
            case TOXICO ->
                    BiomeSelectors.includeByKey(
                            Biomes.SWAMP,
                            Biomes.MANGROVE_SWAMP,
                            Biomes.DARK_FOREST,
                            Biomes.PALE_GARDEN,
                            Biomes.LUSH_CAVES);
            default -> BiomeSelectors.foundInOverworld();
        };
    }
}
