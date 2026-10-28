package com.dolfin.space.client.mobs;

import com.dolfin.space.*;
import com.dolfin.space.client.mobs.DesignModel.Layer;
import com.dolfin.space.mobs.*;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Each Kepler mob draws its own design model ({@link DesignModel}) with its texture, an inflated
 * relief layer ({@link ExteriorLayer}) with {@code <id>_exterior.png} and, for the spiders,
 * full-bright eyes from {@code <id>_ojos.png}. Humanoids keep their held items; the creeper keeps
 * its swelling, flash and charge aura.
 */
public final class MobsClient {
    private MobsClient() {}

    private static Identifier texture(String id) {
        return DolfinSpace.id("textures/entity/" + id + ".png");
    }

    private static DesignModel.Data data(String id) {
        return DesignModel.load(DolfinSpace.MOD_ID, id);
    }

    public static void init() {
        EntityRendererRegistry.register(
                MobsModule.PARASITE, ctx -> new DesignRenderer<Parasite>(ctx, "parasito", true));
        EntityRendererRegistry.register(
                MobsModule.STALKER, ctx -> new DesignRenderer<Stalker>(ctx, "acechador", true));
        EntityRendererRegistry.register(
                MobsModule.WORM, ctx -> new DesignRenderer<Worm>(ctx, "gusano", false));
        EntityRendererRegistry.register(
                MobsModule.SENTINEL, ctx -> new DesignRenderer<Sentinel>(ctx, "centinela", false));
        EntityRendererRegistry.register(
                MobsModule.ZOMBIE,
                ctx ->
                        new ArmedDesignRenderer<SpaceZombie, ZombieRenderState>(
                                ctx, "zombi_espacial", ZombieRenderState::new));
        EntityRendererRegistry.register(
                MobsModule.SKELETON,
                ctx ->
                        new ArmedDesignRenderer<SpaceSkeleton, SkeletonRenderState>(
                                ctx, "esqueleto_espacial", SkeletonRenderState::new));
        EntityRendererRegistry.register(MobsModule.CREEPER, CreeperDesignRenderer::new);
        EntityRendererRegistry.register(
                MobsModule.PROJECTILE, ctx -> new ThrownItemRenderer<>(ctx, .6F, true));
        EntityRendererRegistry.register(
                MobsModule.CRYSTAL_ARROW,
                ctx ->
                        new TippableArrowRenderer(ctx) {
                            @Override
                            protected Identifier getTextureLocation(
                                    TippableArrowRenderState state) {
                                return texture("flecha_cristal");
                            }
                        });
        EntityRendererRegistry.register(MobsModule.ACID_CLOUD, NoopRenderer::new);
        HudElementRegistry.addLast(
                DolfinSpace.id("parasite_tint"),
                (gui, delta) -> {
                    var player = Minecraft.getInstance().player;
                    if (player != null
                            && player.getPassengers().stream().anyMatch(e -> e instanceof Parasite))
                        gui.fill(0, 0, gui.guiWidth(), gui.guiHeight(), 0x3033cc44);
                });
    }

    /** Relief layer plus, if asked, full-bright eyes; shared by every Kepler renderer. */
    private static <S extends LivingEntityRenderState>
            List<RenderLayer<S, DesignModel<S>>> designLayers(
                    RenderLayerParent<S, DesignModel<S>> renderer, String id, boolean eyes) {
        List<RenderLayer<S, DesignModel<S>>> layers = new ArrayList<>();
        layers.add(
                new ExteriorLayer<>(
                        renderer,
                        new DesignModel<>(renderer.getModel().data(), Layer.EXTERIOR),
                        texture(id + "_exterior")));
        if (!eyes) return layers;
        layers.add(
                new RenderLayer<S, DesignModel<S>>(renderer) {
                    @Override
                    public void submit(
                            PoseStack pose,
                            SubmitNodeCollector collector,
                            int light,
                            S state,
                            float y,
                            float x) {
                        collector
                                .order(1)
                                .submitModel(
                                        getParentModel(),
                                        state,
                                        pose,
                                        RenderTypes.eyes(texture(id + "_ojos")),
                                        15728880,
                                        OverlayTexture.NO_OVERLAY,
                                        0);
                    }
                });
        return layers;
    }

    /** Crawlers and floaters: the design and its animation, nothing held. */
    private static final class DesignRenderer<T extends Mob>
            extends MobRenderer<T, LivingEntityRenderState, DesignModel<LivingEntityRenderState>> {
        private final Identifier texture;

        DesignRenderer(EntityRendererProvider.Context ctx, String id, boolean eyes) {
            super(ctx, new DesignModel<>(data(id), Layer.BASE), .5F);
            texture = texture(id);
            designLayers(this, id, eyes).forEach(this::addLayer);
        }

        @Override
        public LivingEntityRenderState createRenderState() {
            return new LivingEntityRenderState();
        }

        @Override
        public Identifier getTextureLocation(LivingEntityRenderState state) {
            return texture;
        }
    }

    /** Humanoids that hold items (the space skeleton's bow, the zombie's weapon). */
    private static final class ArmedDesignRenderer<T extends Mob, S extends ArmedEntityRenderState>
            extends MobRenderer<T, S, DesignModel<S>> {
        private final Identifier texture;
        private final Supplier<S> states;
        private final ItemModelResolver items;

        ArmedDesignRenderer(EntityRendererProvider.Context ctx, String id, Supplier<S> states) {
            super(ctx, new DesignModel<>(data(id), Layer.BASE), .5F);
            texture = texture(id);
            this.states = states;
            items = ctx.getItemModelResolver();
            designLayers(this, id, false).forEach(this::addLayer);
            addLayer(new ItemInHandLayer<>(this));
        }

        @Override
        public S createRenderState() {
            return states.get();
        }

        @Override
        public void extractRenderState(T mob, S state, float partial) {
            super.extractRenderState(mob, state, partial);
            ArmedEntityRenderState.extractArmedEntityRenderState(
                    (LivingEntity) mob, state, items, partial);
        }

        @Override
        public Identifier getTextureLocation(S state) {
            return texture;
        }

        @Override
        protected void scale(S state, PoseStack pose) {
            if (state.isBaby) pose.scale(.5F, .5F, .5F);
        }
    }

    /** The alien creeper: vanilla swelling, white flash and lightning-charge aura on the design. */
    private static final class CreeperDesignRenderer
            extends MobRenderer<Creeper, CreeperRenderState, DesignModel<CreeperRenderState>> {
        private static final Identifier AURA =
                Identifier.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
        private final Identifier texture = texture("creeper_espacial");

        CreeperDesignRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new DesignModel<>(data("creeper_espacial"), Layer.BASE), .5F);
            designLayers(this, "creeper_espacial", false).forEach(this::addLayer);
            var aura = new DesignModel<CreeperRenderState>(model.data(), Layer.AURA);
            addLayer(
                    new EnergySwirlLayer<>(this) {
                        @Override
                        protected boolean isPowered(CreeperRenderState state) {
                            return state.isPowered;
                        }

                        @Override
                        protected float xOffset(float t) {
                            return t * .01F;
                        }

                        @Override
                        protected Identifier getTextureLocation() {
                            return AURA;
                        }

                        @Override
                        protected DesignModel<CreeperRenderState> model() {
                            return aura;
                        }
                    });
        }

        @Override
        public CreeperRenderState createRenderState() {
            return new CreeperRenderState();
        }

        @Override
        public void extractRenderState(Creeper creeper, CreeperRenderState state, float partial) {
            super.extractRenderState(creeper, state, partial);
            state.swelling = creeper.getSwelling(partial);
            state.isPowered = creeper.isPowered();
        }

        @Override
        public Identifier getTextureLocation(CreeperRenderState state) {
            return texture;
        }

        @Override
        protected void scale(CreeperRenderState state, PoseStack pose) {
            float fuse = state.swelling;
            float shake = 1 + Mth.sin(fuse * 100) * fuse * .01F;
            fuse = Mth.clamp(fuse, 0, 1);
            fuse *= fuse * fuse;
            float width = (1 + fuse * .4F) * shake, height = (1 + fuse * .1F) / shake;
            pose.scale(width, height, width);
        }

        @Override
        protected float getWhiteOverlayProgress(CreeperRenderState state) {
            float fuse = state.swelling;
            return (int) (fuse * 10) % 2 == 0 ? 0 : Mth.clamp(fuse, .5F, 1);
        }
    }
}
