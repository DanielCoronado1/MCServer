package com.dolfin.zombies.client;

import com.dolfin.zombies.kinds.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import java.util.Optional;

/**
 * Renders Mimo and converted Contagiado zombies using the player model and resolved player skin
 * (falling back to vanilla zombie rendering for unconverted Contagiado zombies).
 */
public final class PlayerSkinZombieRenderer extends HumanoidMobRenderer<Zombie,PlayerSkinZombieRenderer.State,HumanoidModel<PlayerSkinZombieRenderer.State>> {
    private static final Identifier ZOMBIE_TEXTURE=Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png");

    public static final class State extends ZombieRenderState {
        public Identifier skinTexture=ZOMBIE_TEXTURE;
    }

    public PlayerSkinZombieRenderer(EntityRendererProvider.Context ctx){
        super(ctx,new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)),new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)),.5f);
        addLayer(new HumanoidArmorLayer<>(
            this,
            ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR,ctx.getModelSet(),HumanoidModel::new),
            ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR,ctx.getModelSet(),HumanoidModel::new),
            ctx.getEquipmentRenderer()
        ));
    }

    @Override public State createRenderState(){
        return new State();
    }

    @Override public void extractRenderState(Zombie entity,State state,float partialTick){
        super.extractRenderState(entity,state,partialTick);
        state.isAggressive=entity.isAggressive();
        state.isConverting=entity.isUnderWaterConverting();
        Optional<ResolvableProfile> profile=Optional.empty();
        if(entity instanceof MimoZombie mimo){
            profile=mimo.mimicProfile();
            state.skinTexture=resolveSkin(profile,DefaultPlayerSkin.getDefaultTexture());
        }else if(entity instanceof ContagiadoZombie contagiado&&contagiado.isConverted()){
            profile=contagiado.convertedProfile();
            state.skinTexture=resolveSkin(profile,ZOMBIE_TEXTURE);
        }else{
            state.skinTexture=ZOMBIE_TEXTURE;
        }
    }

    private static Identifier resolveSkin(Optional<ResolvableProfile> profileOpt,Identifier fallback){
        if(profileOpt.isEmpty())return fallback;
        try{
            Minecraft mc=Minecraft.getInstance();
            if(mc!=null&&mc.playerSkinRenderCache()!=null){
                PlayerSkin skin=mc.playerSkinRenderCache().getOrDefault(profileOpt.get()).playerSkin();
                if(skin!=null&&skin.body()!=null)return skin.body().texturePath();
            }
        }catch(Throwable ignored){}
        return fallback;
    }

    @Override public Identifier getTextureLocation(State state){
        return state.skinTexture;
    }
}
