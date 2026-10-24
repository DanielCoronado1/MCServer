package com.dolfin.space.client.mobs;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Relief layer, like the player's hat and jacket: the design model with every box inflated ({@link
 * DesignModel.Layer#EXTERIOR}) and its own {@code <mob>_exterior.png} in the skin's UV layout,
 * written by {@code tools/skins/integrate_existing.py}. Transparent texels are skipped; painted
 * ones stand out.
 */
final class ExteriorLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends RenderLayer<S, M> {
    private final EntityModel<? super S> model;
    private final Identifier texture;

    ExteriorLayer(
            RenderLayerParent<S, M> parent, EntityModel<? super S> model, Identifier texture) {
        super(parent);
        this.model = model;
        this.texture = texture;
    }

    @Override
    public void submit(
            PoseStack pose, SubmitNodeCollector collector, int light, S state, float y, float x) {
        coloredCutoutModelCopyLayerRender(model, texture, pose, collector, light, state, -1, 1);
    }
}
