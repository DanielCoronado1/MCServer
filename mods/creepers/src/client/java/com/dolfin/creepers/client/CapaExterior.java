package com.dolfin.creepers.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Capa exterior con relieve, como el sombrero y la chaqueta del jugador: el modelo del mob con cada
 * caja inflada ({@link ModeloDiseno.Capa#EXTERIOR}) y su propia textura ({@code
 * <mob>_exterior.png}, mismo UV que la skin). Lo transparente no se dibuja; lo pintado sobresale.
 * Las texturas salen de {@code tools/skins/integrate_existing.py}.
 */
final class CapaExterior<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends RenderLayer<S, M> {
    private final EntityModel<? super S> modelo;
    private final Identifier textura;

    CapaExterior(RenderLayerParent<S, M> padre, EntityModel<? super S> modelo, Identifier textura) {
        super(padre);
        this.modelo = modelo;
        this.textura = textura;
    }

    @Override
    public void submit(
            PoseStack pose,
            SubmitNodeCollector collector,
            int luz,
            S estado,
            float yRot,
            float xRot) {
        coloredCutoutModelCopyLayerRender(
                modelo, textura, pose, collector, luz, estado, 0xffffffff, 1);
    }
}
