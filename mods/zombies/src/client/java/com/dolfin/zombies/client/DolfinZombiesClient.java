package com.dolfin.zombies.client;
import com.dolfin.zombies.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ZombieRenderer;
/** Vanilla presentation until issue #8 supplies individual skins and sounds. */
public final class DolfinZombiesClient implements ClientModInitializer {
    public static boolean red;
    @Override public void onInitializeClient(){
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(BloodMoonPayload.TYPE,(payload,context)->context.client().execute(()->red=payload.red()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h,c)->red=false);
        for(var kind:ZombieKind.values()){
            if(kind==ZombieKind.MIMO||kind==ZombieKind.CONTAGIADO)EntityRendererRegistry.register(DolfinZombies.type(kind),PlayerSkinZombieRenderer::new);
            else EntityRendererRegistry.register(DolfinZombies.type(kind),ZombieRenderer::new);
        }
    }
}
