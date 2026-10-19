package com.dolfin.zombies.custody;

import com.dolfin.zombies.*;
import com.google.gson.JsonElement;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;

/**
 * Custom harmful MobEffect ("Infección") applied by Contagiado zombies.
 * Progresses through 3 stages based on remaining vs total duration:
 * 0-50% elapsed: no extra symptoms; 50-80%: mild Hunger I; 80-100%: occasional Nausea.
 */
public final class InfectionEffect extends MobEffect {
    public static Holder.Reference<MobEffect> HOLDER;
    private static final Map<UUID,Integer> INITIAL_DURATION=new HashMap<>();

    public InfectionEffect(){
        super(MobEffectCategory.HARMFUL,0x588C38);
    }

    public static void register(){
        if(HOLDER!=null)return;
        var key=ResourceKey.create(Registries.MOB_EFFECT,DolfinZombies.id("infeccion"));
        HOLDER=Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,key,new InfectionEffect());
    }

    public static void infect(ServerPlayer player,int durationTicks){
        if(HOLDER==null||durationTicks<=0||player.isCreative()||player.isSpectator())return;
        INITIAL_DURATION.put(player.getUUID(),durationTicks);
        player.addEffect(new MobEffectInstance(HOLDER,durationTicks,0,false,true,true));
        DolfinZombies.LOGGER.info("event=infection_applied player={} playerName={} durationTicks={}",
            player.getUUID(),player.getName().getString(),durationTicks);
    }

    public static boolean isInfected(LivingEntity entity){
        return HOLDER!=null&&entity.hasEffect(HOLDER);
    }

    public static int initialDuration(UUID playerId,int fallback){
        return INITIAL_DURATION.getOrDefault(playerId,fallback);
    }

    public static void clearTracking(UUID playerId){
        INITIAL_DURATION.remove(playerId);
    }

    /** Checks if the consumed item is configured in Contagiado's `curas` list and cures infection if so. */
    public static boolean tryCureWithItem(ServerPlayer player,ItemStack stack){
        if(!isInfected(player)||stack.isEmpty())return false;
        if(!isCureItem(stack,configuredCures()))return false;
        player.removeEffect(HOLDER);
        clearTracking(player.getUUID());
        DolfinZombies.LOGGER.info("event=infection_cured player={} playerName={} item={}",
            player.getUUID(),player.getName().getString(),BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return true;
    }

    public static List<String> configuredCures(){
        var cfg=DolfinZombies.config();
        if(cfg==null)return List.of("minecraft:golden_apple","minecraft:enchanted_golden_apple","minecraft:milk_bucket");
        var hab=cfg.current().zombie(ZombieKind.CONTAGIADO).getAsJsonObject("habilidades");
        var obj=hab.has("infeccion")?hab.getAsJsonObject("infeccion"):hab.getAsJsonObject("convertirAlMorir");
        if(obj==null||!obj.has("curas")||!obj.get("curas").isJsonArray())return List.of("minecraft:golden_apple","minecraft:enchanted_golden_apple","minecraft:milk_bucket");
        List<String> out=new ArrayList<>();
        for(JsonElement e:obj.getAsJsonArray("curas"))if(e.isJsonPrimitive())out.add(e.getAsString());
        return out;
    }

    public static boolean isCureItem(ItemStack stack,List<String> selectors){
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return ItemAccounting.matchesSelector(id,s->stack.is(TagKey.create(Registries.ITEM,Identifier.parse(s))),selectors);
    }

    @Override public boolean shouldApplyEffectTickThisTick(int duration,int amplifier){
        return duration%40==0;
    }

    @Override public boolean applyEffectTick(ServerLevel level,LivingEntity entity,int amplifier){
        if(!(entity instanceof ServerPlayer player)||player.isCreative()||player.isSpectator())return true;
        var inst=player.getEffect(HOLDER);
        if(inst==null)return true;
        int rem=inst.getDuration();
        int total=Math.max(rem,INITIAL_DURATION.getOrDefault(player.getUUID(),defaultDuration()));
        INITIAL_DURATION.putIfAbsent(player.getUUID(),total);
        int stage=ItemAccounting.infectionStage(rem,total);
        if(stage>=1){
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER,60,0,true,false,true));
        }
        if(stage>=2&&rem%120==0){
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA,100,0,true,false,true));
        }
        return true;
    }

    private static int defaultDuration(){
        var cfg=DolfinZombies.config();
        if(cfg==null)return 6000;
        var hab=cfg.current().zombie(ZombieKind.CONTAGIADO).getAsJsonObject("habilidades");
        var obj=hab.has("convertirAlMorir")?hab.getAsJsonObject("convertirAlMorir"):null;
        return obj!=null&&obj.has("duracionInfeccion")?obj.get("duracionInfeccion").getAsInt():6000;
    }
}
