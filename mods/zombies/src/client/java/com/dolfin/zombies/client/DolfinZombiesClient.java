package com.dolfin.zombies.client;
import com.dolfin.zombies.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ZombieRenderer;
/** Vanilla presentation until issue #8 supplies individual skins and sounds. */
public final class DolfinZombiesClient implements ClientModInitializer {
    @Override public void onInitializeClient(){for(var kind:ZombieKind.values())EntityRendererRegistry.register(DolfinZombies.type(kind),ZombieRenderer::new);}
}
