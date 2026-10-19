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
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.EnumMap;

/** Three original multipart vanilla models. Render snapshots never retain live entities. */
public final class SpaceshipRenderer
        extends EntityRenderer<SpaceshipEntity, SpaceshipRenderer.State> {
    public static final class State extends EntityRenderState {
        public ShipTier tier = ShipTier.BASICA;
        public boolean burning;
        public float yaw;
    }

    private final EnumMap<ShipTier, EntityModel<State>> models = new EnumMap<>(ShipTier.class);
    private final EntityModel<State> flame;

    public SpaceshipRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.5f;
        for (var tier : ShipTier.values()) {
            var mesh = new MeshDefinition();
            for (var part : ShipDesign.of(tier).parts())
                mesh.getRoot()
                        .addOrReplaceChild(
                                part.name(),
                                CubeListBuilder.create()
                                        .texOffs(0, part.name().contains("window") ? 96 : 0)
                                        .addBox(
                                                part.x(), part.y(), part.z(), part.w(), part.h(),
                                                part.d()),
                                PartPose.ZERO);
            models.put(
                    tier, new EntityModel<>(LayerDefinition.create(mesh, 128, 128).bakeRoot()) {});
        }
        var mesh = new MeshDefinition();
        mesh.getRoot()
                .addOrReplaceChild(
                        "flame",
                        CubeListBuilder.create().addBox(-4, -14, -4, 8, 14, 8),
                        PartPose.ZERO);
        flame = new EntityModel<>(LayerDefinition.create(mesh, 32, 32).bakeRoot()) {};
    }

    public State createRenderState() {
        return new State();
    }

    public void extractRenderState(SpaceshipEntity ship, State s, float dt) {
        super.extractRenderState(ship, s, dt);
        s.tier = ship.tier();
        s.burning = ship.burning();
        s.yaw = ship.getYRot();
    }

    public void submit(
            State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.rotateDegrees(Axis.YP, -s.yaw);
        collector.submitModel(
                models.get(s.tier),
                s,
                pose,
                DolfinSpace.id("textures/entity/" + s.tier.itemId() + ".png"),
                s.lightCoords,
                OverlayTexture.NO_OVERLAY,
                s.outlineColor);
        if (s.burning) {
            int engines = s.tier == ShipTier.CARGA ? 2 : 1;
            for (int i = 0; i < engines; i++) {
                pose.pushPose();
                pose.translate(engines == 2 ? (i == 0 ? -1.125 : 1.125) : 0, 0, 0);
                pose.scale(1, .8f + .25f * (float) Math.sin(s.ageInTicks * 1.8), 1);
                collector.submitModel(
                        flame,
                        s,
                        pose,
                        DolfinSpace.id("textures/entity/ship_flame.png"),
                        15728880,
                        OverlayTexture.NO_OVERLAY,
                        0);
                pose.popPose();
            }
        }
        pose.popPose();
        super.submit(s, pose, collector, camera);
    }
}
