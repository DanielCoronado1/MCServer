package com.dolfin.zombies.client;

import com.dolfin.zombies.*;
import com.dolfin.zombies.kinds.ExploradorZombie;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.Set;

public final class KindZombieRenderer extends ZombieRenderer {
    private final ZombieKind kind;
    private final Identifier skin;
    private static final Set<ZombieKind> GLOW =
            Set.of(
                    ZombieKind.MINERO,
                    ZombieKind.BOMBERO,
                    ZombieKind.ABISMO,
                    ZombieKind.ENANO,
                    ZombieKind.CONTAGIADO,
                    ZombieKind.EXPLORADOR);

    static final class State extends ZombieRenderState {
        boolean hiddenBody;
    }

    public KindZombieRenderer(EntityRendererProvider.Context context, ZombieKind kind) {
        super(context);
        this.kind = kind;
        skin = texture(kind, "");
        // Props use their own mesh and texture, so the entity skin remains a portable 64x64 skin.
        if (kind != ZombieKind.EXPLORADOR) {
            var props = ZombieAttachments.create(kind);
            addLayer(
                    new RenderLayer<ZombieRenderState, ZombieModel<ZombieRenderState>>(this) {
                        @Override
                        public void submit(
                                PoseStack pose,
                                SubmitNodeCollector collector,
                                int light,
                                ZombieRenderState state,
                                float y,
                                float x) {
                            if (state.isInvisible) return;
                            collector
                                    .order(0)
                                    .submitModel(
                                            props,
                                            state,
                                            pose,
                                            RenderTypes.entityCutout(texture(kind, "_accessories")),
                                            light,
                                            OverlayTexture.NO_OVERLAY,
                                            0);
                            if (kind == ZombieKind.MINERO || kind == ZombieKind.ENANO)
                                collector
                                        .order(1)
                                        .submitModel(
                                                props,
                                                state,
                                                pose,
                                                RenderTypes.eyes(
                                                        texture(kind, "_accessories_emissive")),
                                                15728880,
                                                OverlayTexture.NO_OVERLAY,
                                                0);
                        }
                    });
        }
        if (GLOW.contains(kind))
            addLayer(
                    new RenderLayer<ZombieRenderState, ZombieModel<ZombieRenderState>>(this) {
                        @Override
                        public void submit(
                                PoseStack pose,
                                SubmitNodeCollector collector,
                                int light,
                                ZombieRenderState state,
                                float y,
                                float x) {
                            // Explicitly ignores viewer night vision and entity invisibility, like
                            // spider eyes.
                            int tint =
                                    kind == ZombieKind.CONTAGIADO
                                            ? pulse(state.ageInTicks)
                                            : 0xffffffff;
                            collector
                                    .order(1)
                                    .submitModel(
                                            getParentModel(),
                                            state,
                                            pose,
                                            RenderTypes.eyes(texture(kind, "_eyes")),
                                            15728880,
                                            OverlayTexture.NO_OVERLAY,
                                            tint,
                                            null,
                                            0);
                        }
                    });
    }

    static int pulse(float ticks) {
        int value = (int) (190 + 65 * Math.sin(ticks * .12));
        return 0xff000000 | (value << 16) | (value << 8) | value;
    }

    static Identifier texture(ZombieKind kind, String suffix) {
        return DolfinZombies.id("textures/entity/" + kind.id() + suffix + ".png");
    }

    @Override
    public ZombieRenderState createRenderState() {
        return new State();
    }

    @Override
    public Identifier getTextureLocation(ZombieRenderState state) {
        return skin;
    }

    @Override
    public void extractRenderState(Zombie zombie, ZombieRenderState state, float partial) {
        super.extractRenderState(zombie, state, partial);
        if (zombie instanceof ExploradorZombie explorer) {
            ((State) state).hiddenBody = !explorer.bodyRevealed();
            state.isInvisible = ((State) state).hiddenBody;
            state.isInvisibleToPlayer = ((State) state).hiddenBody;
        }
    }

    @Override
    protected RenderType getRenderType(
            ZombieRenderState state, boolean visible, boolean translucent, boolean glowing) {
        if (kind == ZombieKind.EXPLORADOR)
            return ((State) state).hiddenBody ? null : RenderTypes.entityTranslucent(skin);
        return super.getRenderType(state, visible, translucent, glowing);
    }

    @Override
    protected int getModelTint(ZombieRenderState state) {
        return kind == ZombieKind.EXPLORADOR ? 0x99ffffff : super.getModelTint(state);
    }

    @Override
    protected void scale(ZombieRenderState state, PoseStack pose) {
        super.scale(state, pose);
        if (kind == ZombieKind.HAMBRIENTO) pose.scale(.7f, 1, .75f);
        if (kind == ZombieKind.BILIOSO) pose.scale(1.25f, 1, 1.15f);
    }
}
