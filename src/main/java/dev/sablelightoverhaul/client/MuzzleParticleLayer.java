package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import org.joml.Vector4f;

/**
 * The single sorted muzzle batch, shared by the flame/gas volume, the soft smoke puffs and the
 * tank-owned CBC fallback sprite.
 *
 * <p>One render type, one material contract, because the two that existed in 0.1.520-0.1.533
 * disagreed and the disagreement was the defect: {@code SMOKE} wrote depth (so a puff's almost
 * transparent rim became an occluder and cut the puffs behind it) and blended with the default
 * {@code SRC_ALPHA} although Photon had already premultiplied the colour it writes to RT13 (so the
 * falloff was squared and its edge read as a hard transparent outline). Under a Photon pack the
 * custom shader is therefore not bound at all: Iris draws the batch through
 * {@code gbuffers_all_translucent}, which knows our vertex markers (see sloMuzzleKind in the patch)
 * and writes premultiplied colour, so {@code ONE / ONE_MINUS_SRC_ALPHA} is the only correct state.
 * Complementary and the native shader emit straight alpha and keep the default blend.
 *
 * <p>No depth write, ever: the smoke is a translucent layer. Covering the LoD terrain is the
 * composite's job (the RT13 coverage test in c1_blend_layers), not the particle's.
 */
public final class MuzzleParticleLayer {
    private MuzzleParticleLayer() {}
    /**
     * The in-game A/B switch for the rim investigation; {@link #FIXED} is the shipped contract.
     *
     * <p>Four states, each removing exactly one candidate so a screenshot can be attributed:
     * {@link #BLEND} keeps the correct depth state but blends the old way, {@link #DEPTH} keeps the
     * correct blend but writes depth, {@link #SINGLE} keeps the correct material but drops two of
     * the three lobes.
     */
    public enum Mode {
        FIXED("shipped: no depth write, premultiplied blend under a pack, three lobes"),
        BLEND("no depth write, default SRC_ALPHA (the 0.1.520 blend)"),
        DEPTH("depth write on, premultiplied blend (the 0.1.520 depth)"),
        SINGLE("shipped blend/depth, one lobe instead of three");
        private final String detail;
        Mode(String detail) { this.detail = detail; }
        public String detail() { return detail; }
    }
    private static volatile Mode mode = Mode.FIXED;
    public static Mode mode() { return mode; }
    public static void mode(Mode next) { mode = next == null ? Mode.FIXED : next; }
    /** One line describing the state that will actually be set, for the command and the log. */
    public static String describe() {
        boolean depth = mode == Mode.DEPTH;
        boolean premultiplied = ClientFlashes.inPackLighting() && IrisCompat.premultipliedParticles() && mode != Mode.BLEND;
        return "depthMask=" + depth + " blend=" + (premultiplied ? "ONE/ONE_MINUS" : "SRC_ALPHA")
            + " lobes=" + (mode == Mode.SINGLE ? 1 : 3);
    }
    private static net.minecraft.client.renderer.ShaderInstance shader;
    private static final Vector4f AUTO_GAS_A = new Vector4f(), AUTO_GAS_B = new Vector4f();
    public static void registerShader(net.neoforged.neoforge.client.event.RegisterShadersEvent event) {
        try {
            event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),
                    "sablelightoverhaul:muzzle_particles",DefaultVertexFormat.PARTICLE), value -> shader=value);
        } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
    }
    /** Photon-only material contract in otherwise unused low lightmap bits; no flags on vanilla fallback. */
    public static int markLight(int packed, boolean flame) {
        return ClientFlashes.inPackLighting() ? dev.sablelightoverhaul.GasVertexData.materialLight(packed,flame) : packed;
    }
    public static boolean proceduralGas() {
        // Fragment ray integration needs the pack's opaque/LOD depth. Other packs/native use the geometric fallback.
        return ClientFlashes.inPackLighting();
    }
    public static boolean proceduralSmoke() {
        return ClientFlashes.inPackLighting()||!IrisCompat.shaderPackInUse();
    }
    /** Native fallback shader has no Iris bridge, so the same two vectors are pushed here. */
    private static void applyGasShape() {
        if (shader == null) return;
        var target = net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
        int lod = ClientFlashes.sloOnly() ? NativeVoxyDepth.texture() : 0;
        shader.setSampler("LodDepthSampler", lod);
        shader.safeGetUniform("uHasLod").set(lod > 0 ? 1 : 0);
        shader.safeGetUniform("uLodZeroToOne").set(NativeVoxyDepth.zeroToOne());
        shader.safeGetUniform("uInvLodViewProj").set(NativeVoxyDepth.inverse());
        shader.safeGetUniform("sloNativeScreenSize").set((float) target.width, (float) target.height);
        shader.setSampler("sloSmokeAtlas",SmokeFlipbook.texture());
        shader.setSampler("sloTankSmokeAtlas",SmokeFlipbook.tankTexture());
        shader.safeGetUniform("sloSmokeOptions").set(BigSmokeConfig.FLIPBOOK.get()?1f:0f,
            BigSmokeConfig.SIMPLE_LIGHTING.get()?1f:0f,BigSmokeConfig.AMBIENT_GAIN.get().floatValue(),BigSmokeConfig.EMISSION_GAIN.get().floatValue());
        shader.safeGetUniform("sloAutoGas").set(AUTO_GAS_A.set(
            MuzzleClientConfig.AUTO_LOBE_COUNT.get().floatValue(),
            MuzzleClientConfig.AUTO_LOBE_STRENGTH.get().floatValue(),
            MuzzleClientConfig.AUTO_LOBE_TWIST.get().floatValue(),
            MuzzleClientConfig.AUTO_LOBE_LENGTH_SPREAD.get().floatValue()));
        shader.safeGetUniform("sloAutoGas2").set(AUTO_GAS_B.set(
            MuzzleClientConfig.AUTO_LOBE_SHARPNESS.get().floatValue(),
            MuzzleClientConfig.AUTO_CORE_FOCUS.get().floatValue(),
            MuzzleClientConfig.AUTO_EDGE_SOFTNESS.get().floatValue(),
            0.0F));
    }
    private static void prepare() {
        if (!IrisCompat.shaderPackInUse() && shader != null) RenderSystem.setShader(() -> shader);
        if (!IrisCompat.shaderPackInUse()) applyGasShape();
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
        RenderSystem.enableBlend();
    }
    /** Flame/gas and smoke share one translucent contract: premultiplied target, no depth write. */
    public static final ParticleRenderType TYPE = new ParticleRenderType() {
        @Override public BufferBuilder begin(Tesselator tessellator, TextureManager textures) {
            prepare();
            // DEPTH and BLEND exist only to reproduce one 0.1.520 defect at a time; FIXED is the
            // shipped state. Keeping them behind the command means the default path stays provably
            // correct while a screenshot can still separate depth from blending.
            RenderSystem.depthMask(mode == Mode.DEPTH);
            if (ClientFlashes.inPackLighting() && IrisCompat.premultipliedParticles() && mode != Mode.BLEND) RenderSystem.blendFunc(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            else RenderSystem.defaultBlendFunc();
            return tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }
        @Override public boolean isTranslucent() { return true; }
        @Override public String toString() { return "SLO_MUZZLE_TRANSLUCENT"; }
    };
}
