package com.dolfin.zombies.client;

import com.dolfin.zombies.*;
import com.dolfin.zombies.kinds.ExploradorZombie;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.Set;

/**
 * Each kind with its own design model (hats, tools and mutations are part of the mesh), its relief
 * layer, held items and, for some kinds, glowing eyes.
 */
public final class KindZombieRenderer
        extends MobRenderer<Zombie, ZombieRenderState, DesignModel<ZombieRenderState>> {
    private final ZombieKind kind;
    private final Identifier skin;
    // The SCALE attribute already sizes the kind; this only corrects designs drawn at another size.
    private final float scale;
    private final ItemModelResolver items;
    private static final int EXPLORER_TINT = 0x99ffffff;
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
        super(
                context,
                new DesignModel<>(
                        DesignModel.load(DolfinZombies.MOD_ID, kind.id()), DesignModel.Layer.BASE),
                .5f);
        this.kind = kind;
        skin = texture(kind, "");
        scale = model.data().scale() / kind.defaults().get("escala").getAsFloat();
        items = context.getItemModelResolver();
        addLayer(
                new ExteriorLayer<>(
                        this,
                        new DesignModel<>(model.data(), DesignModel.Layer.EXTERIOR),
                        texture(kind, "_exterior"),
                        kind == ZombieKind.EXPLORADOR ? EXPLORER_TINT : 0));
        addLayer(new ItemInHandLayer<>(this));
        if (GLOW.contains(kind))
            addLayer(
                    new RenderLayer<ZombieRenderState, DesignModel<ZombieRenderState>>(this) {
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
                                            RenderTypes.eyes(texture(kind, "_ojos")),
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
        ArmedEntityRenderState.extractArmedEntityRenderState(zombie, state, items, partial);
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
        return kind == ZombieKind.EXPLORADOR ? EXPLORER_TINT : super.getModelTint(state);
    }

    @Override
    protected void scale(ZombieRenderState state, PoseStack pose) {
        float size = state.isBaby ? scale * .5f : scale;
        pose.scale(size, size, size);
    }
}
