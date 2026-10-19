package com.dolfin.zombies.client;
import com.dolfin.zombies.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
/** Per-kind presentation with synchronized explorer eyes and copied player appearances. */
public final class DolfinZombiesClient implements ClientModInitializer {
 public static boolean red;
 @Override public void onInitializeClient(){
  net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(BloodMoonPayload.TYPE,(payload,context)->context.client().execute(()->red=payload.red()));
  net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h,c)->red=false);
  for(var kind:ZombieKind.values()){
   if(kind==ZombieKind.MIMO||kind==ZombieKind.CONTAGIADO)EntityRendererRegistry.register(DolfinZombies.type(kind),context->new PlayerLikeZombieRenderer(context,kind));
   else EntityRendererRegistry.register(DolfinZombies.type(kind),context->new KindZombieRenderer(context,kind));
  }
 }
}
