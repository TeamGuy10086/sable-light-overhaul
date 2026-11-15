package dev.sablelightoverhaul.client;

import java.lang.reflect.Method;
import net.neoforged.fml.ModList;

/**
 * Optional Iris integration, resolved reflectively so Iris stays a non-dependency and none of its
 * classes are touched on a dedicated server. Used only to decide which render stage the GPU light
 * pass runs on: with a shader pack the main framebuffer only holds the final composited image after
 * Iris has finished (the {@code AFTER_LEVEL} stage); without one, opaque terrain is complete at
 * {@code AFTER_CUTOUT_BLOCKS}.
 */
public final class IrisCompat {
    private static boolean resolved;
    private static Object api;
    private static Method isShaderPackInUse;
    private static Method getCurrentPack;
    private static Object lastPack;
    private static PackBridge packBridge = PackBridge.NONE;

    private enum PackBridge { NONE, PHOTON, COMPLEMENTARY, MELLOW }

    private IrisCompat() {}

    public static boolean shaderPackInUse() {
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
                    "Sable Light Overhaul: Iris is present but its shader-pack state could not be read; treating shaders as inactive", ex);
            return false;
        }
    }

    public static boolean sloPackInUse() {
        return bridge() != PackBridge.NONE;
    }

    public static boolean premultipliedParticles() {
        return bridge() == PackBridge.PHOTON;
    }

    /** Read Iris' parsed options, so a renamed archive and a pack reload both work. */
    private static PackBridge bridge() {
        if (!shaderPackInUse() || getCurrentPack == null) return PackBridge.NONE;
        try {
            Object pack = ((java.util.Optional<?>) getCurrentPack.invoke(null)).orElse(null);
            if (pack == lastPack) return packBridge;
            lastPack = pack;
            packBridge = PackBridge.NONE;
            if (pack == null) return packBridge;
            Object options = pack.getClass().getMethod("getShaderPackOptions").invoke(pack);
            Object set = options.getClass().getMethod("getOptionSet").invoke(options);
            var flags = (java.util.Map<?, ?>) set.getClass().getMethod("getBooleanOptions").invoke(set);
            if (flags.containsKey("SLO_COMPLEMENTARY_BRIDGE")) {
                packBridge = PackBridge.COMPLEMENTARY;
            } else if (flags.containsKey("SLO_MELLOW_BRIDGE")) {
                packBridge = PackBridge.MELLOW;
            } else if (flags.containsKey("SLO_TAA_SMOKE") && flags.containsKey("SLO_RT13_ALPHA")) {
                packBridge = PackBridge.PHOTON;
            }
            return packBridge;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            getCurrentPack = null;
            com.mojang.logging.LogUtils.getLogger().warn(
                    "Sable Light Overhaul: Iris shader-pack capabilities could not be read", ex);
            return PackBridge.NONE;
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
                    "Sable Light Overhaul: Iris API entry point unavailable; treating shaders as inactive", ex);
        }
        try {
            getCurrentPack = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPack");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "Sable Light Overhaul: Iris pack capabilities entry point unavailable", ex);
        }
    }
}
