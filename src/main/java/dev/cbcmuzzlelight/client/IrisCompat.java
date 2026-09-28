package dev.cbcmuzzlelight.client;

import java.lang.reflect.Method;
import net.neoforged.fml.ModList;

/**
 * Optional Iris integration, resolved reflectively so Iris stays a non-dependency and none of its
 * classes are touched on a dedicated server. Used only to decide which render stage the GPU light
 * pass runs on: with a shader pack the main framebuffer only holds the final composited image after
 * Iris has finished (the {@code AFTER_LEVEL} stage); without one, opaque terrain is complete at
 * {@code AFTER_CUTOUT_BLOCKS}.
 */
final class IrisCompat {
    private static boolean resolved;
    private static Object api;
    private static Method isShaderPackInUse;

    private IrisCompat() {}

    static boolean shaderPackInUse() {
        if (!ModList.get().isLoaded("iris")) return false;
        if (!resolved) resolve();
        if (api == null || isShaderPackInUse == null) return false;
        try {
            Object result = isShaderPackInUse.invoke(api);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            // Stop retrying and fall back to the no-shader path.
            api = null;
            isShaderPackInUse = null;
            com.mojang.logging.LogUtils.getLogger().warn(
                    "CBC Muzzle Light: Iris is present but its shader-pack state could not be read; treating shaders as inactive", ex);
            return false;
        }
    }

    private static void resolve() {
        resolved = true;
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = apiClass.getMethod("getInstance").invoke(null);
            isShaderPackInUse = apiClass.getMethod("isShaderPackInUse");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            api = null;
            isShaderPackInUse = null;
            com.mojang.logging.LogUtils.getLogger().warn(
                    "CBC Muzzle Light: Iris API entry point unavailable; treating shaders as inactive", ex);
        }
    }
}
