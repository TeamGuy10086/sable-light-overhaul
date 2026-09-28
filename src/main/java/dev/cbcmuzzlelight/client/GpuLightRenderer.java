package dev.cbcmuzzlelight.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.cbcmuzzlelight.AreaLight;
import dev.cbcmuzzlelight.MuzzleLight;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

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
    private static volatile List<AreaSnapshot> lights = List.of();
    private static volatile float[] lightData = new float[MAX_FLOATS];
    private static volatile int lightCount;
    private static VertexBuffer lightBuffer;
    private static TextureTarget depthTarget;
    private static long lastRenderLog;
    private static final Vector4f SCRATCH = new Vector4f();
    /** Screen tile edge, in pixels, for the per-tile light lists. */
    private static final int TILE = 16;
    private static int tileTexture;
    private static int[] tileMasks = new int[0];
    private static int tilesX, tilesY;
    /** Reused corner buffers, so projecting a light allocates nothing. */
    private static final float[] CORNER_X = new float[8], CORNER_Y = new float[8], CORNER_Z = new float[8];
    private static final float NEAR_CLIP = 1.0E-4F;

    private GpuLightRenderer() {}

    public static void registerShader(RegisterShadersEvent event) {
        try {
            event.registerShader(
                new ShaderInstance(event.getResourceProvider(), MuzzleLight.ID + ":cbc_gpu_light", DefaultVertexFormat.POSITION),
                GpuLightRenderer::installShader
            );
        } catch (Exception ex) {
            com.mojang.logging.LogUtils.getLogger().error("CBC Muzzle Light: failed to register GPU light shader", ex);
        }
    }

    /**
     * Chooses the render stage for the light pass.
     *
     * <p>Without a shader pack the opaque terrain (and Sable sub-levels) are complete at
     * {@code AFTER_CUTOUT_BLOCKS}, and the main framebuffer holds the scene with valid depth.
     *
     * <p>With an Iris shader pack the terrain goes into Iris' own gbuffer and Iris writes the final
     * composited image into the main framebuffer at the end of {@code renderLevel}. NeoForge
     * dispatches {@code AFTER_LEVEL} after that call returns, so that is the only point where the
     * pass survives. Iris' final pass only disables depth writes, so the main depth buffer still
     * holds the scene depth there.
     */
    public static void onRenderLevelStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        boolean pack = IrisCompat.shaderPackInUse();
        // In the Advanced tier the patched pack emits the flash instead, so the screen-space
        // overlay stands down to avoid doubling the light.
        if (ClientFlashes.inPackLighting()) return;
        var wanted = pack
                ? net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL
                : net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS;
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

    /** Replaces the active light set with the given world-space area lights (no Sable plot receivers). */
    public static void setLights(List<AreaSnapshot> list) {
        int n = Math.min(list.size(), MAX_LIGHTS);
        float[] data = new float[MAX_FLOATS];
        int base = 0;
        List<AreaSnapshot> copy = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            AreaSnapshot snap = list.get(i);
            base = writeLight(data, base, snap.light);
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

    /** Called on the render thread from {@code RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS}. */
    public static void render(Matrix4f projection, Matrix4f modelView, net.minecraft.client.Camera camera) {
        if (shader == null || lightCount <= 0 || lights.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        var target = minecraft.getMainRenderTarget();
        if (target == null || !target.useDepth) return;

        var cameraPos = camera.getPosition();
        // One screen-space quad per light, covering the projection of its bounds. Drawing the light
        // volume as world geometry fails when the camera sits inside the volume: the near faces are
        // behind the camera and the only faces left in front are the far ones, which lose the depth
        // test against the terrain. Covering the projected bounds instead works in every case, and
        // the fragment shader reconstructs the lit surface from the depth buffer.
        Matrix4f projView = new Matrix4f(projection).mul(modelView);
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder builder = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        var rects = new ArrayList<ScreenQuad>(lights.size());
        for (int i = 0; i < lights.size(); i++) {
            // The light's own index is carried along: the shader's light array is indexed that way, so
            // the tile bit has to be the same number, not this quad's position in the list.
            float[] rect = screenRect(lights.get(i), projView, cameraPos.x, cameraPos.y, cameraPos.z);
            if (rect == null) continue;
            rects.add(new ScreenQuad(i, rect));
            quad(builder, rect);
        }
        MeshData mesh = builder.buildOrThrow();
        // Nothing covers a pixel of the screen: skip the full-screen depth copy and the draw. The copy
        // alone is a whole framebuffer of bandwidth, which is not worth paying to light nothing. The
        // mesh is still built and closed so the tesselator's pooled buffer goes back where it came from.
        if (rects.isEmpty()) { mesh.close(); return; }
        if (lightBuffer == null) {
            lightBuffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        lightBuffer.bind();
        lightBuffer.upload(mesh); // upload() closes the mesh

        // Copy the main framebuffer's depth into a separate target. Sampling the main target's
        // depth attachment while it is still bound for rendering is an OpenGL feedback loop, so we
        // mirror the pattern Minecraft itself uses for its post-chain temp targets: a TextureTarget
        // whose stencil state matches the source, so both depth attachments resolve to the same
        // internal format and glBlitFramebuffer accepts the copy.
        if (depthTarget == null || depthTarget.width != target.width || depthTarget.height != target.height) {
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
        boolean tiled = ExtraConfig.TILED_LIGHTS.get() && uploadTiles(rects, target.width, target.height);

        shader.setSampler("DepthSampler", depthId);
        shader.setSampler("uTileMasks", tiled && tileTexture != 0 ? tileTexture : depthId);
        shader.getUniform("uCameraPos").set((float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
        shader.getUniform("uScreenSize").set((float) target.width, (float) target.height);
        shader.getUniform("uInvProjMat").set(new Matrix4f(projection).invert());
        shader.getUniform("uInvViewMat").set(new Matrix4f(modelView).invert());
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
        shader.getUniform("uLightData").set(lightData);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        // The pass covers projected bounds rather than world geometry, so depth must not reject it.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        GlStateManager._disableCull();

        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, minecraft.getWindow());
        shader.apply();
        lightBuffer.bind();
        lightBuffer.draw();
        VertexBuffer.unbind();
        shader.clear();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        if (MuzzleClientConfig.ADV_DEBUG.get()) {
            long nowMs = System.currentTimeMillis();
            if (nowMs - lastRenderLog > 2000L) {
                lastRenderLog = nowMs;
                com.mojang.logging.LogUtils.getLogger().info(
                        "CBC Muzzle Light GPU render: shader={}, lights={}, irisPath={}, target={}x{} stencil={} depthFmt={}, copy={}x{} stencil={} depthFmt={}",
                        shader != null, lightCount, IrisCompat.shaderPackInUse(),
                        target.width, target.height, target.isStencilEnabled(), depthFormat(target.getDepthTextureId()),
                        depthTarget.width, depthTarget.height, depthTarget.isStencilEnabled(), depthFormat(depthTarget.getDepthTextureId()));
            }
        }
    }

    /** Diagnostic only: resolves the driver's internal format for a depth texture. */
    private static int depthFormat(int textureId) {
        GlStateManager._bindTexture(textureId);
        int format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL30.GL_TEXTURE_INTERNAL_FORMAT);
        GlStateManager._bindTexture(0);
        return format;
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
        double range=l.axial(), radius=range*l.lateral();
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
    private static float[] boxRect(AABB b, Matrix4f projView, double camX, double camY, double camZ) {
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
    private static void quad(BufferBuilder builder, float[] rect) {
        float minX = rect[0], minY = rect[1], maxX = rect[2], maxY = rect[3];
        quad(builder, minX, minY, 0.0F, maxX, minY, 0.0F, maxX, maxY, 0.0F, minX, maxY, 0.0F);
    }
    /**
     * Builds and uploads the per-tile light lists: one 32-bit mask per screen tile, bit {@code i} set
     * when light {@code i} covers that tile.
     * @returns false when there is nothing to upload, so the shader falls back to walking every light.
     */
    private static boolean uploadTiles(List<ScreenQuad> quads, int width, int height) {
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
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32UI, tx, ty, 0, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT, tileMasks);
        GlStateManager._bindTexture(0);
        return true;
    }
    /** NDC x or y to a tile index, clamped to the grid. */
    private static int tileIndex(float ndc, int extent, int tiles) {
        float pixel = (ndc * 0.5F + 0.5F) * extent;
        int tile = (int) Math.floor(pixel / TILE);
        return Math.max(0, Math.min(tiles - 1, tile));
    }

    /** Emits one screen-space quad covering the projection of the light's world-space bounds. */
    private record ScreenQuad(int lightIndex,float[] rect) {}

    private static void quad(BufferBuilder b, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        b.addVertex(ax, ay, az);
        b.addVertex(bx, by, bz);
        b.addVertex(cx, cy, cz);
        b.addVertex(dx, dy, dz);
    }
}
