package com.dolfin.creepers.client;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.Variante;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.resources.Identifier;

/** Parte del mod que solo corre en el juego del jugador: como se dibuja cada variante. */
public class DolfinCreepersClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        for (Variante variante : Variante.values()) {
            Identifier textura = DolfinCreepers.id("textures/entity/" + variante.id() + ".png");
            EntityRendererRegistry.register(
                    DolfinCreepers.tipo(variante), contexto -> new Renderizador(contexto, textura));
        }
    }

    /** El mismo modelo del creeper (con su aura de cargado y su parpadeo), con otra textura. */
    private static class Renderizador extends CreeperRenderer {
        private final Identifier textura;

        Renderizador(EntityRendererProvider.Context contexto, Identifier textura) {
            super(contexto);
            this.textura = textura;
        }

        @Override
        public Identifier getTextureLocation(CreeperRenderState estado) {
            return textura;
        }
    }
}
