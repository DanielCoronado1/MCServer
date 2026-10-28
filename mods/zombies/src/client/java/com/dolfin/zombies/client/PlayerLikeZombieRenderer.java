package com.dolfin.zombies.client;

import com.dolfin.zombies.*;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.*;

/**
 * Player geometry, skin layers, held items and armor once a player was copied; until then the
 * kind's own design. No fake client player or world mutation.
 */
public final class PlayerLikeZombieRenderer
        extends HumanoidMobRenderer<ConfiguredZombie, AvatarRenderState, PlayerModel> {
    static final class State extends AvatarRenderState {
        boolean copied;
        boolean infected;
    }

    private final ZombieKind kind;
    private final PlayerModel wide, slim;
    // Until it copies a player, the zombie shows its own design instead of the player body.
    private final DesignModel<AvatarRenderState> design, designExterior;

    public PlayerLikeZombieRenderer(EntityRendererProvider.Context context, ZombieKind kind) {
        super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), .5f);
        this.kind = kind;
        var data = DesignModel.load(DolfinZombies.MOD_ID, kind.id());
        design = new DesignModel<>(data, DesignModel.Layer.BASE);
        designExterior = new DesignModel<>(data, DesignModel.Layer.EXTERIOR);
        wide = model;
        slim = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addArmor(context, false);
        addArmor(context, true);
        addLayer(
                new RenderLayer<AvatarRenderState, PlayerModel>(this) {
                    @Override
                    public void submit(
                            PoseStack pose,
                            SubmitNodeCollector collector,
                            int light,
                            AvatarRenderState state,
                            float y,
                            float x) {
                        if (state.isInvisible) return;
                        var s = (State) state;
                        if (!s.copied) {
                            submitDesign(pose, collector, light, state);
                            return;
                        }
                        if (s.infected) {
                            collector
                                    .order(1)
                                    .submitModel(
                                            getParentModel(),
                                            state,
                                            pose,
                                            RenderTypes.eyes(
                                                    KindZombieRenderer.texture(
                                                            ZombieKind.CONTAGIADO, "_eyes")),
                                            15728880,
                                            OverlayTexture.NO_OVERLAY,
                                            KindZombieRenderer.pulse(state.ageInTicks),
                                            null,
                                            0);
                        } else if (s.copied)
                            collector
                                    .order(1)
                                    .submitModel(
                                            getParentModel(),
                                            state,
                                            pose,
                                            RenderTypes.entityCutout(
                                                    DolfinZombies.id(
                                                            "textures/entity/mimo_zombie_disguise.png")),
                                            light,
                                            OverlayTexture.NO_OVERLAY,
                                            0);
                    }
                });
    }

    private void addArmor(EntityRendererProvider.Context context, boolean narrow) {
        ArmorModelSet<HumanoidModel<AvatarRenderState>> armor =
                ArmorModelSet.bake(
                        narrow ? ModelLayers.PLAYER_SLIM_ARMOR : ModelLayers.PLAYER_ARMOR,
                        context.getModelSet(),
                        HumanoidModel::new);
        addLayer(
                new HumanoidArmorLayer<
                        AvatarRenderState, PlayerModel, HumanoidModel<AvatarRenderState>>(
                        this, armor, context.getEquipmentRenderer()) {
                    @Override
                    public void submit(
                            PoseStack pose,
                            SubmitNodeCollector collector,
                            int light,
                            AvatarRenderState state,
                            float y,
                            float x) {
                        if ((state.skin.model() == PlayerModelType.SLIM) == narrow)
                            super.submit(pose, collector, light, state, y, x);
                    }
                });
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            ConfiguredZombie zombie, AvatarRenderState state, float partial) {
        super.extractRenderState(zombie, state, partial);
        var s = (State) state;
        s.copied = zombie.hasPlayerAppearance();
        s.infected = kind == ZombieKind.CONTAGIADO;
        if (s.copied) {
            s.skin =
                    Minecraft.getInstance()
                            .playerSkinRenderCache()
                            .getOrDefault(zombie.playerAppearance())
                            .playerSkin();
        } else s.skin = DefaultPlayerSkin.getDefaultSkin();
        s.showHat =
                s.showJacket =
                        s.showLeftPants =
                                s.showRightPants = s.showLeftSleeve = s.showRightSleeve = true;
        s.showCape = false;
        s.isSpectator = false;
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return ((State) state).copied
                ? state.skin.body().texturePath()
                : KindZombieRenderer.texture(kind, "");
    }

    /** The kind's own design, relief and (contagiado) pulsing eyes, while no player is copied. */
    private void submitDesign(
            PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state) {
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0);
        collector
                .order(0)
                .submitModel(
                        design,
                        state,
                        pose,
                        RenderTypes.entityCutout(KindZombieRenderer.texture(kind, "")),
                        light,
                        overlay,
                        0);
        collector
                .order(1)
                .submitModel(
                        designExterior,
                        state,
                        pose,
                        RenderTypes.entityCutout(KindZombieRenderer.texture(kind, "_exterior")),
                        light,
                        overlay,
                        0);
        if (((State) state).infected)
            collector
                    .order(2)
                    .submitModel(
                            design,
                            state,
                            pose,
                            RenderTypes.eyes(KindZombieRenderer.texture(kind, "_ojos")),
                            15728880,
                            OverlayTexture.NO_OVERLAY,
                            KindZombieRenderer.pulse(state.ageInTicks),
                            null,
                            0);
    }

    @Override
    protected RenderType getRenderType(
            AvatarRenderState state, boolean visible, boolean translucent, boolean glowing) {
        // The player body only shows once a player was copied; until then the design layer draws.
        return ((State) state).copied
                ? super.getRenderType(state, visible, translucent, glowing)
                : null;
    }

    @Override
    protected int getModelTint(AvatarRenderState state) {
        return ((State) state).infected ? 0xff91cf87 : 0xffffffff;
    }

    @Override
    public void submit(
            AvatarRenderState state,
            PoseStack pose,
            SubmitNodeCollector collector,
            CameraRenderState camera) {
        model = state.skin.model() == PlayerModelType.SLIM ? slim : wide;
        super.submit(state, pose, collector, camera);
    }
}
