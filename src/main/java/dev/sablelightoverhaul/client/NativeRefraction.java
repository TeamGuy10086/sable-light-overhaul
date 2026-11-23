package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.sablelightoverhaul.SableLightOverhaul;
import dev.sablelightoverhaul.client.ExtraConfig.ShockwaveMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;

final class NativeRefraction {
    private static ShaderInstance shader;
    private static TextureTarget background;

    private NativeRefraction() {}

    static void registerShader(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    SableLightOverhaul.ID + ":slo_native_refract", DefaultVertexFormat.POSITION),
                    instance -> shader = instance);
        } catch (java.io.IOException failure) {
            com.mojang.logging.LogUtils.getLogger().warn("SLO native refraction shader unavailable", failure);
        }
    }

    static void render(RenderLevelStageEvent event) {
        if (shader == null || !ClientFlashes.sloOnly() || !ExtraConfig.SLO_ONLY_REFRACTION.get()
                || !ExtraConfig.SHOCKWAVE_ENABLED.get() || ExtraConfig.SHOCKWAVE_REFRACT.get() <= 0.0
                || ExtraConfig.effectiveShockwaveMode() == ShockwaveMode.PARTICLES
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) return;
        var waves = Shockwave.waves();
        if (waves.isEmpty()) return;
        var camera = event.getCamera().getPosition();
        var projection = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        var main = Minecraft.getInstance().getMainRenderTarget();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean copied = false;
        for (var wave : waves) {
            double size = wave.meshSize * ExtraConfig.SHOCKWAVE_MESH_SCALE.get();
            double radius = ShockwaveMesh.radiusAt(size, wave.meshTicks, wave.meshExpansion, wave.age + partialTick);
            if (!(radius > 0.01)) continue;
            var center = wave.center;
            float[] rect = GpuLightRenderer.boxRect(new AABB(center, center).inflate(radius),
                    projection, camera.x, camera.y, camera.z);
            if (rect == null || rect[0] >= rect[2] || rect[1] >= rect[3]) continue;
            if (!copied) {
                if (background == null || background.width != main.width || background.height != main.height) {
                    if (background != null) background.destroyBuffers();
                    background = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
                    if (main.isStencilEnabled()) background.enableStencil();
                }
                background.copyDepthFrom(main);
                com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
                com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, background.frameBufferId);
                GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                        GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
                main.bindWrite(false);
                copied = true;
            }
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            try {
                RenderSystem.setShader(() -> shader);
                shader.setSampler("Background", background.getColorTextureId());
                shader.setSampler("DepthSampler", background.getDepthTextureId());
                int lod = NativeVoxyDepth.texture();
                shader.setSampler("LodDepthSampler", lod > 0 ? lod : background.getDepthTextureId());
                shader.safeGetUniform("uHasLod").set(lod > 0 ? 1 : 0);
                shader.safeGetUniform("uLodZeroToOne").set(NativeVoxyDepth.zeroToOne());
                shader.safeGetUniform("uInvLodViewProj").set(NativeVoxyDepth.inverse());
                shader.safeGetUniform("uViewProj").set(projection);
                shader.safeGetUniform("InverseProjection").set(new Matrix4f(projection).invert());
                shader.safeGetUniform("ScreenSize").set((float) main.width, (float) main.height);
                shader.safeGetUniform("Sphere").set((float) (center.x - camera.x), (float) (center.y - camera.y),
                        (float) (center.z - camera.z), (float) radius);
                shader.safeGetUniform("Refraction").set(ExtraConfig.SHOCKWAVE_REFRACT.get().floatValue()
                        * ShockwaveMesh.lifeFade(radius / size));
                var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
                GpuLightRenderer.quad(buffer, rect);
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            } finally {
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
            }
        }
    }
}
