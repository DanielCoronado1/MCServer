package com.dolfin.space.mixin.client;

import com.dolfin.space.DolfinSpace;
import com.dolfin.space.KeplerDimension;
import com.dolfin.space.SpaceConfig;
import com.dolfin.space.client.DolfinSpaceClient;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.world.level.MoonPhase;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * Customizes {@link SkyRenderer} for {@code dolfinspace:kepler}: renders a black space sky with
 * permanent stars, suppresses atmospheric sunrise glow, and draws the Earth celestial sprite.
 */
@Mixin(SkyRenderer.class)
public abstract class KeplerSkyMixin {
    @Unique private static final float EARTH_HEIGHT = 100.0F;
    @Unique private static final float EARTH_SIZE = 28.0F;

    @Shadow @Final private TextureAtlas celestialsAtlas;

    @Shadow @Final private RenderSystem.AutoStorageIndexBuffer quadIndices;

    @Shadow
    private static GpuBuffer buildCelestialQuad(String label, TextureAtlasSprite sprite) {
        throw new AssertionError();
    }

    @Shadow
    private Matrix4f applyCelestialBodyTransform(PoseStack poseStack, float height, float size) {
        throw new AssertionError();
    }

    @Shadow
    private void drawCelestialBody(
            Supplier<String> label,
            RenderPass pass,
            GpuBufferSlice transformSlice,
            GpuBuffer indexBuffer,
            GpuBuffer vertexBuffer,
            int baseVertex) {
        throw new AssertionError();
    }

    @Unique private GpuBuffer dolfinspace$earthBuffer;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dolfinspace$initEarthQuad(
            TextureManager textureManager,
            AtlasManager atlasManager,
            RenderTarget renderTarget,
            CallbackInfo ci) {
        TextureAtlasSprite earthSprite =
                this.celestialsAtlas.getSprite(DolfinSpaceClient.EARTH_SPRITE);
        this.dolfinspace$earthBuffer = buildCelestialQuad("Earth quad", earthSprite);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void dolfinspace$configureKeplerSkyState(
            ClientLevel level,
            float partialTick,
            Camera camera,
            SkyRenderState state,
            CallbackInfo ci) {
        boolean inKepler = KeplerDimension.isKepler(level.dimension());
        DolfinSpaceClient.renderingKeplerSky = inKepler;
        if (!inKepler) {
            return;
        }

        SpaceConfig cfg = DolfinSpace.config();
        float starBrightness =
                cfg != null ? (float) cfg.current().cliente().brilloEstrellasKepler() : 0.90F;

        state.skyColor = new Vector3f(0.0F, 0.0F, 0.0F);
        state.sunriseAndSunsetColor = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);
        state.rainBrightness = 1.0F;
        state.starBrightness = Math.max(state.starBrightness, starBrightness);
        state.shouldRenderDarkDisc = false;
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void dolfinspace$renderEarthInKepler(
            RenderPass pass,
            MoonPhase phase,
            float rainBrightness,
            PoseStack poseStack,
            CallbackInfo ci) {
        if (!DolfinSpaceClient.renderingKeplerSky || this.dolfinspace$earthBuffer == null) {
            return;
        }
        SpaceConfig cfg = DolfinSpace.config();
        if (cfg != null && !cfg.current().cliente().mostrarTierraEnCielo()) {
            ci.cancel();
            return;
        }

        Matrix4f transform = this.applyCelestialBodyTransform(poseStack, EARTH_HEIGHT, EARTH_SIZE);
        GpuBufferSlice uniformSlice =
                RenderSystem.getDynamicUniforms()
                        .writeTransform(transform, new Vector4f(1.0F, 1.0F, 1.0F, rainBrightness));
        this.drawCelestialBody(
                () -> "Earth",
                pass,
                uniformSlice,
                this.quadIndices.getBuffer(6),
                this.dolfinspace$earthBuffer,
                0);
        ci.cancel();
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void dolfinspace$closeEarthBuffer(CallbackInfo ci) {
        if (this.dolfinspace$earthBuffer != null) {
            this.dolfinspace$earthBuffer.close();
            this.dolfinspace$earthBuffer = null;
        }
    }
}
