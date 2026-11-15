package dev.sablelightoverhaul.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL45;

/** Borrows Voxy's current depth attachment. Voxy retains ownership of the texture. */
public final class NativeVoxyDepth {
    private static final Matrix4f INVERSE = new Matrix4f();
    private static final Matrix4f BLIT_PROJECTION = new Matrix4f();
    private static int texture;
    private static boolean zeroToOne;
    private static boolean blitZeroToOne;
    private static boolean unavailable;
    private static Field framebuffer, properties, mvp, textureId, vanillaProjection, modelView;
    private static Method depthTexture, depthConvention;

    private NativeVoxyDepth() {}

    public static void clear() { texture = 0; }
    public static int texture() { return texture; }
    public static Matrix4f inverse() { return INVERSE; }
    public static Matrix4f blitProjection() { return BLIT_PROJECTION; }
    public static int zeroToOne() { return zeroToOne ? 1 : 0; }
    public static int blitZeroToOne() { return blitZeroToOne ? 1 : 0; }

    public static void capture(Object pipeline, Object viewport, int framebufferId) {
        if (unavailable || IrisCompat.shaderPackInUse()) return;
        var mode = ClientFlashes.effectiveMode();
        if (mode != dev.sablelightoverhaul.LightingMode.SloOnly
                && mode != dev.sablelightoverhaul.LightingMode.DynamicOnly) return;
        var target = net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
        if (framebufferId != target.frameBufferId) return;
        try {
            if (framebuffer == null) {
                Class<?> pipelineType = pipeline.getClass();
                framebuffer = pipelineType.getField("fb");
                properties = pipelineType.getField("properties");
                mvp = viewport.getClass().getField("MVP");
                vanillaProjection = viewport.getClass().getField("vanillaProjection");
                modelView = viewport.getClass().getField("modelView");
                depthTexture = framebuffer.getType().getMethod("getDepthTex");
                textureId = depthTexture.getReturnType().getField("id");
                depthConvention = properties.getType().getMethod("isZero2One");
            }
            Object depth = depthTexture.invoke(framebuffer.get(pipeline));
            // Voxy's projection convention and OpenGL's viewport transform are independent.
            // Its normal final blit uses the former when writing gl_FragDepth into vanilla.
            blitZeroToOne = (boolean) depthConvention.invoke(properties.get(pipeline));
            var caps = GL.getCapabilities();
            zeroToOne = (caps.OpenGL45 || caps.GL_ARB_clip_control)
                    && GL11.glGetInteger(GL45.GL_CLIP_DEPTH_MODE) == GL45.GL_ZERO_TO_ONE;
            INVERSE.set((Matrix4f) mvp.get(viewport)).invert();
            BLIT_PROJECTION.set((Matrix4f) vanillaProjection.get(viewport))
                    .mul((Matrix4f) modelView.get(viewport));
            texture = textureId.getInt(depth);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            unavailable = true;
            clear();
            com.mojang.logging.LogUtils.getLogger().warn("SLO could not borrow Voxy depth; using scene depth", failure);
        }
    }
}
