package com.dolfin.zombies.griefing;

import com.dolfin.zombies.ConfiguredZombie;
import com.dolfin.zombies.DolfinZombies;
import com.dolfin.zombies.ZombieKind;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Congelado's death block (#4 owns it; the kind's combat lives in #5). Hooked through a Fabric death event
 * so it does not touch #5's entity class. Every placed block goes through {@link BlockGuard}.
 */
public final class CongeladoIce {
    private CongeladoIce(){}
    static final int MAX_RADIUS=3;

    public static void register(){
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
            if(entity instanceof ConfiguredZombie z&&z.kind()==ZombieKind.CONGELADO&&z.level() instanceof ServerLevel level)place(level,z);
        });
    }

    static void place(ServerLevel level,ConfiguredZombie zombie){
        if(!zombie.abilityEnabled("congelar")){BlockGuard.rejected(zombie,zombie.blockPosition(),"death_ice",GuardDecision.Reason.DISABLED);return;}
        var a=zombie.settings().getAsJsonObject("habilidades").getAsJsonObject("congelar");
        int radius=Math.max(0,Math.min(MAX_RADIUS,a.has("radioHielo")?a.get("radioHielo").getAsInt():1));
        BlockState ice=Blocks.ICE.defaultBlockState();
        if(a.has("bloqueAlMorir")){
            var id=Identifier.tryParse(a.get("bloqueAlMorir").getAsString());
            var block=id==null?null:BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if(block==null||block==Blocks.AIR)DolfinZombies.LOGGER.warn("event=config_invalid id={} field=habilidades.congelar.bloqueAlMorir value={} fallback=minecraft:ice",zombie.kind().id(),a.get("bloqueAlMorir"));
            else ice=block.defaultBlockState();
        }
        BlockPos base=zombie.blockPosition();int placed=0;
        for(int[] o:disc(radius)){
            BlockPos pos=base.offset(o[0],0,o[1]);
            if(!level.isLoaded(pos))continue;
            BlockState state=level.getBlockState(pos);
            boolean water=state.is(Blocks.WATER)&&state.getFluidState().isSourceOfType(Fluids.WATER);
            boolean air=state.isAir()&&!level.getBlockState(pos.below()).isAir();
            if((water||air)&&BlockGuard.tryPlace(level,zombie,pos,ice,"death_ice"))placed++;
        }
        DolfinZombies.LOGGER.debug("event=death_ice id={} uuid={} pos={} radius={} placed={}",zombie.kind().id(),zombie.getUUID(),base.toShortString(),radius,placed);
    }

    /** Horizontal disc offsets {dx,dz} with dx²+dz² ≤ r², centre first. */
    static List<int[]> disc(int r){
        List<int[]> out=new ArrayList<>();out.add(new int[]{0,0});
        for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++)if((dx!=0||dz!=0)&&dx*dx+dz*dz<=r*r)out.add(new int[]{dx,dz});
        return out;
    }
}
