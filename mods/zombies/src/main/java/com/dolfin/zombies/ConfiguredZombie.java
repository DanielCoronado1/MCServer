package com.dolfin.zombies;

import com.google.gson.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.*;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.storage.*;

/** One EntityType per kind. Entity-local settings persist and survive config reloads/restarts. */
public class ConfiguredZombie extends Zombie {
    private final ZombieKind kind;
    private JsonObject settings;
    private long configGeneration;
    public ConfiguredZombie(EntityType<? extends Zombie> type,Level level,ZombieKind kind){
        super(type,level);this.kind=kind;
        if(!level.isClientSide()){
            var snapshot=DolfinZombies.config().current();settings=snapshot.zombie(kind);configGeneration=snapshot.generation();applyStats();
        }
    }
    public ZombieKind kind(){return kind;}
    public long configGeneration(){return configGeneration;}
    public JsonObject settings(){return settings==null?kind.defaults():settings.deepCopy();}
    public boolean abilityEnabled(String id){return DolfinZombies.config().current().abilityEnabled(kind,id);}
    public void applyStats(){
        if(settings==null)return;
        // Vanilla can randomly make a leader with multiplied health; config owns these stats.
        getAttribute(Attributes.MAX_HEALTH).removeModifier(net.minecraft.resources.Identifier.withDefaultNamespace("leader_zombie_bonus"));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(settings.get("vida").getAsDouble());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(settings.get("danio").getAsDouble());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.23*settings.get("velocidad").getAsDouble());
        getAttribute(Attributes.ARMOR).setBaseValue(settings.get("armadura").getAsDouble());
        getAttribute(Attributes.SCALE).setBaseValue(settings.get("escala").getAsDouble());
        setHealth(getMaxHealth());
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,net.minecraft.world.DifficultyInstance difficulty,EntitySpawnReason reason,SpawnGroupData group){
        var result=super.finalizeSpawn(level,difficulty,reason,group);setBaby(false);applyStats();
        DolfinZombies.LOGGER.debug("event=zombie_spawn id={} uuid={} reason={} dimension={} pos={} generation={} health={} damage={} speed={} loot={}",kind.id(),getUUID(),reason,level.getLevel().dimension().identifier(),blockPosition(),configGeneration,getMaxHealth(),getAttributeValue(Attributes.ATTACK_DAMAGE),getAttributeValue(Attributes.MOVEMENT_SPEED),settings.get("loot").getAsString());
        return result;
    }
    // Preserve special identity instead of silently converting into a vanilla drowned.
    @Override protected boolean convertsInWater(){return false;}
    @Override protected boolean isSunSensitive(){return settings==null?super.isSunSensitive():settings.get("quemaConSol").getAsBoolean();}
    @Override protected int getBaseExperienceReward(ServerLevel level){return settings==null?5:settings.get("xp").getAsInt();}
    @Override protected void dropFromLootTable(ServerLevel level,DamageSource source,boolean recentlyHit){
        dropFromLootTable(level,source,recentlyHit,ResourceKey.create(Registries.LOOT_TABLE,net.minecraft.resources.Identifier.parse(settings.get("loot").getAsString())));
    }
    @Override protected void addAdditionalSaveData(ValueOutput out){super.addAdditionalSaveData(out);if(settings!=null)out.putString("DolfinZombieSettings",settings.toString());out.putLong("DolfinConfigGeneration",configGeneration);}
    @Override protected void readAdditionalSaveData(ValueInput in){
        super.readAdditionalSaveData(in);
        in.getString("DolfinZombieSettings").ifPresent(raw->{try{settings=DolfinZombies.config().validateSaved(kind,raw);}catch(RuntimeException e){DolfinZombies.LOGGER.error("event=entity_settings_invalid id={} uuid={} reason={}",kind.id(),getUUID(),e.toString());}});
        configGeneration=in.getLongOr("DolfinConfigGeneration",configGeneration);
        // Attribute/health values were already loaded by vanilla; do not heal or reapply new configuration.
    }
}
