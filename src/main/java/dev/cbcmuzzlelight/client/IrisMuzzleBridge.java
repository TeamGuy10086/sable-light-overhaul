package dev.cbcmuzzlelight.client;

import java.util.List;
import dev.cbcmuzzlelight.AreaLight;
import dev.cbcmuzzlelight.lighting.SourceColor;
import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Optional Iris bridge for shader packs that ship a CBC muzzle-light patch.
 *
 * <p>Publishes the active muzzle/tracer lights as plain vector uniforms. Iris assigns every
 * registered "fixed input" uniform a location in whichever program declares it, so the patched
 * shader only has to declare the names below; no {@code shaders.properties} entry is needed.
 *
 * <p>Positions are sent camera-relative: Iris shifts the world origin by large amounts, and an
 * offset from the camera is invariant under that shift. The pack's {@code scene_pos} is in the same
 * camera-relative, world-axis space, so {@code N·L} is a plain dot product there.
 */
public final class IrisMuzzleBridge {
    /** Keep in sync with the patched shader's loop bound. */
    public static final int MAX_LIGHTS = 16;
    private static final int STRIDE = 4;

    private record Frame(float[] position, float[] shape, float[] axis, float[] color) {}

    private static volatile Frame frame = new Frame(
            new float[MAX_LIGHTS * STRIDE], new float[MAX_LIGHTS * STRIDE], new float[MAX_LIGHTS * STRIDE],
            new float[MAX_LIGHTS * STRIDE]);

    /**
     * Which lights cast, one bit per buffer slot.
     *
     * <p>Set with the list, because a bit is an index and the list's order is what defines it. The mask
     * replaced a count ("the first N cast") for one reason above all: a count forces the mod to reorder
     * its light list to put a chosen light at a chosen index, which ties who casts to which lights survive
     * the budgets. A mask leaves the order free.
     */
    private static volatile int shadowMask;

    private static final Vector3f COLOR = new Vector3f();
    private static final Vector4f[] LIGHT = new Vector4f[MAX_LIGHTS];
    private static final Vector4f[] SHAPE = new Vector4f[MAX_LIGHTS];
    private static final Vector4f[] AXIS = new Vector4f[MAX_LIGHTS];
    private static final Vector4f[] TINT = new Vector4f[MAX_LIGHTS];

    static {
        for (int i = 0; i < MAX_LIGHTS; i++) {
            LIGHT[i] = new Vector4f();
            SHAPE[i] = new Vector4f();
            AXIS[i] = new Vector4f();
            TINT[i] = new Vector4f();
        }
    }

    private IrisMuzzleBridge() {}

    public static void register(UniformHolder holder, FrameUpdateNotifier notifier) {
        // Kept for compatibility: per-light colours below replaced the single global tint, and a
        // pack that still declares this uniform would otherwise lose its uniform registration.
        holder.uniform3f(UniformUpdateFrequency.PER_FRAME, "cbcMuzzleColor", () -> COLOR.set(
                ExtraConfig.GPU_RED.get().floatValue(),
                ExtraConfig.GPU_GREEN.get().floatValue(),
                ExtraConfig.GPU_BLUE.get().floatValue()));
        // FloatSupplier is named explicitly: uniform1f is also overloaded for IntSupplier/DoubleSupplier.
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, "cbcMuzzleIntensity",
                (FloatSupplier) () -> ExtraConfig.GPU_PACK_INTENSITY.get().floatValue());
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, "cbcMuzzleNormalInfluence",
                (FloatSupplier) () -> ExtraConfig.GPU_NORMAL.get().floatValue());
        // The mask travels with the list because its bits are the list's indices. Set by AdvancedLights
        // once a tick, with the frame-rate taper already applied where the slots were handed out.
        holder.uniform1i(UniformUpdateFrequency.PER_FRAME, "cbcMuzzleShadowMask", () -> shadowMask);
        // The tap ceiling is the player's and is read from the config every frame, so retuning it needs
        // no relog. A light may ask for less than this through axis.y and never for more.
        holder.uniform1i(UniformUpdateFrequency.PER_FRAME, "cbcMuzzleShadowSteps",
                () -> ExtraConfig.GPU_SHADOW_STEPS.get());
        for (int i = 0; i < MAX_LIGHTS; i++) {
            final int index = i;
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "cbcMuzzleLight" + index, () -> {
                Frame f = frame;
                int b = index * STRIDE;
                return LIGHT[index].set(f.position[b], f.position[b + 1], f.position[b + 2], f.position[b + 3]);
            });
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "cbcMuzzleShape" + index, () -> {
                Frame f = frame;
                int b = index * STRIDE;
                return SHAPE[index].set(f.shape[b], f.shape[b + 1], f.shape[b + 2], f.shape[b + 3]);
            });
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "cbcMuzzleAxis" + index, () -> {
                Frame f = frame;
                int b = index * STRIDE;
                return AXIS[index].set(f.axis[b], f.axis[b + 1], f.axis[b + 2], f.axis[b + 3]);
            });
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "cbcMuzzleTint" + index, () -> {
                Frame f = frame;
                int b = index * STRIDE;
                return TINT[index].set(f.color[b], f.color[b + 1], f.color[b + 2], f.color[b + 3]);
            });
        }
    }

    /**
     * Called from {@code AdvancedLights.gpuUpdate} on the client thread.
     *
     * <p>{@code softness} and {@code steps} are parallel to {@code lights} and carry each light's own
     * shadow behaviour. They ride in the light's axis vector, which is free for a sphere, so a lamp can
     * be soft while its neighbour is hard at no cost in uniforms.
     */
    static void update(List<AreaSnapshot> lights,int mask,double[] softness,int[] steps) {
        shadowMask=mask;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;
        float[] position = new float[MAX_LIGHTS * STRIDE];
        float[] shape = new float[MAX_LIGHTS * STRIDE];
        float[] axis = new float[MAX_LIGHTS * STRIDE];
        float[] color = new float[MAX_LIGHTS * STRIDE];
        // Flashing tints step every SourceColor.FLASH_TICKS ticks, so the phase is shared by every
        // light in one frame; a colour can never change between the lights of one draw.
        long tick = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
        int phase = (int) (tick / SourceColor.FLASH_TICKS);
        float[] rgb = new float[3];
        float defaultRed = ExtraConfig.GPU_RED.get().floatValue();
        float defaultGreen = ExtraConfig.GPU_GREEN.get().floatValue();
        float defaultBlue = ExtraConfig.GPU_BLUE.get().floatValue();
        int count = Math.min(lights.size(), MAX_LIGHTS);
        for (int i = 0; i < count; i++) {
            AreaSnapshot snapshot = lights.get(i);
            AreaLight light = snapshot.light;
            int b = i * STRIDE;
            position[b] = (float) (light.x() - camX);
            position[b + 1] = (float) (light.y() - camY);
            position[b + 2] = (float) (light.z() - camZ);
            // The spatial profile already carries the temporal strength curve.
            position[b + 3] = (float) (light.brightness() / 15.0);
            if (light.beam()) {
                // A spotlight is angular only: shape.x is the range cut-off and the axis carries
                // the half-angle tangent in w (a zero tangent marks every non-beam light).
                shape[b] = (float) light.axial();
                axis[b] = (float) light.dx();
                axis[b + 1] = (float) light.dy();
                axis[b + 2] = (float) light.dz();
                axis[b + 3] = (float) light.lateral();
            } else {
                double extentX = (snapshot.bounds.maxX - snapshot.bounds.minX) * 0.5;
                double extentY = (snapshot.bounds.maxY - snapshot.bounds.minY) * 0.5;
                double extentZ = (snapshot.bounds.maxZ - snapshot.bounds.minZ) * 0.5;
                // A single radius approximates the shape's reach; the profile fractions shape the falloff.
                shape[b] = (float) ((extentX + extentY + extentZ) / 3.0);
                // A sphere's axis vector is free -- axis.w == 0 is exactly what marks a light as a sphere
                // -- so the two numbers describing its shadow ride there. This is what lets one lamp be
                // soft while the next is hard, and one be given a larger tap budget than the rest, without
                // a single extra uniform.
                axis[b] = (float) (i < softness.length ? softness[i] : 0.0);
                axis[b + 1] = (float) (i < steps.length ? steps[i] : 0);
            }
            shape[b + 1] = (float) light.core();
            shape[b + 2] = (float) light.middle();
            shape[b + 3] = (float) light.tail();
            // Muzzle flashes and tracers carry palette index 0, so they keep the configured colour.
            SourceColor.rgb(snapshot.color, phase, rgb, defaultRed, defaultGreen, defaultBlue);
            color[b] = rgb[0];
            color[b + 1] = rgb[1];
            color[b + 2] = rgb[2];
            // tint.w carries the "do not light your own block" flag for the patched pack.
            color[b + 3] = snapshot.selfCull && ExtraConfig.GPU_SELF_CULL.get() ? 1.0F : 0.0F;
        }
        frame = new Frame(position, shape, axis, color);
    }
}
