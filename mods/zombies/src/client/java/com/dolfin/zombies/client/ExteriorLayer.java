package com.dolfin.zombies.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Relief layer, like the player's hat and jacket: the design model with every box inflated ({@link
 * DesignModel.Layer#EXTERIOR}) and its own {@code <kind>_exterior.png} (same UV as the skin,
 * written by {@code tools/skins/integrate_existing.py}). Transparent texels are skipped; painted
 * ones stand out.
 */
final class ExteriorLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends RenderLayer<S, M> {
    private final EntityModel<? super S> model;
    private final Identifier texture;
    private final int translucentTint;

    /**
     * @param translucentTint ARGB tint for a see-through body (explorer), or {@code 0} for an
     *     opaque cutout layer
     */
    ExteriorLayer(
            RenderLayerParent<S, M> parent,
            EntityModel<? super S> model,
            Identifier texture,
            int translucentTint) {
        super(parent);
        this.model = model;
        this.texture = texture;
        this.translucentTint = translucentTint;
    }

    @Override
    public void submit(
            PoseStack pose, SubmitNodeCollector collector, int light, S state, float y, float x) {
        if (state.isInvisible) return;
        if (translucentTint == 0) {
            coloredCutoutModelCopyLayerRender(
                    model, texture, pose, collector, light, state, 0xffffffff, 1);
            return;
        }
        collector
                .order(1)
                .submitModel(
                        model,
                        state,
                        pose,
                        RenderTypes.entityTranslucent(texture),
                        light,
                        LivingEntityRenderer.getOverlayCoords(state, 0),
                        translucentTint,
                        null,
                        0);
    }
}
