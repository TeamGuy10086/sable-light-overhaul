package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.BlastVisualScale;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.client.ExtraConfig.ShockwaveMode;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * The shockwave drawn as a 3D spherical wavefront, brightened at its limb (solution 2).
 *
 * <p>Appearance is three config keys: meshBrightness (peak alpha), meshRingSharpness (how thin the\n * ring is) and meshSegments (tessellation), plus waveSpeed for how fast the front crosses its range.\n *\n * <p><b>Why a sphere.</b> A blast wave is a spherical shock, so the geometry is a closed sphere whose
 * radius is the same expansion curve the particle path uses. Its alpha comes from a Fresnel-style limb
 * term: a vertex is bright when the view ray grazes the surface there and transparent when the ray goes
 * straight through the middle, which projects to a crisp ring from any camera angle with no billboard
 * orientation and no sorting. The eight measured octant reaches are deliberately <em>not</em> used: the
 * user's call is that a shockwave is a sphere, and the octant measurement is what the particle path reads
 * for how it spreads its puffs.
 *
 * <p><b>Occlusion</b> is the depth buffer's job. The pass runs after the level with depth testing on and
 * depth writes off, so terrain hides the part of the sphere behind it -- and where the sphere has grown
 * through the ground, the surviving part is a ground-hugging ring for free, which is why nothing here
 * drags the lower half onto the floor any more.
 *
 * <p><b>Matrices are explicit, and that is the one part of this that is easy to get wrong.</b> The
 * pipeline could not be relied on for them: at {@code AFTER_CUTOUT_BLOCKS} (no shader pack) the shader's
 * model-view already is the camera matrix -- {@link SubLevelFarEntities} draws there with a plain pose
 * stack and world-minus-camera vertices and works -- while at {@code AFTER_LEVEL} (with a pack) the
 * leftover is whatever the pack's final composite left behind, which is also why
 * {@link GpuLightRenderer} hands its matrices to the shader explicitly instead of reading them back.
 * A version that baked the camera into the vertices on top of a live camera model-view therefore applied
 * the camera transform twice without a pack, and a version that trusted the leftover was wrong with one.
 * So this pass bakes the world-to-view matrix from the event into the vertices <em>and</em> pins the
 * shader's model-view to identity for the duration, which makes it correct at either stage.
 */
public final class ShockwaveMesh {
    private static final class NativeWaveType {
        private static final RenderType INSTANCE = RenderType.create(
                "slo_native_shockwave", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                1536, false, false, RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }
    private ShockwaveMesh() {}

    /** Unit sphere directions, rebuilt when the configured tessellation changes. */
    private static Vec3[][] unitSphere = new Vec3[0][];
    private static int unitSphereSegments = -1;

    /**
     * The directions of the sphere at the configured tessellation: {@code segments} longitudes, and half
     * as many latitudes. Cached, because rebuilding 36x18 vectors every frame would be silly, and rebuilt
     * only when the config value changes (the render thread is the only caller).
     */
    private static Vec3[][] unitSphere(int segments) {
        if (segments == unitSphereSegments) return unitSphere;
        int longitudes = Math.max(8, segments);
        int latitudes = Math.max(4, longitudes / 2);
        Vec3[][] built = new Vec3[latitudes + 1][longitudes + 1];
        for (int lat = 0; lat <= latitudes; lat++) {
            double phi = -0.5 * Math.PI + Math.PI * lat / latitudes;
            double cosPhi = Math.cos(phi);
            double sinPhi = Math.sin(phi);
            for (int lon = 0; lon <= longitudes; lon++) {
                double theta = 2.0 * Math.PI * lon / longitudes;
                built[lat][lon] = new Vec3(cosPhi * Math.cos(theta), sinPhi, cosPhi * Math.sin(theta));
            }
        }
        unitSphere = built;
        unitSphereSegments = segments;
        return built;
    }

    /**
     * How much a wave has faded by the time it has grown this far: 1 at birth, 0 at the end of its life.
     *
     * <p>Pure, so it can be tested; and read by {@link #limbAlpha}.
     */
    public static float lifeFade(double progress) {
        double p = Math.min(Math.max(progress, 0.0), 1.0);
        return (float) Math.max(0.0, 1.0 - p * p);
    }

    /**
     * The limb term: how bright the surface is where the view ray meets it at a given facing.
     *
     * <p>Pure. {@code facing} is the cosine between the surface normal and the direction from that point
     * to the camera: {@code ±1} looks straight through the sphere (transparent) and {@code 0} grazes the
     * silhouette (brightest). The absolute value is what makes the far half of the sphere light up at the
     * same silhouette as the near half, which is what turns the sphere into a ring; with additive
     * blending the two contributions simply add there.
     *
     * @param brightness peak alpha at the silhouette, the config's {@code meshBrightness}.
     * @param sharpness  exponent on the limb falloff, the config's {@code meshRingSharpness}: higher is a
     *                   thinner, harder ring.
     */
    public static float limbAlpha(double facing, float lifeFade, float brightness, double sharpness) {
        double rim = 1.0 - Math.min(1.0, Math.abs(facing));
        return (float) (brightness * Math.pow(rim, Math.max(1.0, sharpness)) * Math.max(0.0F, lifeFade));
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // Back at AFTER_LEVEL. Drawing at AFTER_TRANSLUCENT_BLOCKS was the neighbour mods' stage and would have put the wave
        // inside the pack, but the wave itself stopped being visible there -- the pack's later passes overwrite what is drawn
        // at that point, and the shield survives only because it carries its own render type and state. Being seen comes
        // first; the LoD depth test for this one mesh can come back with a render type of our own.
        boolean pack = IrisCompat.shaderPackInUse();
        // With a pack and the shader wave on, the composite pass draws the wave; drawing the mesh as well would double it.
        if (pack && IrisCompat.sloPackInUse() && ExtraConfig.SHOCKWAVE_SHADER.get()) return;
        RenderLevelStageEvent.Stage wanted = pack
                ? RenderLevelStageEvent.Stage.AFTER_LEVEL
                : RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS;
        if (event.getStage() != wanted) return;
        if (!ExtraConfig.SHOCKWAVE_ENABLED.get()) return;
        if (ExtraConfig.effectiveShockwaveMode() == ShockwaveMode.PARTICLES) return;
        List<Shockwave.Wave> waves = Shockwave.waves();
        if (waves.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = new PoseStack();
        // Iris batches entity buffers even with its shaderpack disabled. endBatch(type) is a no-op
        // there: view-space vertices would later receive the restored camera transform twice.
        BufferBuilder out = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // The three appearance knobs, read once per frame so a page or config edit is felt immediately.
        float brightness = ExtraConfig.SHOCKWAVE_MESH_BRIGHTNESS.get().floatValue();
        Vec3[][] sphere = unitSphere(ExtraConfig.SHOCKWAVE_MESH_SEGMENTS.get());
        double thickness = ExtraConfig.SHOCKWAVE_MESH_THICKNESS.get();


        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        pose.pushPose();
        // The whole world-to-view transform goes into the vertices, read fresh from the event rather than
        // from whatever the previous stage left in the pipeline.
        pose.last().pose().set(event.getModelViewMatrix());
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        try {
            for (Shockwave.Wave wave : waves) {
                emit(pose, out, wave, camera, partialTick, sphere, brightness, thickness);
            }
            // Submit immediately; never route these pre-transformed vertices through an entity batch.
            var mesh = out.build();
            if (mesh != null) (ClientFlashes.sloOnly() ? NativeWaveType.INSTANCE : RenderType.lightning()).draw(mesh);
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
            pose.popPose();
        }
    }

    /**
     * The front's radius at a tick: its own size, its own duration, its own pacing.
     *
     * <pre>
     *   r = size * (1 - (1 - t / life) ^ expansion)        // 0 at birth, size when the life is up
     * </pre>
     *
     * <p>No measurement and no warp: the size comes from the charge (see {@link #meshRadius}), the time is
     * a number the reader sets, and the exponent is the only thing left to decide how the distance is
     * spread between them. The average speed is therefore {@code size / life} and the launch speed is
     * {@code expansion} times that, both of which the debug line prints. Pure, so the motion can be tested
     * without a game.
     */
    public static double radiusAt(double size, double life, double expansion, double tick) {
        double span = Math.max(1.0E-6, life);
        double t = Math.min(Math.max(tick, 0.0), span) / span;
        double p = Math.max(1.0E-3, expansion);
        return size * (1.0 - Math.pow(1.0 - t, p));
    }

    /**
     * Blocks of radius per point of explosion power, at size coefficient 1.
     *
     * <p>The calibration is the user's own: they tuned the effect in game on a ground burst of a reinforced
     * high-explosive shell of power 24, and the radius they settled on was 60 blocks. So a coefficient of 1
     * reproduces exactly that, and everything else scales by its charge -- TNT (power 4) is 10 blocks, a
     * tier-6 blast 15, and so on up the {@code yieldTiers} ladder.
     */
    public static final double BLOCKS_PER_POWER = 60.0 / 24.0;

    /**
     * The radius a wave gets for a given charge: {@code coefficient * power * 2.5}, with a small floor so a
     * degenerate charge cannot ask for a wave of nothing. Pure, so the whole scaling rule can be tested.
     *
     * @param power       the explosion power from the server ({@code Explosion.radius()} / CBC's
     *                    {@code block_damaging_explosive_power}).
     * @param coefficient the config's {@code meshSize}: 1.0 is the calibrated effect.
     */
    public static double meshRadius(double power, double coefficient) {
        // Sub-linear on purpose. The calibration point stays exactly where it was -- power 24 gives 60 blocks at
        // coefficient 1 -- but a quarter of that charge used to get a quarter of the radius, which on screen read as a
        // wave that barely left the fireball. The 0.8 exponent gives it about a third of the radius instead, so a small
        // charge still throws a wave, and the ladder stays monotonic and continuous.
        double calibrated = Math.max(0.0, power) / 24.0;
        double curve = calibrated <= 0.0 ? 0.0 : Math.pow(calibrated, 0.8);
        return Math.max(2.0, curve * 60.0 * Math.max(0.0, coefficient) * BlastVisualScale.dimensions(power));
    }

    /** One wave: the shells between its inner and outer radius, with the limb term per vertex.
     *
     * <p>The thickness is drawn by <b>stacking concentric shells</b> across the band rather than by widening
     * one shell's edge: a single sphere has one radius, so softening its limb only lights up the cap facing
     * the camera (a blurry ball) and hardening it gives a hairline. Several shells whose skirts overlap
     * merge into one band, and the outermost keeps the configured edge sharpness so the front still has a
     * defined leading edge.
     */
    private static void emit(PoseStack pose, VertexConsumer out, Shockwave.Wave wave, Vec3 camera,
                             float partialTick, Vec3[][] sphere, float brightness,
                             double thickness) {
        double size = wave.meshSize * ExtraConfig.SHOCKWAVE_MESH_SCALE.get();
        if (size <= 0.05) return;
        double outer = radiusAt(size, wave.meshTicks, wave.meshExpansion, wave.age + partialTick);
        if (outer <= 0.05) return;
        double progress = Math.min(1.0, outer / size);
        float lifeFade = lifeFade(progress);
        if (lifeFade <= 1.0E-4F) return;

        pose.pushPose();
        // The explosion centre, relative to the camera: the view matrix above turns this into view space.
        pose.translate(wave.center.x - camera.x, wave.center.y - camera.y, wave.center.z - camera.z);
        PoseStack.Pose matrix = pose.last();
        Vec3 toCamera = camera.subtract(wave.center);
        double camDistSq = toCamera.lengthSqr();
        // One surface, not a stack of them. The band's width is the alpha profile across the limb: full at the
        // rim, gone by `width` cosines inside it. Stacking shells produced visible steps between them -- the wave
        // read as several nested bubbles -- and each extra shell was another whole sphere of quads.
        double width = Math.min(Math.max(thickness, 0.02), 0.6);
        for (int lat = 0; lat < sphere.length - 1; lat++) {
            for (int lon = 0; lon < sphere[lat].length - 1; lon++) {
                Vec3 n00 = sphere[lat][lon];
                Vec3 n01 = sphere[lat][lon + 1];
                Vec3 n11 = sphere[lat + 1][lon + 1];
                Vec3 n10 = sphere[lat + 1][lon];
                // Judge this piece on its own, where it actually is. With a shader pack the same decision is made
                // per fragment on the gpu; this keeps the packless path from drawing a wave inside a hill.
                // Only the half facing the camera is submitted. The far half used to be drawn too, which stacked a
                // second silhouette behind the first -- that is what made the wave read as a soap bubble -- and it
                // halves the quads. It is the same dot product the alpha needs, so nothing extra is computed.
                Vec3 pieceNormal = new Vec3((n00.x + n11.x) * 0.5, (n00.y + n11.y) * 0.5, (n00.z + n11.z) * 0.5);
                // A margin past the silhouette, not a hard cut at it: the band's brightest part sits exactly at dot == 0,
                // so culling on the sign alone would shave the outer half of whatever thickness was configured.
                double sloDot = pieceNormal.x * toCamera.x + pieceNormal.y * toCamera.y + pieceNormal.z * toCamera.z;
                if (sloDot < -0.25 * Math.sqrt(toCamera.lengthSqr())) continue;
                net.minecraft.world.phys.Vec3 piece = new net.minecraft.world.phys.Vec3(
                        (n00.x + n11.x) * 0.5, (n00.y + n11.y) * 0.5, (n00.z + n11.z) * 0.5).scale(outer).add(wave.center);
                if (VoxyAccess.blocked(camera, piece, 0)) continue;
                float a00 = bandAlpha(n00, outer, toCamera, camDistSq, lifeFade, brightness, width);
                float a01 = bandAlpha(n01, outer, toCamera, camDistSq, lifeFade, brightness, width);
                float a11 = bandAlpha(n11, outer, toCamera, camDistSq, lifeFade, brightness, width);
                float a10 = bandAlpha(n10, outer, toCamera, camDistSq, lifeFade, brightness, width);
                if (a00 <= 1.0E-4F && a01 <= 1.0E-4F && a11 <= 1.0E-4F && a10 <= 1.0E-4F) continue;
                emitVertex(out, matrix, n00, outer, a00);
                emitVertex(out, matrix, n01, outer, a01);
                emitVertex(out, matrix, n11, outer, a11);
                emitVertex(out, matrix, n10, outer, a10);
            }
        }
        pose.popPose();
    }

    /**
     * The inner radius of a wave's band: the configured share of the outer radius, so the thickness is
     * self-similar (the same fraction of the sphere at every size and at every moment) and never has to be
     * clamped away at the start, unlike an absolute thickness. Pure, so it can be tested.
     *
     * @param thickness the config's {@code meshThickness}, clamped to {@code 0..0.6}; 0 means "no band".
     */
    /** How many waves the composite pass can be told about at once. */
    public static final int SHADER_WAVES = 16;

    private static final org.joml.Vector4f[] WAVE_A = new org.joml.Vector4f[SHADER_WAVES];
    private static final org.joml.Vector4f[] WAVE_B = new org.joml.Vector4f[SHADER_WAVES];

    static {
        for (int i = 0; i < SHADER_WAVES; i++) {
            WAVE_A[i] = new org.joml.Vector4f();
            WAVE_B[i] = new org.joml.Vector4f();
        }
    }

    /**
     * Publishes the waves the composite pass draws: for each, the centre relative to the camera and the outer radius in
     * {@code sloWaveA<i>}, then the inner radius, the peak alpha and the band width in {@code sloWaveB<i>}.
     *
     * <p>Numbered names, never an array: an array uniform read as zero in every program for six rounds of testing, while
     * every plain {@code vec4} this mod declares has always arrived.
     */
    /** The uniform value for one wave, refreshed on every read of the frame. */
    public static org.joml.Vector4f waveVector(int index, boolean vectorA) {
        java.util.List<Shockwave.Wave> waves = Shockwave.waves();
        if (!ExtraConfig.SHOCKWAVE_SHADER.get() || index >= waves.size()) {
            WAVE_A[index].set(0.0F, 0.0F, 0.0F, 0.0F);
            WAVE_B[index].set(0.0F, 0.0F, 0.0F, 0.0F);
            return vectorA ? WAVE_A[index] : WAVE_B[index];
        }
        Shockwave.Wave wave = waves.get(index);
        var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float partialTick = net.minecraft.client.Minecraft.getInstance().getTimer()
                .getGameTimeDeltaPartialTick(false);
        double size = wave.meshSize * ExtraConfig.SHOCKWAVE_MESH_SCALE.get();
        double outer = radiusAt(size, wave.meshTicks, wave.meshExpansion, wave.age + partialTick);
        // The shell has four knobs that map straight onto what a reader wants to change: the total thickness as a share of
        // the radius, the share of it present at birth, the share left when the wave dies, and how the compression is spaced
        // in time. The shape is computed here so the shader only receives the width it has to draw.
        double total = Math.min(Math.max(ExtraConfig.SHOCKWAVE_MESH_THICKNESS.get(), 0.02), 1.0);
        double initial = Math.min(Math.max(ExtraConfig.SHOCKWAVE_MESH_INITIAL.get(), 0.0), 1.0);
        double last = Math.min(Math.max(ExtraConfig.SHOCKWAVE_MESH_FINAL.get(), 0.0), 1.0);
        double speed = Math.min(Math.max(ExtraConfig.SHOCKWAVE_MESH_SPEED.get(), 0.2), 4.0);
        double progress = Math.min(1.0, Math.max(0.0, (wave.age + partialTick) / Math.max(1.0F, wave.meshTicks)));
        double width = total * (initial + (last - initial) * Math.pow(progress, speed));
        float brightness = ExtraConfig.SHOCKWAVE_MESH_BRIGHTNESS.get().floatValue() * lifeFade(outer / size);
        WAVE_A[index].set((float) (wave.center.x - camera.x), (float) (wave.center.y - camera.y),
                (float) (wave.center.z - camera.z), (float) outer);
        // The fourth lane is the wave's age as a 0..1 fraction: the composite uses it to compress the condensation into the
        // Mach stem, which is the one thing the shader cannot know on its own.
        WAVE_B[index].set((float) innerRadius(outer, width), brightness, (float) width, (float) progress);
        return vectorA ? WAVE_A[index] : WAVE_B[index];
    }

    public static double innerRadius(double outer, double thickness) {
        double share = Math.min(Math.max(thickness, 0.0), 0.6);
        return Math.max(0.0, outer * (1.0 - share));
    }

    /**
     * The limb alpha of one vertex, without the trigonometry an actual intersection would need: the
     * cosine between the normal and the direction to the camera is {@code (normal·toCamera - r)} over the
     * distance from the camera to that surface point, and that distance is {@code |toCamera - r·normal|}.
     */
    /**
     * The alpha of one vertex in the band: full at the limb, gone {@code width} cosines inside it.
     *
     * <p>A smooth band rather than a power of the limb angle. The power curve gave a hairline for any sharpness
     * worth looking at, and the shells that used to fake thickness each drew their own hard circle, which is what
     * made the wave read as nested bubbles.
     */
    private static float bandAlpha(Vec3 normal, double radius, Vec3 toCamera, double camDistSq,
                                   float lifeFade, float brightness, double width) {
        double along = normal.x * toCamera.x + normal.y * toCamera.y + normal.z * toCamera.z;
        double distSq = camDistSq - 2.0 * radius * along + radius * radius;
        if (distSq < 1.0E-6) return 0.0F;
        double cosine = Math.max(-1.0, Math.min(1.0, (along - radius) / Math.sqrt(distSq)));
        double rim = 1.0 - Math.abs(cosine);
        // Flat across the ring, feathered only at the inner edge. A gradient put the brightest part on a hairline exactly
        // where the eye needs something to hold on to; a constant alpha is what makes the edge read as an edge.
        double inner = 1.0 - width;
        if (rim < inner) return 0.0F;
        double feather = Math.max(0.15 * width, 1.0E-3);
        double t = Math.min(1.0, (rim - inner) / feather);
        return (float) (brightness * t * Math.max(0.0F, lifeFade));
    }

    private static float vertexAlpha(Vec3 normal, double radius, Vec3 toCamera, double camDistSq,
                                     float lifeFade, float brightness, double sharpness) {
        double along = normal.x * toCamera.x + normal.y * toCamera.y + normal.z * toCamera.z;
        double distSq = camDistSq - 2.0 * radius * along + radius * radius;
        if (distSq < 1.0E-6) return 0.0F;
        return limbAlpha((along - radius) / Math.sqrt(distSq), lifeFade, brightness, sharpness);
    }

    /** One vertex, in the sphere's own space: the centre and the view matrix are already on the stack. */
    private static void emitVertex(VertexConsumer out, PoseStack.Pose matrix, Vec3 normal, double radius, float alpha) {
        out.addVertex(matrix, (float) (normal.x * radius), (float) (normal.y * radius), (float) (normal.z * radius))
                .setColor(1.0F, 1.0F, 1.0F, alpha);
    }
}
