package com.dolfin.zombies;

import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import org.slf4j.*;

public final class DolfinZombies implements ModInitializer {
    public static final String MOD_ID="dolfinzombies";
    public static final Logger LOGGER=LoggerFactory.getLogger(MOD_ID);
    private static final Map<ZombieKind,EntityType<ConfiguredZombie>> TYPES=new EnumMap<>(ZombieKind.class);
    private static ZombieConfig config;
    public static ZombieConfig config(){return config;}
    public static Identifier id(String path){return Identifier.fromNamespaceAndPath(MOD_ID,path);}
    public static EntityType<ConfiguredZombie> type(ZombieKind kind){return TYPES.get(kind);}
    @Override public void onInitialize(){
        config=new ZombieConfig(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID),LOGGER::warn);
        for(var kind:ZombieKind.values()){
            var key=ResourceKey.create(Registries.ENTITY_TYPE,id(kind.id()));
            var type=Registry.register(BuiltInRegistries.ENTITY_TYPE,key,EntityType.Builder.<ConfiguredZombie>of((t,l)->ZombieFactories.create(t,l,kind),MobCategory.MONSTER).sized(.6f,1.95f).clientTrackingRange(8).build(key));
            TYPES.put(kind,type);FabricDefaultAttributeRegistry.register(type,Zombie.createAttributes().add(Attributes.SCALE,1));
            var egg=ResourceKey.create(Registries.ITEM,id(kind.id()+"_spawn_egg"));
            Registry.register(BuiltInRegistries.ITEM,egg,new SpawnEggItem(new Item.Properties().setId(egg).spawnEgg(type)));
        }
        // Enforce global/variant switches for eggs and vanilla /summon as well as our admin command.
        ServerEntityEvents.ENTITY_LOAD.register((entity,level)->{
            if(entity instanceof ConfiguredZombie zombie&&!config.current().enabled(zombie.kind())){
                LOGGER.debug("event=zombie_disabled_discard id={} uuid={} dimension={} pos={}",zombie.kind().id(),zombie.getUUID(),level.dimension().identifier(),zombie.blockPosition());zombie.discard();
            }
        });
        ZombieSounds.init(); SpawnRules.init(); BloodMoon.init();
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->ZombieCommands.register(dispatcher));
        LOGGER.info("event=startup registeredEntities={} registeredEggs={} configRoot={} generation={} permissionProvider={} naturalSpawning=dynamic mechanics=deferred_issues_4_7",TYPES.size(),TYPES.size(),config.root().toAbsolutePath(),config.current().generation(),ZombiePermissions.provider());
    }
}
