package com.dolfin.space.client.mobs;

import com.dolfin.space.*;
import com.dolfin.space.mobs.*;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.resources.Identifier;

/** Vanilla geometry/animations, mod textures, scaled to the registered collision dimensions. */
public final class MobsClient {
    private MobsClient() {}

    private static Identifier texture(String id) {
        return DolfinSpace.id("textures/entity/" + id + ".png");
    }

    public static void init() {
        EntityRendererRegistry.register(
                MobsModule.PARASITE,
                ctx ->
                        new SpiderRenderer<Parasite>(ctx) {
                            @Override
                            public Identifier getTextureLocation(LivingEntityRenderState state) {
                                return texture("parasito");
                            }

                            @Override
                            protected void scale(LivingEntityRenderState state, PoseStack pose) {
                                pose.scale(.36F, .5F, .36F);
                            }
                        });
        EntityRendererRegistry.register(
                MobsModule.STALKER,
                ctx ->
                        new SpiderRenderer<Stalker>(ctx) {
                            @Override
                            public Identifier getTextureLocation(LivingEntityRenderState state) {
                                return texture("acechador");
                            }

                            @Override
                            protected void scale(LivingEntityRenderState state, PoseStack pose) {
                                pose.scale(.85F, .9F, .85F);
                            }
                        });
        EntityRendererRegistry.register(
                MobsModule.WORM,
                ctx ->
                        new SilverfishRenderer(ctx) {
                            @Override
                            public Identifier getTextureLocation(LivingEntityRenderState state) {
                                return texture("gusano");
                            }

                            @Override
                            protected void scale(LivingEntityRenderState state, PoseStack pose) {
                                pose.scale(1.3F, 1.4F, 1.3F);
                            }
                        });
        EntityRendererRegistry.register(
                MobsModule.SENTINEL,
                ctx ->
                        new GhastRenderer(ctx) {
                            @Override
                            public Identifier getTextureLocation(GhastRenderState state) {
                                return texture("centinela");
                            }

                            @Override
                            protected void scale(GhastRenderState state, PoseStack pose) {
                                pose.scale(.25F, .25F, .25F);
                            }
                        });
        EntityRendererRegistry.register(
                MobsModule.ZOMBIE,
                ctx ->
                        new ZombieRenderer(ctx) {
                            @Override
                            public Identifier getTextureLocation(ZombieRenderState state) {
                                return texture("zombi_espacial");
                            }
                        });
        EntityRendererRegistry.register(
                MobsModule.SKELETON,
                ctx ->
                        new SkeletonRenderer(ctx) {
                            @Override
                            public Identifier getTextureLocation(SkeletonRenderState state) {
                                return texture("esqueleto_espacial");
                            }
                        });
        EntityRendererRegistry.register(
                MobsModule.CREEPER,
                ctx ->
                        new CreeperRenderer(ctx) {
                            @Override
                            public Identifier getTextureLocation(CreeperRenderState state) {
                                return texture("creeper_espacial");
                            }
                        });
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
}
