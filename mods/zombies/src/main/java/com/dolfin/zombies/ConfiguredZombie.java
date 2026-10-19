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
    private static final net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.world.item.component.ResolvableProfile> APPEARANCE=net.minecraft.network.syncher.SynchedEntityData.defineId(ConfiguredZombie.class,net.minecraft.network.syncher.EntityDataSerializers.RESOLVABLE_PROFILE);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> HAS_APPEARANCE=net.minecraft.network.syncher.SynchedEntityData.defineId(ConfiguredZombie.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private final ZombieKind kind;
    private JsonObject settings;
    private long configGeneration;
    public ConfiguredZombie(EntityType<? extends Zombie> type,Level level,ZombieKind kind){
        super(type,level);this.kind=kind;
        if(!level.isClientSide()){
            var snapshot=DolfinZombies.config().current();settings=snapshot.zombie(kind);configGeneration=snapshot.generation();applyStats();
        }
    }
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(APPEARANCE,net.minecraft.world.item.component.ResolvableProfile.createUnresolved("Mimo"));builder.define(HAS_APPEARANCE,false);}
    public boolean hasPlayerAppearance(){return entityData.get(HAS_APPEARANCE);}
    public net.minecraft.world.item.component.ResolvableProfile playerAppearance(){return entityData.get(APPEARANCE);}
    /** Conversion/death mechanics call once; profile properties and equipped armor survive restart and synchronize. */
    public void copyPlayerAppearance(net.minecraft.world.entity.player.Player player){
        if(kind!=ZombieKind.MIMO&&kind!=ZombieKind.CONTAGIADO)return;
        entityData.set(APPEARANCE,net.minecraft.world.item.component.ResolvableProfile.createResolved(player.getGameProfile()));entityData.set(HAS_APPEARANCE,true);
        for(var slot:EquipmentSlot.values())if(slot.getType()==EquipmentSlot.Type.HUMANOID_ARMOR)setItemSlot(slot,player.getItemBySlot(slot).copy());
        DolfinZombies.LOGGER.debug("event=zombie_appearance_copied uuid={} player={} kind={}",getUUID(),player.getUUID(),kind);
    }
    @Override public void tick(){
        super.tick();
        if(level().isClientSide()&&tickCount%10==0){
            var particle=switch(kind){case BOMBERO->net.minecraft.core.particles.ParticleTypes.SMOKE;case BILIOSO->net.minecraft.core.particles.ParticleTypes.SNEEZE;case CONGELADO->net.minecraft.core.particles.ParticleTypes.CLOUD;default->null;};
            if(particle!=null)level().addParticle(particle,getX(),getEyeY(),getZ(),0,.02,0);
        }
    }
    /** Invoke after the inventory transaction succeeds; never infer theft from a melee hit. */
    public void playStealSound(){if(kind==ZombieKind.LADRON&&abilityEnabled("robar"))ZombieSounds.play(this,ZombieSounds.LADRON_STEAL);}
    /** The grab ability invokes this after accepting its target. */
    public void playGrabSound(){if(kind==ZombieKind.ABISMO&&abilityEnabled("agarre"))ZombieSounds.play(this,ZombieSounds.ABISMO_GRAB);}
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound(){return kind==ZombieKind.BILIOSO&&abilityEnabled("nube")?ZombieSounds.BILIOSO_BURST:super.getDeathSound();}
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
        // Blood moon bonus is baked into this entity's persisted settings snapshot, so it survives dawn, reloads and restarts.
        if(BloodMoon.active(level.getLevel())){double bonus=settings.getAsJsonObject("lunaDeSangre").get("bonusStats").getAsDouble();for(var key:new String[]{"vida","danio"})settings.addProperty(key,settings.get(key).getAsDouble()*bonus);DolfinZombies.LOGGER.debug("event=blood_moon_bonus id={} uuid={} bonusStats={}",kind.id(),getUUID(),bonus);}
        var result=super.finalizeSpawn(level,difficulty,reason,group);setBaby(false);applyStats();
        DolfinZombies.LOGGER.debug("event=zombie_spawn id={} uuid={} reason={} dimension={} pos={} generation={} health={} damage={} speed={} loot={}",kind.id(),getUUID(),reason,level.getLevel().dimension().identifier(),blockPosition(),configGeneration,getMaxHealth(),getAttributeValue(Attributes.ATTACK_DAMAGE),getAttributeValue(Attributes.MOVEMENT_SPEED),settings.get("loot").getAsString());
        SpawnRules.spawned(this,reason);return result;
    }
    // Natural spawn rules (incl. light and the per-player cap) are owned by the SpawnPlacements predicate in SpawnRules;
    // skipping Monster's light heuristic here keeps luzMax authoritative and avoids evaluating the rules twice.
    @Override public boolean checkSpawnRules(LevelAccessor level,EntitySpawnReason reason){return true;}
    @Override public boolean checkSpawnObstruction(LevelReader level){return kind==ZombieKind.ABISMO?level.isUnobstructed(this):super.checkSpawnObstruction(level);}
    // Preserve special identity instead of silently converting into a vanilla drowned.
    @Override protected boolean convertsInWater(){return false;}
    /** Whether this kind burns in daylight (quemaConSol); used by the blood moon dawn close. */
    public boolean burnsInSun(){return isSunSensitive();}
    @Override protected boolean isSunSensitive(){return settings==null?super.isSunSensitive():settings.get("quemaConSol").getAsBoolean();}
    @Override protected int getBaseExperienceReward(ServerLevel level){return BloodMoonOptions.experience(settings==null?5:settings.get("xp").getAsInt(),BloodMoon.options().xpMultiplier(),BloodMoon.active(level));}
    @Override protected void dropFromLootTable(ServerLevel level,DamageSource source,boolean recentlyHit){
        dropFromLootTable(level,source,recentlyHit,ResourceKey.create(Registries.LOOT_TABLE,net.minecraft.resources.Identifier.parse(settings.get("loot").getAsString())));
    }
    @Override protected void addAdditionalSaveData(ValueOutput out){super.addAdditionalSaveData(out);if(hasPlayerAppearance()){out.store("DolfinPlayerAppearance",net.minecraft.world.item.component.ResolvableProfile.CODEC,playerAppearance());DolfinZombies.LOGGER.debug("event=zombie_appearance_saved uuid={}",getUUID());}if(settings!=null)out.putString("DolfinZombieSettings",settings.toString());out.putLong("DolfinConfigGeneration",configGeneration);}
    @Override protected void readAdditionalSaveData(ValueInput in){
        super.readAdditionalSaveData(in);
        in.read("DolfinPlayerAppearance",net.minecraft.world.item.component.ResolvableProfile.CODEC).ifPresent(profile->{entityData.set(APPEARANCE,profile);entityData.set(HAS_APPEARANCE,true);DolfinZombies.LOGGER.debug("event=zombie_appearance_loaded uuid={}",getUUID());});
        in.getString("DolfinZombieSettings").ifPresent(raw->{try{settings=DolfinZombies.config().validateSaved(kind,raw);}catch(RuntimeException e){DolfinZombies.LOGGER.error("event=entity_settings_invalid id={} uuid={} reason={}",kind.id(),getUUID(),e.toString());}});
        configGeneration=in.getLongOr("DolfinConfigGeneration",configGeneration);
        // Attribute/health values were already loaded by vanilla; do not heal or reapply new configuration.
    }
}
