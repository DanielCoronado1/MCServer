package com.dolfin.zombies.client;
import com.dolfin.zombies.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.*;
/** Player geometry, skin layers, held items and armor; no fake client player or world mutation. */
public final class PlayerLikeZombieRenderer extends HumanoidMobRenderer<ConfiguredZombie,AvatarRenderState,PlayerModel> {
 static final class State extends AvatarRenderState {boolean copied;boolean infected;}
 private final ZombieKind kind;
 private final PlayerModel wide,slim;
 public PlayerLikeZombieRenderer(EntityRendererProvider.Context context,ZombieKind kind){
  super(context,new PlayerModel(context.bakeLayer(ModelLayers.PLAYER),false),.5f);this.kind=kind;wide=model;slim=new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM),true);
  addArmor(context,false);addArmor(context,true);
  addLayer(new RenderLayer<AvatarRenderState,PlayerModel>(this){
   @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,AvatarRenderState state,float y,float x){
    if(state.isInvisible)return;var s=(State)state;
    if(s.infected){collector.order(1).submitModel(getParentModel(),state,pose,RenderTypes.eyes(KindZombieRenderer.texture(ZombieKind.CONTAGIADO,"_eyes")),15728880,OverlayTexture.NO_OVERLAY,KindZombieRenderer.pulse(state.ageInTicks),null,0);}
    else if(s.copied)collector.order(1).submitModel(getParentModel(),state,pose,RenderTypes.entityCutout(DolfinZombies.id("textures/entity/mimo_zombie_disguise.png")),light,OverlayTexture.NO_OVERLAY,0);
   }
  });
 }
 private void addArmor(EntityRendererProvider.Context context,boolean narrow){
  ArmorModelSet<HumanoidModel<AvatarRenderState>> armor=ArmorModelSet.bake(narrow?ModelLayers.PLAYER_SLIM_ARMOR:ModelLayers.PLAYER_ARMOR,context.getModelSet(),HumanoidModel::new);
  addLayer(new HumanoidArmorLayer<AvatarRenderState,PlayerModel,HumanoidModel<AvatarRenderState>>(this,armor,context.getEquipmentRenderer()){
   @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,AvatarRenderState state,float y,float x){if((state.skin.model()==PlayerModelType.SLIM)==narrow)super.submit(pose,collector,light,state,y,x);}
  });
 }
 @Override public AvatarRenderState createRenderState(){return new State();}
 @Override public void extractRenderState(ConfiguredZombie zombie,AvatarRenderState state,float partial){
  super.extractRenderState(zombie,state,partial);var s=(State)state;s.copied=zombie.hasPlayerAppearance();s.infected=kind==ZombieKind.CONTAGIADO;
  if(s.copied){s.skin=Minecraft.getInstance().playerSkinRenderCache().getOrDefault(zombie.playerAppearance()).playerSkin();}
  else s.skin=DefaultPlayerSkin.getDefaultSkin();
  s.showHat=s.showJacket=s.showLeftPants=s.showRightPants=s.showLeftSleeve=s.showRightSleeve=true;
  s.showCape=false;s.isSpectator=false;
 }
 @Override public Identifier getTextureLocation(AvatarRenderState state){return ((State)state).copied?state.skin.body().texturePath():KindZombieRenderer.texture(kind,"");}
 @Override protected int getModelTint(AvatarRenderState state){return ((State)state).infected?0xff91cf87:0xffffffff;}
 @Override public void submit(AvatarRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){model=state.skin.model()==PlayerModelType.SLIM?slim:wide;super.submit(state,pose,collector,camera);}
}
