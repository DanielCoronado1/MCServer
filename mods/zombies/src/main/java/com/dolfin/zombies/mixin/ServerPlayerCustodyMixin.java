package com.dolfin.zombies.mixin;

import com.dolfin.zombies.ZombieKind;
import com.dolfin.zombies.custody.*;
import com.dolfin.zombies.kinds.ContagiadoZombie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerCustodyMixin extends LivingEntity {
    protected ServerPlayerCustodyMixin(){super(null,null);}

    @Unique private ItemStack dolfinConsumingItem=ItemStack.EMPTY;

    @Inject(method="completeUsingItem",at=@At("HEAD"))
    private void beforeCompleteUsingItem(CallbackInfo ci){
        this.dolfinConsumingItem=this.getUseItem().copy();
    }

    @Inject(method="completeUsingItem",at=@At("RETURN"))
    private void afterCompleteUsingItem(CallbackInfo ci){
        if(!this.dolfinConsumingItem.isEmpty()){
            InfectionEffect.tryCureWithItem((ServerPlayer)(Object)this,this.dolfinConsumingItem);
            this.dolfinConsumingItem=ItemStack.EMPTY;
        }
    }

    @Inject(method="die",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V"))
    private void beforeDropAllDeathLoot(DamageSource source,CallbackInfo ci){
        ServerPlayer self=(ServerPlayer)(Object)this;
        boolean wasInfected=InfectionEffect.isInfected(self);
        boolean killedByContagiado=wasInfected
            ||source.getEntity() instanceof ContagiadoZombie
            ||source.getDirectEntity() instanceof ContagiadoZombie
            ||self.getKillCredit() instanceof ContagiadoZombie;
        if(wasInfected){
            ContagiadoZombie.handleInfectedPlayerDeath(self,source);
        }
        CustodyManager.get(self.level().getServer()).scheduleMimoIfEligible(self,killedByContagiado);
    }
}
