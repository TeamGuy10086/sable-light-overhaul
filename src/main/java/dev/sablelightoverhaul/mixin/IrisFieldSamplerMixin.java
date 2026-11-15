package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.client.LightFields;
import dev.sablelightoverhaul.client.SpotShadows;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Binds the vanilla light field into whichever sampler set turns out to be a program's.
 *
 * <p>Every route into an Iris program is offered this sampler, because two of them were tried one at a time and
 * both answered "not here": {@code addLevelSamplers} accepted the name in some programs and the shader still
 * sampled zero, and {@code addCustomTextures} reported {@code bound=false} for all 240 programs of a session.
 * Guessing at one method per launch is what has made this take so long, so all four registration points are
 * hooked at once and each reports its own pass count and result. The one that binds wins; the others are inert
 * after the first successful bind and can be removed.
 *
 * <p>Iris' own code answers the last question this file had: {@code addCustomTextures} passes {@code null} for the
 * {@code GlSampler} when it registers a pack's own custom textures, so a null sampler is not a fault.
 */
@Mixin(targets = "net.irisshaders.iris.samplers.IrisSamplers", remap = false)
public abstract class IrisFieldSamplerMixin {
    /** One line per registration point, and only the first bind of each, so the log stays readable. */
    private static final java.util.Map<String, Boolean> REPORTED = new java.util.HashMap<>();
    private static final java.util.Map<String, Boolean> SHADOW_REPORTED = new java.util.HashMap<>();
    private static final java.util.Map<String, Integer> PASSES = new java.util.HashMap<>();

    private static void offer(String where, SamplerHolder holder) {
        try {
            holder.addDynamicSampler(TextureType.TEXTURE_2D,
                    dev.sablelightoverhaul.client.SmokeFlipbook::tankTexture, null, "sloTankSmokeAtlas");
            boolean smoke = holder.addDynamicSampler(TextureType.TEXTURE_2D,
                    dev.sablelightoverhaul.client.SmokeFlipbook::texture, null, "sloSmokeAtlas");
            if(smoke && REPORTED.putIfAbsent("smoke/"+where, Boolean.TRUE)==null)
                com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul: SMOKE ATLAS BOUND via {}",where);
            LightFields.ensureTexture();
            boolean bound = holder.addDynamicSampler(TextureType.TEXTURE_2D, LightFields::texture, null, "sloField");
            int pass = PASSES.merge(where, 1, Integer::sum);
            if (bound && REPORTED.putIfAbsent(where, Boolean.TRUE) == null) {
                com.mojang.logging.LogUtils.getLogger().info(
                        "Sable Light Overhaul: FIELD BOUND via {} on pass {} (texture {})",
                        where, pass, LightFields.texture());
            } else if (!bound && pass <= 1) {
                com.mojang.logging.LogUtils.getLogger().info(
                        "Sable Light Overhaul: field not declared by the first program through {}", where);
            }
            // The same channel carries the spot shadow map: a mod-owned texture, bound by name, is the only
            // way a composite pass can read data this mod computed (a composite pass cannot render geometry).
            SpotShadows.ensureTexture();
            boolean shadow = holder.addDynamicSampler(TextureType.TEXTURE_2D, SpotShadows::texture, null,
                    "sloSpotShadow");
            if (shadow && SHADOW_REPORTED.putIfAbsent(where, Boolean.TRUE) == null) {
                com.mojang.logging.LogUtils.getLogger().info(
                        "Sable Light Overhaul: SPOT SHADOW BOUND via {} (texture {})", where, SpotShadows.texture());
            }
        } catch (RuntimeException | LinkageError ex) {
            com.mojang.logging.LogUtils.getLogger().error(
                    "Sable Light Overhaul: offering the field through " + where + " failed", ex);
        }
    }

    @Inject(method = "addLevelSamplers", at = @At("TAIL"))
    private static void slo$level(SamplerHolder holder, WorldRenderingPipeline pipeline, AbstractTexture whitePixel,
                                  boolean hasTexture, boolean hasLightmap, boolean hasOverlay, CallbackInfo ci) {
        offer("addLevelSamplers", holder);
    }

    @Inject(method = "addCustomTextures", at = @At("TAIL"))
    private static void slo$custom(SamplerHolder holder, Object2ObjectMap<String, ?> customTextures, CallbackInfo ci) {
        offer("addCustomTextures", holder);
    }

    @Inject(method = "addCompositeSamplers", at = @At("TAIL"))
    private static void slo$composite(SamplerHolder holder, RenderTargets renderTargets, CallbackInfo ci) {
        offer("addCompositeSamplers", holder);
    }

    @Inject(method = "addWorldDepthSamplers", at = @At("TAIL"))
    private static void slo$depth(SamplerHolder holder, RenderTargets renderTargets, CallbackInfo ci) {
        offer("addWorldDepthSamplers", holder);
    }
}
