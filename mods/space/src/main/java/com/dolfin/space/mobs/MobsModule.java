package com.dolfin.space.mobs;

import com.dolfin.space.*;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.*;
import net.minecraft.world.level.levelgen.Heightmap;

public final class MobsModule {
    public static final EntityType<Parasite> PARASITE=mob("parasito",Parasite::new,Spider::createAttributes,.5F,.5F,false);
    public static final EntityType<Stalker> STALKER=mob("acechador",Stalker::new,Spider::createAttributes,1.2F,.8F,true);
    public static final EntityType<Worm> WORM=mob("gusano",Worm::new,Silverfish::createAttributes,.9F,.5F,false);
    public static final EntityType<Sentinel> SENTINEL=mob("centinela",Sentinel::new,Ghast::createAttributes,1.0F,1.0F,false);
    public static final EntityType<SpaceZombie> ZOMBIE=mob("zombi_espacial",SpaceZombie::new,Zombie::createAttributes,.6F,1.95F,false);
    public static final EntityType<SpaceSkeleton> SKELETON=mob("esqueleto_espacial",SpaceSkeleton::new,Skeleton::createAttributes,.6F,1.99F,false);
    public static final EntityType<SpaceCreeper> CREEPER=mob("creeper_espacial",SpaceCreeper::new,Creeper::createAttributes,.6F,1.7F,false);
    public static final EntityType<EnergyProjectile> PROJECTILE=entity("proyectil_energia",EntityType.Builder.of(EnergyProjectile::new,MobCategory.MISC).sized(.25F,.25F).clientTrackingRange(8));
    public static final EntityType<CrystalArrow> CRYSTAL_ARROW=entity("flecha_cristal",EntityType.Builder.of(CrystalArrow::new,MobCategory.MISC).sized(.5F,.5F).clientTrackingRange(8));
    public static final EntityType<AcidCloud> ACID_CLOUD=entity("nube_acida",EntityType.Builder.of(AcidCloud::new,MobCategory.MISC).sized(6,.5F).clientTrackingRange(10));
    public static final Item CHITIN=SpaceContent.registerItem("quitina_alienigena",Item::new,new Item.Properties());
    public static final Item TOOTH=SpaceContent.registerItem("diente_gusano",Item::new,new Item.Properties());
    public static final Item CORE=SpaceContent.registerItem("nucleo_energia",Item::new,new Item.Properties());
    public static final ResourceKey<DamageType> PARASITE_DAMAGE=ResourceKey.create(Registries.DAMAGE_TYPE,DolfinSpace.id("parasito"));
    private MobsModule() {}
    private static <T extends Mob> EntityType<T> mob(String id, EntityType.EntityFactory<T> factory, Supplier<AttributeSupplier.Builder> stats, float width, float height, boolean night) {
        var builder=FabricEntityType.Builder.createMob(factory,MobCategory.MONSTER,b -> b.defaultAttributes(stats).spawnPlacement(SpawnPlacementTypes.ON_GROUND,Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,(type,level,reason,pos,random) ->
            DolfinSpace.config().current().general().activo() && KeplerDimension.isKepler(level.getLevel().dimension()) && (!night || FaunaRules.night(level.getLevel().getDefaultClockTime())) && level.getDifficulty()!=net.minecraft.world.Difficulty.PEACEFUL && Mob.checkMobSpawnRules(type,level,reason,pos,random) && (!id.equals("centinela") || java.util.stream.IntStream.rangeClosed(0,10).allMatch(i -> level.isEmptyBlock(pos.above(i))))));
        EntityType<T> type=entity(id,builder.sized(width,height).eyeHeight(height*.8F).clientTrackingRange(8));
        SpaceContent.registerItem(id+"_spawn_egg",SpawnEggItem::new,new Item.Properties().spawnEgg(type));
        return type;
    }
    private static <T extends Entity> EntityType<T> entity(String id, EntityType.Builder<T> builder) {
        var key=ResourceKey.create(Registries.ENTITY_TYPE,DolfinSpace.id(id));return Registry.register(BuiltInRegistries.ENTITY_TYPE,key,builder.build(key));
    }
    public static DamageSource parasiteDamage(ServerLevel level, Parasite parasite) { return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(PARASITE_DAMAGE),parasite); }
    public static void init() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,server) ->
            java.util.List.copyOf(handler.player.getPassengers()).forEach(e -> { if (e instanceof Parasite parasite) parasite.detach("disconnect"); }));
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity,source) -> {
            if (entity instanceof net.minecraft.world.entity.player.Player)
                java.util.List.copyOf(entity.getPassengers()).forEach(e -> { if (e instanceof Parasite parasite) parasite.detach("victim_death"); });
        });
        // Initialize attachment before any loaded Kepler chunk receives mutations.
        var provenance=TerrainHistory.PLACED;
        DolfinSpace.LOGGER.info("event=fauna_registered mobs={} provenance={} claims=opac_optional",FaunaRules.MOBS,provenance.identifier());
    }
}
