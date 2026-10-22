package com.dolfin.space.client.ships;

import com.dolfin.space.DolfinSpace;
import com.dolfin.space.ships.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;

/**
 * Renders the three box-UV ship models baked from {@link ShipDesign} plus an emissive, flickering
 * exhaust flame per nozzle. Render snapshots never retain live entities.
 */
public final class SpaceshipRenderer
        extends EntityRenderer<SpaceshipEntity, SpaceshipRenderer.State> {
    private static final Identifier FLAME_TEXTURE =
            DolfinSpace.id("textures/entity/ship_flame.png");
    private static final int FULL_BRIGHT = 15728880;

    /** Per-frame snapshot: tier, yaw and engine thrust in [0, 1]. */
    public static final class State extends EntityRenderState {
        public ShipTier tier = ShipTier.BASICA;
        public float thrust;
        public float yaw;
    }

    private final EnumMap<ShipTier, EntityModel<State>> models = new EnumMap<>(ShipTier.class);
    private final EntityModel<State> flame = flameModel();

    public SpaceshipRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.5f;
        for (var tier : ShipTier.values()) models.put(tier, shipModel(ShipDesign.of(tier)));
    }

    public State createRenderState() {
        return new State();
    }

    public void extractRenderState(SpaceshipEntity ship, State s, float dt) {
        super.extractRenderState(ship, s, dt);
        s.tier = ship.tier();
        s.thrust = ship.thrust();
        s.yaw = ship.getYRot();
    }

    public void submit(
            State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        // Vanilla model convention: Y-down model space, front (-Z) facing the entity yaw.
        pose.rotateDegrees(Axis.YP, 180 - s.yaw);
        pose.scale(-1, -1, 1);
        collector.submitModel(
                models.get(s.tier),
                s,
                pose,
                DolfinSpace.id("textures/entity/" + s.tier.itemId() + ".png"),
                s.lightCoords,
                OverlayTexture.NO_OVERLAY,
                s.outlineColor);
        if (s.thrust > 0) submitFlames(s, pose, collector);
        pose.popPose();
        super.submit(s, pose, collector, camera);
    }

    /** One outer plume and one tighter core per nozzle, each flickering independently. */
    private void submitFlames(State s, PoseStack pose, SubmitNodeCollector collector) {
        var nozzles = ShipDesign.of(s.tier).nozzles();
        for (int i = 0; i < nozzles.size(); i++) {
            var nozzle = nozzles.get(i);
            float t = s.ageInTicks + i * 7.3f;
            float flicker = 1 + .14f * (float) Math.sin(t * 1.7) + .07f * (float) Math.sin(t * 4.9);
            float width = nozzle.size() / 8f;
            submitFlame(s, pose, collector, nozzle, width, s.thrust * 1.6f * flicker);
            submitFlame(s, pose, collector, nozzle, width * .5f, s.thrust * flicker);
        }
    }

    private void submitFlame(
            State s,
            PoseStack pose,
            SubmitNodeCollector collector,
            ShipDesign.Nozzle nozzle,
            float width,
            float length) {
        pose.pushPose();
        pose.translate(-nozzle.x() / 16f, 0, nozzle.z() / 16f);
        pose.scale(width, length, width);
        collector.submitModel(
                flame, s, pose, FLAME_TEXTURE, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    /** Bakes a design's parts into the flipped vanilla model space with box-UV offsets. */
    private static EntityModel<State> shipModel(ShipDesign design) {
        var mesh = new MeshDefinition();
        for (var part : design.parts())
            mesh.getRoot()
                    .addOrReplaceChild(
                            part.name(),
                            CubeListBuilder.create()
                                    .texOffs(part.u(), part.v())
                                    .addBox(
                                            -(part.x() + part.w()),
                                            -(part.y() + part.h()),
                                            part.z(),
                                            part.w(),
                                            part.h(),
                                            part.d()),
                            PartPose.ZERO);
        var layer = LayerDefinition.create(mesh, design.textureWidth(), design.textureHeight());
        return new EntityModel<>(layer.bakeRoot()) {};
    }

    /** A one-block plume hanging below the origin, scaled per nozzle at render time. */
    private static EntityModel<State> flameModel() {
        var mesh = new MeshDefinition();
        mesh.getRoot()
                .addOrReplaceChild(
                        "flame",
                        CubeListBuilder.create().addBox(-4, 0, -4, 8, 16, 8),
                        PartPose.ZERO);
        return new EntityModel<>(
                LayerDefinition.create(mesh, 32, 32).bakeRoot(),
                RenderTypes::entityTranslucentEmissive) {};
    }
}
