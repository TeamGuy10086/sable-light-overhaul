package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.sablelightoverhaul.AreaLight;
import dev.sablelightoverhaul.SableLightOverhaul;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryStack;

/**
 * GPU dynamic-light verification pass. Light volume geometry is additive-blended on top of the
 * already-rendered opaque terrain (Sodium + Sable both write the main depth buffer), and the
 * fragment shader reconstructs the visible surface's world position from the depth buffer to
 * evaluate {@link AreaLight#at} on the GPU. Light changes only update a uniform array; no chunk
 * or section mesh is ever rebuilt.
 */
public final class GpuLightRenderer {
    public static final int MAX_LIGHTS = 32;
    private static final int FLOATS_PER_LIGHT = 20;
    private static final int MAX_FLOATS = MAX_LIGHTS * FLOATS_PER_LIGHT;

    private static volatile ShaderInstance shader;
    private static ShaderInstance glowShader;
    private static volatile List<AreaSnapshot> lights = List.of();
    private static volatile float[] lightData = new float[MAX_FLOATS];
    private static volatile int lightCount;
    private static final float[] TINTS = new float[MAX_LIGHTS * 3];
    private static final float[] RGB = new float[3];
    private static String[] lightKeys=new String[0];
    private static boolean hasMath;
    private static VertexBuffer lightBuffer;
    private static TextureTarget depthTarget;
    private static TextureTarget colorProbeTarget;
    private static long lastRenderLog;
    private static Matrix4f terrainViewProjection;
    private static Matrix4f terrainRenderSystemViewProjection;
    private static DepthPixel terrainPixel;
    private static boolean hadDynamicLight;
    private static long lastColorProbe;
    private static int capturedProgram = -1;
    private static List<ColorResult> pendingColorProbes = List.of();

    private record ColorProbe(int slot, String light, int x, int y, double expected, float power, int before) {}
    private record ColorResult(ColorProbe probe, int after, int isolated, int isolatedMain, int rawMain, float gpuPower) {}
    private static final int[][] PROBE_OFFSETS = {
        {0, 0}, {0, -16}, {0, -48}, {0, -112}, {0, 40}, {-32, -32}, {32, -32}, {-72, 0}, {72, 0}
    };

    private record DepthPixel(int x, int y, float projected, float depth, Vec3 receiver) {}
    private static final int[] SAVED_SAMPLERS = new int[3];
    private static final Vector4f SCRATCH = new Vector4f();
    /** Screen tile edge, in pixels, for the per-tile light lists. */
    private static final int TILE = 16;
    private static int tileTexture;
    private static int[] tileMasks = new int[0];
    private static int tilesX, tilesY, tileTextureWidth, tileTextureHeight;
    /** Reused corner buffers, so projecting a light allocates nothing. */
    private static final float[] CORNER_X = new float[8], CORNER_Y = new float[8], CORNER_Z = new float[8];
    private static final float NEAR_CLIP = 1.0E-4F;

    private GpuLightRenderer() {}

    public static void registerShader(RegisterShadersEvent event) {
        try {
            event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SableLightOverhaul.ID + ":slo_gpu_light", DefaultVertexFormat.POSITION),
                GpuLightRenderer::installShader
            );
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    SableLightOverhaul.ID + ":slo_native_glow", DefaultVertexFormat.POSITION),
                    instance -> glowShader = instance);
        } catch (Exception ex) {
            com.mojang.logging.LogUtils.getLogger().error("Sable Light Overhaul: failed to register GPU light shader", ex);
        }
    }

    /**
     * Chooses the render stage for the light pass.
     *
     * <p>SLO Only waits for opaque block entities and moving geometry as well as terrain.
     *
     * <p>With an Iris shader pack the terrain goes into Iris' own gbuffer and Iris writes the final
     * composited image into the main framebuffer at the end of {@code renderLevel}. NeoForge
     * dispatches {@code AFTER_LEVEL} after that call returns, so that is the only point where the
     * pass survives. Iris' final pass only disables depth writes, so the main depth buffer still
     * holds the scene depth there.
     */
    public static void onRenderLevelStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            finishColorProbes(Minecraft.getInstance().getMainRenderTarget());
        }
        if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY) {
            NativeVoxyDepth.clear();
            terrainViewProjection = null;
            terrainRenderSystemViewProjection = null;
            terrainPixel = null;
            pendingColorProbes = List.of();
        }
        boolean pack = IrisCompat.shaderPackInUse();
        // In the Advanced tier the patched pack emits the flash instead, so the screen-space
        // overlay stands down to avoid doubling the light.
        if (ClientFlashes.inPackLighting()) return;
        var wanted = pack
                ? net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL
                : ClientFlashes.sloOnly()
                ? net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES
                : net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS;
        if (!pack && ClientFlashes.sloOnly() && ExtraConfig.on(MuzzleClientConfig.ADV_DEBUG)
                && event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS
                && System.currentTimeMillis() - lastRenderLog > 2000L
                && lightCount > 0 && org.lwjgl.opengl.GL.getCapabilities().OpenGL45) {
            RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
            Vec3 hit = hitPosition();
            // Sodium supplies the matrices passed to its chunk draw when dispatching this event.
            terrainViewProjection = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
            terrainRenderSystemViewProjection = new Matrix4f(RenderSystem.getProjectionMatrix())
                    .mul(event.getModelViewMatrix());
            if (hit != null && target != null && target.useDepth) {
                terrainPixel = sampleDepth(terrainViewProjection, event.getCamera().getPosition(), hit,
                        target, target.getDepthTextureId());
            }
        }
        if (event.getStage() != wanted) return;
        render(event.getProjectionMatrix(), event.getModelViewMatrix(), event.getCamera());
    }

    private static void installShader(ShaderInstance instance) {
        shader = instance;
    }

    public static boolean ready() {
        return shader != null;
    }

    public static void clear() {
        lights = List.of();
        lightData = new float[MAX_FLOATS];
        lightCount = 0;
    }

    static double sampleLight(double x, double y, double z) {
        double value = 0.0;
        for (var snapshot : lights) value = Math.max(value, snapshot.at(x, y, z));
        return value;
    }

    /** Replaces the active light set with the given world-space area lights (no Sable plot receivers). */
    public static void setLights(List<AreaSnapshot> list) {setLights(list,java.util.Map.of());}
    public static void setLights(List<AreaSnapshot> list,java.util.Map<AreaSnapshot,String> keys) {
        int n = Math.min(list.size(), MAX_LIGHTS);
        float[] data = new float[MAX_FLOATS];
        int base = 0;
        lightKeys=new String[n];hasMath=false;
        List<AreaSnapshot> copy = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            AreaSnapshot snap = list.get(i);
            lightKeys[i]=keys.get(snap);hasMath|=HealthbarFlightBridge.mathKey(lightKeys[i]);
            base = writeLight(data, base, snap.light);
            data[base-FLOATS_PER_LIGHT+3]*=snap.radiance;
            copy.add(snap);
        }
        lightData = data;
        lightCount = n;
        lights = copy;
    }

    private static int writeLight(float[] data, int base, AreaLight l) {
        data[base++] = (float) l.x();
        data[base++] = (float) l.y();
        data[base++] = (float) l.z();
        data[base++] = (float) l.brightness();
        data[base++] = (float) l.dx();
        data[base++] = (float) l.dy();
        data[base++] = (float) l.dz();
        data[base++] = (float) l.axial();
        data[base++] = (float) l.lateral();
        data[base++] = (float) l.core();
        data[base++] = (float) l.middle();
        data[base++] = (float) l.tail();
        data[base++] = (float) l.rear();
        data[base++] = (float) l.vertical();
        data[base++] = (float) l.rearCutoff();
        float flags = 0.0F;
        if (l.directional()) flags += 1.0F;
        if (l.diamond()) flags += 2.0F;
        if (l.cone()) flags += 4.0F;
        if (l.beam()) flags += 8.0F;
        data[base++] = flags;
        data[base++] = (float) l.ux();
        data[base++] = (float) l.uy();
        data[base++] = (float) l.uz();
        data[base++] = 0.0F;
        return base;
    }

    /** Draws the completed opaque scene's light overlay on the render thread. */
    public static void render(Matrix4f projection, Matrix4f modelView, net.minecraft.client.Camera camera) {
        if (shader == null) return;
        boolean nativeOnly = ClientFlashes.sloOnly();
        Minecraft minecraft = Minecraft.getInstance();
        var missileGlows=dev.sablelightoverhaul.api.ClientMissileEffectsApi.collect(minecraft.level);
        if (lightCount <= 0 && missileGlows.isEmpty() && (!nativeOnly || !DebugFlares.hasStates() && IrisMuzzleBridge.beamCount() == 0)) return;
        var target = minecraft.getMainRenderTarget();
        if (target == null || !target.useDepth) return;

        var cameraPos = camera.getPosition();
        // The fragment shader combines every applicable light for each pixel. The projected
        // bounds only limit where to run it, so their union must be drawn once.
        Matrix4f projView = new Matrix4f(projection).mul(modelView);
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder builder = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        var rects = new ArrayList<ScreenQuad>(lights.size());
        float[] renderData = nativeOnly || hasMath ? lightData.clone() : lightData;
        for (int i = 0; i < lights.size(); i++) {
            // The light's own index is carried along: the shader's light array is indexed that way, so
            // the tile bit has to be the same number, not this quad's position in the list.
            AreaSnapshot snapshot = lights.get(i);
            AreaLight light = snapshot.light;
            double x = light.x(), y = light.y(), z = light.z();
            float[] rect;
            if(i<lightKeys.length&&HealthbarFlightBridge.mathKey(lightKeys[i])){
                var pos=HealthbarFlightBridge.position(lightKeys[i],minecraft.getTimer().getGameTimeDeltaPartialTick(false));
                int b=i*FLOATS_PER_LIGHT;
                if(pos==null){renderData[b+3]=0;continue;}
                x = pos.x; y = pos.y; z = pos.z;
                renderData[b]=(float)x;renderData[b+1]=(float)y;renderData[b+2]=(float)z;
                rect=nativeOnly ? null : boxRect(snapshot.bounds.move(x-light.x(),y-light.y(),z-light.z()),projView,cameraPos.x,cameraPos.y,cameraPos.z);
            }else rect=nativeOnly ? null : screenRect(snapshot, projView, cameraPos.x, cameraPos.y, cameraPos.z);
            if (nativeOnly) {
                int base = i * FLOATS_PER_LIGHT;
                renderData[base] = (float) (x - cameraPos.x);
                renderData[base + 1] = (float) (y - cameraPos.y);
                renderData[base + 2] = (float) (z - cameraPos.z);
            }
            // The native receiver may come from either vanilla or Voxy. Let the shader
            // evaluate the full spatial falloff without clipping it to CPU light bounds.
            if (nativeOnly) continue;
            if (rect == null) continue;
            if (rect[0] >= rect[2] || rect[1] >= rect[3]) continue;
            rects.add(new ScreenQuad(i, rect));
        }
        if (nativeOnly && lightCount > 0) {
            quad(builder, new float[]{-1F, -1F, 1F, 1F});
        } else if (!rects.isEmpty()) {
            float[] bounds = rects.getFirst().rect().clone();
            for (int i = 1; i < rects.size(); i++) {
                float[] rect = rects.get(i).rect();
                bounds[0] = Math.min(bounds[0], rect[0]);
                bounds[1] = Math.min(bounds[1], rect[1]);
                bounds[2] = Math.max(bounds[2], rect[2]);
                bounds[3] = Math.max(bounds[3], rect[3]);
            }
            quad(builder, bounds);
        }
        var glows = new ArrayList<Glow>(nativeOnly ? collectGlows(projView, cameraPos, target.width, target.height) : List.of());
        for(var g:missileGlows) addGlow(glows,projView,new Vector4f((float)(g.position().x-cameraPos.x),(float)(g.position().y-cameraPos.y),
                (float)(g.position().z-cameraPos.z),g.radius()),new Vector4f(((g.rgb()>>16)&255)/255f,((g.rgb()>>8)&255)/255f,(g.rgb()&255)/255f,g.brightness()),
                target.width,target.height,1f,1f,0f,1f,1f);
        // build() rather than buildOrThrow(): when every light projects off screen there is no vertex to
        // store, and buildOrThrow() turns that into "BufferBuilder was empty" instead of returning null.
        // That is a whole-frame crash for a frame that simply has nothing to draw -- it fires on the frame
        // the level is being torn down, where the light list is still populated but no longer projects.
        MeshData mesh = builder.build();
        // Nothing covers a pixel of the screen: skip the full-screen depth copy and the draw. The copy
        // alone is a whole framebuffer of bandwidth, which is not worth paying to light nothing. The
        // mesh is still built and closed so the tesselator's pooled buffer goes back where it came from.
        if (mesh == null && glows.isEmpty()) return;
        if (lightBuffer == null) {
            lightBuffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        if (mesh != null) {
            lightBuffer.bind();
            lightBuffer.upload(mesh); // upload() closes the mesh
        }

        // Copy the main framebuffer's depth into a separate target. Sampling the main target's
        // depth attachment while it is still bound for rendering is an OpenGL feedback loop, so we
        // mirror the pattern Minecraft itself uses for its post-chain temp targets: a TextureTarget
        // whose stencil state matches the source, so both depth attachments resolve to the same
        // internal format and glBlitFramebuffer accepts the copy.
        if (depthTarget == null || depthTarget.width != target.width || depthTarget.height != target.height
                || depthTarget.isStencilEnabled() != target.isStencilEnabled()) {
            if (depthTarget != null) depthTarget.destroyBuffers();
            depthTarget = new TextureTarget(target.width, target.height, true, Minecraft.ON_OSX);
            depthTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            if (target.isStencilEnabled()) depthTarget.enableStencil();
        }
        depthTarget.copyDepthFrom(target);
        target.bindWrite(false); // copyDepthFrom unbinds; rebind the main target for this pass
        int depthId = depthTarget.getDepthTextureId();

        // One tile list for the whole screen: a fragment then evaluates only the lights that can
        // actually reach its tile, instead of walking every active light. The list is what keeps the
        // cost from rising with the square of the light count.
        if (mesh == null) {
            drawGlows(glows, depthId, target.width, target.height, projView);
            return;
        }
        boolean tiled = !nativeOnly && ExtraConfig.TILED_LIGHTS.get() && uploadTiles(rects, target.width, target.height);

        shader.setSampler("DepthSampler", depthId);
        // Even when unused, keep the integer sampler on a separate texture unit.
        shader.setSampler("uTileMasks", tiled ? tileTexture : 0);
        shader.getUniform("uCameraPos").set((float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
        shader.getUniform("uScreenSize").set((float) target.width, (float) target.height);
        shader.getUniform("uInvProjMat").set(new Matrix4f(projection).invert());
        shader.getUniform("uInvViewMat").set(new Matrix4f(modelView).invert());
        shader.getUniform("uInvViewProj").set(new Matrix4f(projView).invert());
        shader.getUniform("uLightCount").set(lightCount);
        shader.getUniform("uUseTiles").set(tiled ? 1 : 0);
        shader.getUniform("uTileSize").set((float) TILE);
        shader.getUniform("uLightColor").set(ExtraConfig.GPU_RED.get().floatValue(),
                ExtraConfig.GPU_GREEN.get().floatValue(), ExtraConfig.GPU_BLUE.get().floatValue());
        float intensity = ExtraConfig.GPU_INTENSITY.get().floatValue();
        // Under a shader pack the pass runs after the pack's tonemapping, so it gets its own scale.
        if (IrisCompat.shaderPackInUse()) intensity *= ExtraConfig.GPU_SHADER_SCALE.get().floatValue();
        shader.getUniform("uIntensity").set(intensity);
        shader.getUniform("uFalloff").set(ExtraConfig.GPU_FALLOFF.get().floatValue());
        shader.getUniform("uNormalInfluence").set(ExtraConfig.GPU_NORMAL.get().floatValue());
        shader.getUniform("uLightData").set(renderData);
        shader.getUniform("uNativeOnly").set(nativeOnly ? 1 : 0);
        shader.getUniform("uProbeLight").set(-1);
        int lod = NativeVoxyDepth.texture();
        shader.setSampler("LodDepthSampler", lod > 0 ? lod : depthId);
        shader.getUniform("uHasLod").set(lod > 0 ? 1 : 0);
        shader.getUniform("uLodZeroToOne").set(NativeVoxyDepth.zeroToOne());
        shader.getUniform("uLodBlitZeroToOne").set(NativeVoxyDepth.blitZeroToOne());
        shader.getUniform("uInvLodViewProj").set(NativeVoxyDepth.inverse());
        shader.getUniform("uLodBlitViewProj").set(NativeVoxyDepth.blitProjection());
        shader.getUniform("uViewProj").set(projView);
        if (nativeOnly) {
            int time = minecraft.level == null ? 0 : (int) minecraft.level.getGameTime();
            for (int i = 0; i < lightCount; i++) {
                String key = lightKeys[i];
                int palette = AdvancedLights.isPaged(key)
                        ? lights.get(i).color : dev.sablelightoverhaul.lighting.SourceColor.WARM;
                tint(palette, time);
                if (palette == dev.sablelightoverhaul.lighting.SourceColor.UNTINTED) {
                    // The pack palette is linear warm radiance. Native framebuffer lighting
                    // operates on the final display colour, where that palette turns pink.
                    RGB[0] = 1.0F;
                    RGB[1] = 0.98F;
                    RGB[2] = 0.94F;
                }
                int offset = i * 3;
                TINTS[offset] = RGB[0];
                TINTS[offset + 1] = RGB[1];
                TINTS[offset + 2] = RGB[2];
            }
            shader.getUniform("uSourceTint").set(TINTS);
        }

        List<ColorProbe> colorProbes = ExtraConfig.on(MuzzleClientConfig.ADV_DEBUG)
                && org.lwjgl.opengl.GL.getCapabilities().OpenGL45
                ? beginColorProbes(target, projView, cameraPos, renderData) : List.of();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        // The pass covers projected bounds rather than world geometry, so depth must not reject it.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        GlStateManager._disableCull();

        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, minecraft.getWindow());
        shader.apply();
        // ShaderInstance.apply() applies the JSON additive blend state. Override AFTER it.
        // Screen blending preserves highlight contrast without another scene-colour copy.
        if (nativeOnly) RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE);
        try {
            // Voxy and Iris also bind textures directly. Bind the actual GL units explicitly
            // for this draw and clear any inherited comparison/filter sampler.
            bindLightInputs(lod > 0 ? lod : depthId, depthId, tiled ? tileTexture : 0);
            lightBuffer.bind();
            lightBuffer.draw();
            if (!colorProbes.isEmpty()) inspectColorProbes(target, colorProbes, renderData);
        } finally {
            for (int unit = 0; unit < SAVED_SAMPLERS.length; unit++) {
                GL33.glBindSampler(unit, SAVED_SAMPLERS[unit]);
            }
            VertexBuffer.unbind();
            shader.clear();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        if (!glows.isEmpty()) drawGlows(glows, depthId, target.width, target.height, projView);

        if (ExtraConfig.on(MuzzleClientConfig.ADV_DEBUG)) {
            long nowMs = System.currentTimeMillis();
            if (nowMs - lastRenderLog > 2000L) {
                lastRenderLog = nowMs;
                com.mojang.logging.LogUtils.getLogger().info(
                        "Sable Light Overhaul GPU render: shader={}, lights={}, irisPath={}, target={}x{} stencil={} depthFmt={}, copy={}x{} stencil={} depthFmt={}",
                        shader != null, lightCount, IrisCompat.shaderPackInUse(),
                        target.width, target.height, target.isStencilEnabled(), depthFormat(target.getDepthTextureId()),
                        depthTarget.width, depthTarget.height, depthTarget.isStencilEnabled(), depthFormat(depthTarget.getDepthTextureId()));
                if (nativeOnly) {
                    var activeProjection = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(modelView);
                    logNativeDepth(renderData, cameraPos, projView, activeProjection, target);
                }
            }
        }
    }

    private static List<ColorProbe> beginColorProbes(RenderTarget target, Matrix4f projection,
                                                     Vec3 eye, float[] data) {
        int point = -1;
        int dynamic = -1;
        int beam = -1;
        for (int i = 0; i < lightCount; i++) {
            if (data[i * FLOATS_PER_LIGHT + 3] <= 0F) continue;
            if (lights.get(i).light.beam()) {
                if (beam < 0) beam = i;
            } else if (lightKeys[i].startsWith("shot/")) {
                if (dynamic < 0) dynamic = i;
            } else if (!lights.get(i).light.directional() && point < 0) {
                point = i;
            }
        }
        long now = System.currentTimeMillis();
        boolean newBurst = dynamic >= 0 && !hadDynamicLight;
        hadDynamicLight = dynamic >= 0;
        if (!newBurst && now - lastColorProbe < 2000L) return List.of();
        lastColorProbe = now;
        var probes = new ArrayList<ColorProbe>(3);
        for (int index : new int[]{point, dynamic, beam}) {
            if (index < 0) continue;
            var probe = pickColorProbe(target, projection, eye, data, index);
            if (probe != null) probes.add(probe);
        }
        return probes;
    }

    private static ColorProbe pickColorProbe(RenderTarget target, Matrix4f projection,
                                             Vec3 eye, float[] data, int index) {
        AreaLight light = lights.get(index).light;
        int base = index * FLOATS_PER_LIGHT;
        Vec3 source = new Vec3(data[base], data[base + 1], data[base + 2]);
        if (ClientFlashes.sloOnly()) source = source.add(eye);
        Vec3 shift = new Vec3(light.x(), light.y(), light.z()).subtract(source);
        double forward = light.directional() || light.beam() ? Math.min(16F, Math.abs(data[base + 7]) * 0.35F) : 0F;
        var clip = projection.transform(new Vector4f(
                (float) (source.x - eye.x + light.dx() * forward),
                (float) (source.y - eye.y + light.dy() * forward),
                (float) (source.z - eye.z + light.dz() * forward), 1F));
        int centerX = target.width / 2;
        int centerY = target.height / 2;
        var inverse = new Matrix4f(projection).invert();
        int bestX = centerX;
        int bestY = centerY;
        double best = -1.0;
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) {
                if (!(clip.w > 0F) || Math.abs(clip.x) >= clip.w || Math.abs(clip.y) >= clip.w) break;
                centerX = (int) ((clip.x / clip.w * 0.5F + 0.5F) * target.width);
                centerY = (int) ((clip.y / clip.w * 0.5F + 0.5F) * target.height);
            }
            for (int[] offset : PROBE_OFFSETS) {
                int x = centerX + offset[0] * target.width / 1920;
                int y = centerY + offset[1] * target.height / 1080;
                if (x < 0 || y < 0 || x >= target.width || y >= target.height) continue;
                var receiver = reconstructReceiver(inverse, eye, target, x, y,
                        readDepth(depthTarget.getDepthTextureId(), x, y));
                if (receiver == null) continue;
                double expected = light.at(receiver.x + shift.x, receiver.y + shift.y, receiver.z + shift.z);
                if (expected > best) {
                    best = expected;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        return new ColorProbe(index, lightKeys[index], bestX, bestY, best, data[base + 3],
                readColor(target, bestX, bestY));
    }

    private static int readColor(RenderTarget target, int x, int y) {
        try (var stack = MemoryStack.stackPush()) {
            var pixel = stack.malloc(4);
            GL45.glGetTextureSubImage(target.getColorTextureId(), 0, x, y, 0, 1, 1, 1,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            return (Byte.toUnsignedInt(pixel.get(0)) << 16)
                    | (Byte.toUnsignedInt(pixel.get(1)) << 8) | Byte.toUnsignedInt(pixel.get(2));
        }
    }

    // The same program and depth samplers shade one pixel per source in a separate color attachment.
    // Probe coordinates always refer to the main image, even though this target is 1x1.
    private static void inspectColorProbes(RenderTarget target, List<ColorProbe> probes, float[] expectedData) {
        int program = shader.getId();
        if (capturedProgram != program) {
            capturedProgram = program;
            try (var stack = MemoryStack.stackPush()) {
                var ids = stack.mallocInt(8);
                var count = stack.mallocInt(1);
                GL20.glGetAttachedShaders(program, count, ids);
                var folder = Minecraft.getInstance().gameDirectory.toPath().resolve("logs/slo-shader-capture");
                java.nio.file.Files.createDirectories(folder);
                for (int i = 0; i < count.get(0); i++) {
                    int id = ids.get(i);
                    java.nio.file.Files.writeString(folder.resolve(program + "-" + id + ".glsl"),
                            GL20.glGetShaderSource(id));
                }
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().warn("SLO runtime shader capture failed", e);
            }
        }
        int lightUniform = GL20.glGetUniformLocation(program, "uProbeLight");
        int pixelUniform = GL20.glGetUniformLocation(program, "uProbePixel");
        int mainUniform = GL20.glGetUniformLocation(program, "uProbeMainDepth");
        try (var stack = MemoryStack.stackPush()) {
            var viewport = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            if (colorProbeTarget == null) {
                colorProbeTarget = new TextureTarget(1, 1, false, Minecraft.ON_OSX);
                // Preserve signed world positions and unbounded intermediates for diagnosis.
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorProbeTarget.getColorTextureId());
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, 1, 1, 0,
                        GL11.GL_RGBA, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
                // TextureTarget initialization can replace the currently bound depth texture.
                bindLightTextures(NativeVoxyDepth.texture() > 0 ? NativeVoxyDepth.texture() : depthTarget.getDepthTextureId(),
                        depthTarget.getDepthTextureId(), GL20.glGetUniformi(program,
                                GL20.glGetUniformLocation(program, "uUseTiles")) == 1 ? tileTexture : 0);
            }
            var clearColor = stack.floats(0F, 0F, 0F, 0F);
            var results = new ArrayList<ColorResult>(probes.size());
            var logger = com.mojang.logging.LogUtils.getLogger();
            logger.info("SLO Only GPU uniforms: intensity={}, falloff={}, native={}, count={}",
                    GL20.glGetUniformf(program, GL20.glGetUniformLocation(program, "uIntensity")),
                    GL20.glGetUniformf(program, GL20.glGetUniformLocation(program, "uFalloff")),
                    GL20.glGetUniformi(program, GL20.glGetUniformLocation(program, "uNativeOnly")),
                    GL20.glGetUniformi(program, GL20.glGetUniformLocation(program, "uLightCount")));
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            if (scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
            try {
                RenderSystem.disableBlend();
                for (var probe : probes) {
                    int after = readColor(target, probe.x(), probe.y());
                    colorProbeTarget.bindWrite(true);
                    // Keep projected light bounds aligned with the sampled main-image pixel.
                    RenderSystem.viewport(-probe.x(), -probe.y(), target.width, target.height);
                    GL20.glUniform1i(lightUniform, probe.slot());
                    GL20.glUniform2f(pixelUniform, probe.x() + 0.5F, probe.y() + 0.5F);
                    GL20.glUniform1i(mainUniform, 0);
                    GL30.glClearBufferfv(GL11.GL_COLOR, 0, clearColor);
                    lightBuffer.draw();
                    int isolated = readColor(colorProbeTarget, 0, 0);
                    GL20.glUniform1i(mainUniform, 1);
                    GL30.glClearBufferfv(GL11.GL_COLOR, 0, clearColor);
                    lightBuffer.draw();
                    int isolatedMain = readColor(colorProbeTarget, 0, 0);
                    GL20.glUniform1i(mainUniform, 2);
                    GL30.glClearBufferfv(GL11.GL_COLOR, 0, clearColor);
                    lightBuffer.draw();
                    int powerIndex = probe.slot() * FLOATS_PER_LIGHT + 3;
                    int powerLocation = GL20.glGetUniformLocation(program, "uLightData[" + powerIndex + "]");
                    results.add(new ColorResult(probe, after, isolated, isolatedMain,
                            readColor(colorProbeTarget, 0, 0), GL20.glGetUniformf(program, powerLocation)));
                    float[] gpuData = new float[FLOATS_PER_LIGHT];
                    int base = probe.slot() * FLOATS_PER_LIGHT;
                    for (int lane = 0; lane < gpuData.length; lane++) {
                        gpuData[lane] = GL20.glGetUniformf(program,
                                GL20.glGetUniformLocation(program, "uLightData[" + (base + lane) + "]"));
                    }
                    float[] receiver = probeFloats(mainUniform, 3, clearColor);
                    float[] profile = probeFloats(mainUniform, 4, clearColor);
                    logger.info("SLO light stages: source={} slot={} currentProgram={}/{} depth={} receiver={} qProfileBrightnessAngular={} cpuData={} gpuData={}",
                            probe.light(), probe.slot(), GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), program,
                            readDepth(depthTarget.getDepthTextureId(), probe.x(), probe.y()),
                            java.util.Arrays.toString(receiver), java.util.Arrays.toString(profile),
                            java.util.Arrays.toString(java.util.Arrays.copyOfRange(expectedData, base, base + FLOATS_PER_LIGHT)),
                            java.util.Arrays.toString(gpuData));
                }
                pendingColorProbes = results;
            } finally {
                GL20.glUniform1i(lightUniform, -1);
                GL20.glUniform1i(mainUniform, 0);
                target.bindWrite(false);
                RenderSystem.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    private static float[] probeFloats(int modeUniform, int mode, java.nio.FloatBuffer clearColor) {
        GL20.glUniform1i(modeUniform, mode);
        GL30.glClearBufferfv(GL11.GL_COLOR, 0, clearColor);
        lightBuffer.draw();
        float[] values = new float[4];
        GL45.glGetTextureSubImage(colorProbeTarget.getColorTextureId(), 0, 0, 0, 0, 1, 1, 1,
                GL11.GL_RGBA, GL11.GL_FLOAT, values);
        return values;
    }

    private static String hexColor(int rgb) {
        return Integer.toHexString(0x1000000 | rgb).substring(1);
    }

    private static void finishColorProbes(RenderTarget target) {
        if (pendingColorProbes.isEmpty()) return;
        for (var result : pendingColorProbes) {
            var probe = result.probe();
            int end = readColor(target, probe.x(), probe.y());
            com.mojang.logging.LogUtils.getLogger().info(
                    "SLO Only color probe: {} pixel=({}, {}) expectedMainLight={} isolated=#{} isolatedMain=#{} rawMainLight={} powerCpuGpu={}/{} fragmentHasDepth={} before=#{} after=#{} endOfWorld=#{}",
                    probe.light(), probe.x(), probe.y(), Math.round(probe.expected() * 100.0) / 100.0,
                    hexColor(result.isolated()), hexColor(result.isolatedMain()),
                    Math.round((result.rawMain() >> 16) * 1500.0 / 255.0) / 100.0,
                    probe.power(), result.gpuPower(), (result.rawMain() & 255) != 0,
                    hexColor(probe.before()), hexColor(result.after()), hexColor(end));
        }
        pendingColorProbes = List.of();
    }

    private static Vec3 hitPosition() {
        var hit = Minecraft.getInstance().hitResult;
        return hit != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? hit.getLocation() : null;
    }

    private static float readDepth(int texture, int x, int y) {
        try (var stack = MemoryStack.stackPush()) {
            var pixel = stack.mallocFloat(1);
            GL45.glGetTextureSubImage(texture, 0, x, y, 0, 1, 1, 1,
                    GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, pixel);
            return pixel.get(0);
        }
    }

    private static Vec3 reconstructReceiver(Matrix4f inverseViewProjection, Vec3 eye,
                                            RenderTarget target, int x, int y, float depth) {
        if (!(depth > 0F && depth < 1F)) return null;
        float ndcX = (x + 0.5F) * 2F / target.width - 1F;
        float ndcY = (y + 0.5F) * 2F / target.height - 1F;
        var p = inverseViewProjection.transform(new Vector4f(ndcX, ndcY, depth * 2F - 1F, 1F));
        if (Math.abs(p.w) < 1.0E-8F) return null;
        return eye.add(p.x / p.w, p.y / p.w, p.z / p.w);
    }

    private static DepthPixel sampleDepth(Matrix4f viewProjection, Vec3 eye, Vec3 hit,
                                          RenderTarget target, int texture) {
        var clip = viewProjection.transform(new Vector4f((float) (hit.x - eye.x),
                (float) (hit.y - eye.y), (float) (hit.z - eye.z), 1F));
        if (!(clip.w > 0F) || Math.abs(clip.x) >= clip.w || Math.abs(clip.y) >= clip.w) return null;
        int x = (int) ((clip.x / clip.w * 0.5F + 0.5F) * target.width);
        int y = (int) ((clip.y / clip.w * 0.5F + 0.5F) * target.height);
        if (x < 0 || x >= target.width || y < 0 || y >= target.height) return null;
        float depth = readDepth(texture, x, y);
        return new DepthPixel(x, y, clip.z / clip.w * 0.5F + 0.5F, depth,
                reconstructReceiver(new Matrix4f(viewProjection).invert(), eye, target, x, y, depth));
    }

    private static void logNativeDepth(float[] data, Vec3 eye, Matrix4f eventProjection,
                                       Matrix4f activeProjection, RenderTarget target) {
        Vec3 hit = hitPosition();
        DepthPixel eventPixel = null;
        DepthPixel activePixel = null;
        float mainDepth = -1F;
        boolean readback = org.lwjgl.opengl.GL.getCapabilities().OpenGL45;
        if (hit != null && readback) {
            eventPixel = sampleDepth(eventProjection, eye, hit, target, depthTarget.getDepthTextureId());
            if (eventPixel != null) {
                mainDepth = readDepth(target.getDepthTextureId(), eventPixel.x(), eventPixel.y());
            }
            activePixel = sampleDepth(activeProjection, eye, hit, target, depthTarget.getDepthTextureId());
        }

        Vec3 receiver = eventPixel == null ? null : eventPixel.receiver();
        var slots = new StringBuilder();
        for (int i = 0; i < lightCount; i++) {
            AreaLight light = lights.get(i).light;
            int base = i * FLOATS_PER_LIGHT;
            double dx = light.x() - (eye.x + data[base]);
            double dy = light.y() - (eye.y + data[base + 1]);
            double dz = light.z() - (eye.z + data[base + 2]);
            double atHit = hit == null ? -1.0 : light.at(hit.x + dx, hit.y + dy, hit.z + dz);
            double atReceiver = receiver == null ? -1.0 : light.at(receiver.x + dx, receiver.y + dy, receiver.z + dz);
            slots.append(' ').append(lightKeys[i])
                    .append(light.beam() ? "/beam" : light.directional() ? "/directional" : "/point")
                    .append(" power=").append(data[base + 3])
                    .append(" atHit=").append(Math.round(atHit * 100.0) / 100.0)
                    .append(" atMainReceiver=").append(Math.round(atReceiver * 100.0) / 100.0);
        }
        com.mojang.logging.LogUtils.getLogger().info(
                "SLO Only main depth: hit={}, eventPixel={}, mainDepthAtEventPixel={}, activePixel={}, terrainPixel={}, eventMatchesActive={}, eventMatchesTerrain={}, activeMatchesTerrain={}, terrainMatchesRenderSystem={}, readback={},{}",
                hit, eventPixel, mainDepth, activePixel, terrainPixel,
                eventProjection.equals(activeProjection, 1.0E-6F),
                terrainViewProjection != null && eventProjection.equals(terrainViewProjection, 1.0E-6F),
                terrainViewProjection != null && activeProjection.equals(terrainViewProjection, 1.0E-6F),
                terrainViewProjection != null && terrainViewProjection.equals(terrainRenderSystemViewProjection, 1.0E-6F),
                readback, slots);
    }

    private static void bindLightInputs(int lodDepth, int sceneDepth, int masks) {
        for (int unit = 0; unit < SAVED_SAMPLERS.length; unit++) {
            SAVED_SAMPLERS[unit] = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, unit);
        }
        bindLightTextures(lodDepth, sceneDepth, masks);
    }

    private static void bindLightTextures(int lodDepth, int sceneDepth, int masks) {
        int active = GlStateManager._getActiveTexture();
        int program = shader.getId();
        bindLightInput(program, 0, "LodDepthSampler", lodDepth);
        bindLightInput(program, 1, "DepthSampler", sceneDepth);
        bindLightInput(program, 2, "uTileMasks", masks);
        GL13.glActiveTexture(active);
    }

    private static void bindLightInput(int program, int unit, String uniform, int texture) {
        GL33.glBindSampler(unit, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, uniform), unit);
    }

    /** Diagnostic only: resolves the driver's internal format for a depth texture. */
    private static int depthFormat(int textureId) {
        GlStateManager._bindTexture(textureId);
        int format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_INTERNAL_FORMAT);
        GlStateManager._bindTexture(0);
        return format;
    }

    private static void tint(int palette, int tick) {
        dev.sablelightoverhaul.lighting.SourceColor.rgb(palette, tick, RGB,
                ExtraConfig.GPU_RED.get().floatValue(), ExtraConfig.GPU_GREEN.get().floatValue(),
                ExtraConfig.GPU_BLUE.get().floatValue());
    }

    private record Glow(float x, float y, float depth, float radiusX, float radiusY,
                        float red, float green, float blue, float axisLength, float axisWidth) {}

    private static List<Glow> collectGlows(Matrix4f projection, net.minecraft.world.phys.Vec3 eye,
                                           int width, int height) {
        if (glowShader == null) return List.of();
        var result = new ArrayList<Glow>();
        float gain = ExtraConfig.BEAM_GLOW.get().floatValue();
        float shrink = ExtraConfig.FLARE_GLOW_SHRINK_DISTANCE.get().floatValue();
        for (int i = 0; DebugFlares.hasStates() && i < dev.sablelightoverhaul.FlarePayload.MAX_FLARES; i++) {
            addGlow(result, projection, DebugFlares.position(i), DebugFlares.color(i),
                    width, height, 1F, 1F, 1F, 1F, shrink);
        }
        if (gain <= 0F) return result;
        for (int i = 0; i < IrisMuzzleBridge.beamCount(); i++) {
            Vector4f source = IrisMuzzleBridge.beamPosition(i);
            Vector4f axis = IrisMuzzleBridge.beamAxis(i);
            float dot = -(source.x * axis.x + source.y * axis.y + source.z * axis.z);
            float lengths = (float) Math.sqrt((source.x * source.x + source.y * source.y + source.z * source.z)
                    * (axis.x * axis.x + axis.y * axis.y + axis.z * axis.z));
            if (dot > axis.w * lengths) addGlow(result, projection, source, IrisMuzzleBridge.beamColor(i),
                    width, height, gain, 1F, 1F, 1F, shrink);
        }
        var level = Minecraft.getInstance().level;
        int tick = level == null ? 0 : (int) level.getGameTime();
        for (int i = 0; i < lights.size(); i++) {
            var appearance = ExtraLights.appearance(lightKeys[i]);
            if (!appearance.glow() || !appearance.ring() && !appearance.axis()) continue;
            var light = lights.get(i);
            tint(light.color, tick);
            addGlow(result, projection, new Vector4f((float) (light.light.x() - eye.x),
                            (float) (light.light.y() - eye.y), (float) (light.light.z() - eye.z), 0.14F),
                    new Vector4f(RGB[0], RGB[1], RGB[2], (float) light.light.brightness() / 15F),
                    width, height, gain, appearance.ring() ? (float) appearance.ringSize() : 0F,
                    appearance.axis() ? (float) appearance.axisLength() : 0F,
                    (float) appearance.axisWidth(), shrink);
        }
        return result;
    }

    private static void addGlow(List<Glow> glows, Matrix4f projection, Vector4f source, Vector4f color,
                                int width, int height, float gain, float ring, float axis, float axisWidth,
                                float shrink) {
        if (source.w <= 0F || color.w <= 0F) return;
        Vector4f clip = projection.transform(SCRATCH.set(source.x, source.y, source.z, 1F));
        if (clip.w <= 0.01F) return;
        float x = clip.x / clip.w, y = clip.y / clip.w;
        if (Math.abs(x) >= 1F || Math.abs(y) >= 1F) return;
        float distance = (float) Math.sqrt(source.x * source.x + source.y * source.y + source.z * source.z);
        float nearScale = Math.min(1F, shrink / Math.max(distance, 1F));
        float aperture = Math.min(0.04F, source.w / Math.max(distance, 0.5F));
        float ringRadius = ring > 0F ? Math.min(0.65F,
                Math.max(3F / height, (0.14F * nearScale + 3F * aperture) * ring)) : 0F;
        float streakX = axis > 0F ? Math.min(1.5F,
                Math.max(3F / height, (0.24F * nearScale + 4F * aperture) * axis)) : 0F;
        float streakY = axis > 0F ? Math.min(0.45F,
                Math.max(3F / height, (0.024F * nearScale + aperture) * axisWidth)) : 0F;
        float rx = Math.max(ringRadius, streakX) * height / width;
        float ry = Math.max(ringRadius, streakY);
        float bias = Math.max(0.1F, source.w * 1.75F);
        float ratio = Math.max(0.01F, 1F - bias / Math.max(distance, 0.01F));
        clip = projection.transform(SCRATCH.set(source.x * ratio, source.y * ratio, source.z * ratio, 1F));
        float depth = Math.clamp(clip.z / clip.w * 0.5F + 0.5F, 0F, 1F);
        float brightness = gain * color.w;
        glows.add(new Glow(x, y, depth, rx, ry, color.x * brightness, color.y * brightness,
                color.z * brightness, streakY / ry, ringRadius / ry));
    }

    private static void drawGlows(List<Glow> glows, int depth, int width, int height, Matrix4f projection) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            glowShader.setSampler("DepthSampler", depth);
            int lod = NativeVoxyDepth.texture();
            glowShader.setSampler("LodDepthSampler", lod > 0 ? lod : depth);
            glowShader.safeGetUniform("uHasLod").set(lod > 0 ? 1 : 0);
            glowShader.safeGetUniform("uLodZeroToOne").set(NativeVoxyDepth.zeroToOne());
            glowShader.safeGetUniform("uInvLodViewProj").set(NativeVoxyDepth.inverse());
            glowShader.safeGetUniform("uViewProj").set(projection);
            for (Glow glow : glows) {
                var builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
                quad(builder, new float[]{glow.x - glow.radiusX, glow.y - glow.radiusY,
                        glow.x + glow.radiusX, glow.y + glow.radiusY});
                lightBuffer.bind();
                lightBuffer.upload(builder.buildOrThrow());
                glowShader.getUniform("GlowCentre").set(glow.x, glow.y, glow.depth);
                glowShader.getUniform("GlowShape").set(glow.radiusX, glow.radiusY, glow.axisLength, glow.axisWidth);
                glowShader.getUniform("GlowColor").set(glow.red, glow.green, glow.blue);
                glowShader.getUniform("ScreenSize").set((float) width, (float) height);
                glowShader.apply();
                lightBuffer.draw();
            }
        } finally {
            VertexBuffer.unbind();
            glowShader.clear();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    /**
     * The projection of a light, clipped to the camera's near plane.
     *
     * <p>Two things keep the covered screen area honest. The bounds are clipped against the near plane
     * rather than answered with a full-screen quad, which used to make standing inside your own muzzle
     * flash as expensive as a screen-wide pass per light. And a beam is projected as its <b>cone</b>,
     * not as the box that contains it: a 512-block beam's box covers the whole screen from almost
     * anywhere, so the beam used to be evaluated at every pixel no matter where it pointed.
     *
     * <p>The snapshot's own {@code bounds} are deliberately left alone — they are the light's support,
     * which the CPU tier uses to decide where it can have any effect at all.
     * @returns {minX,minY,maxX,maxY} in NDC, or null when nothing of the light is in front.
     */
    private static float[] screenRect(AreaSnapshot snap, Matrix4f projView, double camX, double camY, double camZ) {
        AreaLight light=snap.light;
        if(light.beam()) {
            float[] cone=beamRect(light,projView,camX,camY,camZ);
            if(cone!=null) return cone;
            // The cone could not be projected (it wraps around the camera): fall back to the box, which
            // is conservative and, in that case, correct.
        }
        return boxRect(snap.bounds,projView,camX,camY,camZ);
    }
    /** Projections used for a beam: the apex, then rings along it. Enough for the true silhouette. */
    private static final double[] BEAM_SLICES={0.0,0.35,0.7,1.0};
    private static final int BEAM_RING=8;
    /**
     * The screen rectangle of a beam's cone. Rings at several distances are used, not just the far cap:
     * a nearer ring with a smaller radius can project wider than the far one.
     * @returns null when any sampled point is behind the camera, so the caller can fall back.
     */
    private static float[] beamRect(AreaLight l, Matrix4f projView, double camX, double camY, double camZ) {
        double ax=l.dx(), ay=l.dy(), az=l.dz();
        // An orthonormal pair perpendicular to the axis, built from whichever reference is least aligned.
        double rx=0.0, ry=1.0, rz=0.0;
        if(Math.abs(ay)>0.9) { rx=1.0; ry=0.0; rz=0.0; }
        double ux=ay*rz-az*ry, uy=az*rx-ax*rz, uz=ax*ry-ay*rx;
        double ul=Math.sqrt(ux*ux+uy*uy+uz*uz);
        if(!(ul>1.0E-9)) return null;
        ux/=ul; uy/=ul; uz/=ul;
        double vx=ay*uz-az*uy, vy=az*ux-ax*uz, vz=ax*uy-ay*ux;
        double range=Math.abs(l.axial()), radius=range*l.lateral();
        float minX=Float.MAX_VALUE, minY=Float.MAX_VALUE, maxX=-Float.MAX_VALUE, maxY=-Float.MAX_VALUE;
        for(double t:BEAM_SLICES) {
            double cx=l.x()+ax*range*t, cy=l.y()+ay*range*t, cz=l.z()+az*range*t;
            double r=radius*t;
            int points=t==0.0?1:BEAM_RING;
            for(int k=0;k<points;k++) {
                double angle=(k/(double)points)*Math.PI*2.0;
                double px=cx+(ux*Math.cos(angle)+vx*Math.sin(angle))*r;
                double py=cy+(uy*Math.cos(angle)+vy*Math.sin(angle))*r;
                double pz=cz+(uz*Math.cos(angle)+vz*Math.sin(angle))*r;
                float wx=(float)(px-camX), wy=(float)(py-camY), wz=(float)(pz-camZ);
                float w=projView.m03()*wx+projView.m13()*wy+projView.m23()*wz+projView.m33();
                if(w<=NEAR_CLIP) return null;
                Vector4f v=projView.transform(SCRATCH.set(wx,wy,wz,1.0F));
                float ndcX=v.x/v.w, ndcY=v.y/v.w;
                minX=Math.min(minX,ndcX); maxX=Math.max(maxX,ndcX);
                minY=Math.min(minY,ndcY); maxY=Math.max(maxY,ndcY);
            }
        }
        if(!(minX<maxX)||!(minY<maxY)) return null;
        return new float[] {
                Math.max(-1.0F,minX-0.005F), Math.max(-1.0F,minY-0.005F),
                Math.min(1.0F,maxX+0.005F), Math.min(1.0F,maxY+0.005F)};
    }
    static float[] boxRect(AABB b, Matrix4f projView, double camX, double camY, double camZ) {
        for (int i = 0; i < 8; i++) {
            CORNER_X[i] = (float) (((i & 1) == 0 ? b.minX : b.maxX) - camX);
            CORNER_Y[i] = (float) (((i & 2) == 0 ? b.minY : b.maxY) - camY);
            CORNER_Z[i] = (float) (((i & 4) == 0 ? b.minZ : b.maxZ) - camZ);
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean any = false;
        for (int i = 0; i < 8; i++) {
            float w = clipW(projView, i);
            if (w <= NEAR_CLIP) continue;
            Vector4f v = SCRATCH.set(CORNER_X[i], CORNER_Y[i], CORNER_Z[i], 1.0F);
            projView.transform(v);
            float ndcX = v.x / v.w, ndcY = v.y / v.w;
            minX = Math.min(minX, ndcX); maxX = Math.max(maxX, ndcX);
            minY = Math.min(minY, ndcY); maxY = Math.max(maxY, ndcY);
            any = true;
        }
        // The twelve edges: wherever one end is in front and the other behind, the crossing point is
        // part of the silhouette and the projected corners alone would under-cover the light.
        for (int a = 0; a < 8; a++) {
            for (int axis = 1; axis <= 4; axis <<= 1) {
                int c = a | axis;
                if (c == a) continue;
                float wa = clipW(projView, a), wc = clipW(projView, c);
                if ((wa <= NEAR_CLIP) == (wc <= NEAR_CLIP)) continue;
                float t = (NEAR_CLIP - wa) / (wc - wa);
                Vector4f v = SCRATCH.set(
                        CORNER_X[a] + (CORNER_X[c] - CORNER_X[a]) * t,
                        CORNER_Y[a] + (CORNER_Y[c] - CORNER_Y[a]) * t,
                        CORNER_Z[a] + (CORNER_Z[c] - CORNER_Z[a]) * t, 1.0F);
                projView.transform(v);
                if (v.w <= 0.0F) continue;
                float ndcX = v.x / v.w, ndcY = v.y / v.w;
                minX = Math.min(minX, ndcX); maxX = Math.max(maxX, ndcX);
                minY = Math.min(minY, ndcY); maxY = Math.max(maxY, ndcY);
                any = true;
            }
        }
        if (!any || !(minX < maxX) || !(minY < maxY)) return null;
        return new float[] {
                Math.max(-1.0F, minX - 0.005F), Math.max(-1.0F, minY - 0.005F),
                Math.min(1.0F, maxX + 0.005F), Math.min(1.0F, maxY + 0.005F)};
    }
    /** The clip-space w of one corner: positive means it is in front of the camera. */
    private static float clipW(Matrix4f projView, int corner) {
        return projView.m03() * CORNER_X[corner] + projView.m13() * CORNER_Y[corner]
                + projView.m23() * CORNER_Z[corner] + projView.m33();
    }
    /** Emits one screen-space quad over an NDC rectangle. */
    static void quad(BufferBuilder builder, float[] rect) {
        float minX = rect[0], minY = rect[1], maxX = rect[2], maxY = rect[3];
        quad(builder, minX, minY, 0.0F, maxX, minY, 0.0F, maxX, maxY, 0.0F, minX, maxY, 0.0F);
    }
    /** One mask per screen tile, with bit {@code i} set wherever light {@code i} could contribute. */
    private static void tileCoverage(List<ScreenQuad> quads, int width, int height) {
        int tx = Math.max(1, (width + TILE - 1) / TILE);
        int ty = Math.max(1, (height + TILE - 1) / TILE);
        if (tileMasks.length < tx * ty) tileMasks = new int[tx * ty];
        java.util.Arrays.fill(tileMasks, 0, tx * ty, 0);
        for (ScreenQuad quad : quads) {
            if (quad.lightIndex() >= 32) continue;
            float[] rect = quad.rect();
            int x0 = tileIndex(rect[0], width, tx), x1 = tileIndex(rect[2], width, tx);
            int y0 = tileIndex(rect[1], height, ty), y1 = tileIndex(rect[3], height, ty);
            int bit = 1 << quad.lightIndex();
            for (int y = y0; y <= y1; y++) {
                int row = y * tx;
                for (int x = x0; x <= x1; x++) tileMasks[row + x] |= bit;
            }
        }
        tilesX = tx; tilesY = ty;
    }

    private static boolean uploadTiles(List<ScreenQuad> quads, int width, int height) {
        tileCoverage(quads, width, height);
        if (tileTexture == 0) {
            tileTexture = GlStateManager._genTexture();
            GlStateManager._bindTexture(tileTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
        } else {
            GlStateManager._bindTexture(tileTexture);
        }
        if (tileTextureWidth != tilesX || tileTextureHeight != tilesY) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32UI, tilesX, tilesY, 0,
                    GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT, (java.nio.IntBuffer) null);
            tileTextureWidth = tilesX;
            tileTextureHeight = tilesY;
        }
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, tilesX, tilesY,
                GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT, tileMasks);
        GlStateManager._bindTexture(0);
        return true;
    }
    /** NDC x or y to a tile index, clamped to the grid. */
    private static int tileIndex(float ndc, int extent, int tiles) {
        float pixel = (ndc * 0.5F + 0.5F) * extent;
        int tile = (int) Math.floor(pixel / TILE);
        return Math.max(0, Math.min(tiles - 1, tile));
    }

    /** The light index and its projected bounds for tile assignment. */
    private record ScreenQuad(int lightIndex,float[] rect) {}

    private static void quad(BufferBuilder b, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        b.addVertex(ax, ay, az);
        b.addVertex(bx, by, bz);
        b.addVertex(cx, cy, cz);
        b.addVertex(dx, dy, dz);
    }
}
