package com.dolfin.zombies.client;
import com.dolfin.zombies.ZombieKind;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
/** Model-space props in a separate atlas; empty humanoid parents inherit the walking/head poses. */
final class ZombieAttachments {
 private ZombieAttachments(){}
 static ZombieModel<ZombieRenderState> create(ZombieKind kind){
  MeshDefinition mesh=new MeshDefinition();var root=mesh.getRoot();
  var head=root.addOrReplaceChild("head",CubeListBuilder.create(),PartPose.ZERO);
  head.addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
  var body=root.addOrReplaceChild("body",CubeListBuilder.create(),PartPose.ZERO);
  var right=root.addOrReplaceChild("right_arm",CubeListBuilder.create(),PartPose.offset(-5,2,0));
  root.addOrReplaceChild("left_arm",CubeListBuilder.create(),PartPose.offset(5,2,0));
  root.addOrReplaceChild("right_leg",CubeListBuilder.create(),PartPose.offset(-1.9f,12,0));
  root.addOrReplaceChild("left_leg",CubeListBuilder.create(),PartPose.offset(1.9f,12,0));
  switch(kind){
   case MINERO -> {box(head,"helmet",0,0,-4.5f,-9,-4.5f,9,2,9);box(head,"lamp",48,0,-1.5f,-8,-6,3,3,2);pick(right,false);}
   case LADRON -> {box(body,"sack",16,32,-4,2,2,8,9,5);box(head,"hood",0,0,-4.4f,-8.5f,-4.4f,8.8f,3,8.8f);}
   case BOMBERO -> {box(body,"charred_ridge",0,0,-5,1,0,1,9,3);}
   case ABISMO -> {box(head,"kelp",0,48,-5,-8,0,1,7,2);box(body,"barnacles",16,48,3,3,-3,2,3,2);}
   case ESCUDERO -> {box(head,"dented_helm",0,0,-4.5f,-9,-4.5f,9,3,9);var left=root.getChild("left_arm");box(left,"shield",0,0,-4,-1,-5,8,11,2);}
   case HAMBRIENTO -> box(head,"jaw",0,0,-4,-1,-5,8,3,5);
   case GRITON -> {box(head,"open_jaw",0,0,-4,0,-4.5f,8,2,5);box(head,"eye_left",0,0,-3.5f,-6,-5,2,2,1);box(head,"eye_right",0,0,1.5f,-6,-5,2,2,1);}
   case BRUJA -> {box(head,"brim",0,0,-6,-9,-6,12,1,12);box(head,"witch_crown",0,0,-4,-13,-4,8,4,8);box(head,"tip",0,0,-2,-17,-1,4,4,4);box(head,"mushroom",16,48,2,-11,-6,3,2,3);box(body,"bottles",0,48,-4,8,-3,3,3,2);}
   case GRANJERO -> {hat(head);box(right,"pitchfork_handle",0,32,-1,-3,-1,1,20,1);box(right,"pitchfork_bar",32,32,-3,-4,-1,5,1,1);for(int x=-3;x<=1;x+=2)box(right,"prong"+x,32,32,x,-8,-1,1,4,1);}
   case CONGELADO -> {for(int x=-4;x<=4;x+=4)box(head,"icicle"+x,0,0,x,-2,0,1,5,1);box(body,"fur",16,48,-5,-1,-3,10,3,6);}
   case JINETE -> {hat(head);box(body,"saddle_roll",16,32,-4,7,2,8,3,3);}
   case BILIOSO -> box(body,"belly",0,0,-5,3,-4,10,8,7);
   case ENANO -> {box(head,"helmet",0,0,-4.5f,-9,-4.5f,9,2,9);box(head,"candle",48,0,-.5f,-13,-1,1,4,1);box(head,"beard",32,32,-4,-1,-3,8,5,3);pick(right,true);}
   case CONTAGIADO, MIMO, EXPLORADOR -> {}
  }
  return new ZombieModel<>(LayerDefinition.create(mesh,64,64).bakeRoot());
 }
 private static void hat(PartDefinition head){box(head,"brim",0,0,-6,-9,-6,12,1,12);box(head,"crown",0,0,-4,-12,-4,8,3,8);}
 private static void pick(PartDefinition arm,boolean small){float length=small?7:12;box(arm,"pick_handle",0,32,-1,0,-1,1,length,1);box(arm,"pick_head",32,32,-4,-1,-1,7,2,2);}
 private static void box(PartDefinition parent,String name,int u,int v,float x,float y,float z,float w,float h,float d){parent.addOrReplaceChild(name,CubeListBuilder.create().texOffs(u,v).addBox(x,y,z,w,h,d),PartPose.ZERO);}
}
