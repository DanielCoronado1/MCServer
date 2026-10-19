package com.dolfin.space.client.ships;
import com.dolfin.space.ships.SpaceshipEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
/** Vanilla model, deliberately independent of GeckoLib. */
public final class SpaceshipRenderer extends EntityRenderer<SpaceshipEntity,EntityRenderState> {
 private final EntityModel<EntityRenderState> model;
 private static final Identifier TEXTURE=Identifier.withDefaultNamespace("textures/block/iron_block.png");
 public SpaceshipRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=1.3f;MeshDefinition mesh=new MeshDefinition();
  mesh.getRoot().addOrReplaceChild("hull",CubeListBuilder.create().texOffs(0,0).addBox(-10,0,-10,20,32,20).addBox(-7,32,-7,14,8,14).addBox(-3,40,-3,6,8,6).addBox(-18,0,-2,8,12,4).addBox(10,0,-2,8,12,4).addBox(-2,0,10,4,12,8),PartPose.ZERO);
  model=new EntityModel<>(LayerDefinition.create(mesh,64,64).bakeRoot()){};
 }
 public EntityRenderState createRenderState(){return new EntityRenderState();}
 public void submit(EntityRenderState s,PoseStack p,SubmitNodeCollector c,CameraRenderState camera){p.pushPose();c.submitModel(model,s,p,TEXTURE,s.lightCoords,OverlayTexture.NO_OVERLAY,s.outlineColor);p.popPose();super.submit(s,p,c,camera);}
}
