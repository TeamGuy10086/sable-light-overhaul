package dev.sablelightoverhaul.client;

import java.util.List;
import dev.sablelightoverhaul.AreaLight;
import dev.sablelightoverhaul.lighting.LightSourceBlockEntity;
import net.minecraft.core.BlockPos;
import dev.sablelightoverhaul.lighting.SourceColor;
import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
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
 *
 * <p>The offset is subtracted <b>per frame</b>, in the uniform supplier, from the camera pose of the frame
 * being drawn -- the array holds world positions. It used to be baked into the array when the light list was
 * rebuilt, so the frame being drawn combined a current {@code scene_pos} with a light offset taken from an
 * older camera pose. The result was a light drawn displaced by however far the camera had travelled since
 * that rebuild, against the direction of travel and growing with speed: for a lamp standing on a floor the
 * displacement sinks it through the floor, the floor's terminator says the light is behind it, and the patch
 * of floor goes black. It appeared as a flicker that got worse the faster the player moved, and no change to
 * the shader could touch it because the wrong number arrived before any shader code ran.
 */
public final class IrisMuzzleBridge {
    /** Keep in sync with the patched shader's loop bound. */
    public static final int MAX_LIGHTS = 32;

    /** How many spotlights can be drawn as a beam and an aperture glow at once. */
    public static final int MAX_BEAMS = 8;
    private static final org.joml.Vector4f SMOKE_OPTIONS=new org.joml.Vector4f();
    private static final org.joml.Vector4f[] BEAM_A = new org.joml.Vector4f[MAX_BEAMS];
    private static final org.joml.Vector4f[] BEAM_B = new org.joml.Vector4f[MAX_BEAMS];
    private static final org.joml.Vector4f[] BEAM_C = new org.joml.Vector4f[MAX_BEAMS];
    private static final org.joml.Vector4f[] BEAM_D = new org.joml.Vector4f[MAX_BEAMS];
    /** The shadow map's frame: {@code xyz} across the beam, {@code w} the map's row block plus one (0 = none). */
    private static final org.joml.Vector4f[] BEAM_E = new org.joml.Vector4f[MAX_BEAMS];
    /** The shadow map's other axis and its range in blocks. */
    private static final org.joml.Vector4f[] BEAM_F = new org.joml.Vector4f[MAX_BEAMS];
    private static final org.joml.Vector4f[] BEAM_G = new org.joml.Vector4f[MAX_BEAMS];
    private static final double[][] BEAM_WORLD = new double[MAX_BEAMS][3];
    private static int beamCount;

    static {
        for (int i = 0; i < MAX_BEAMS; i++) {
            BEAM_A[i] = new org.joml.Vector4f();
            BEAM_B[i] = new org.joml.Vector4f();
            BEAM_C[i] = new org.joml.Vector4f();
            BEAM_D[i] = new org.joml.Vector4f();
            BEAM_E[i] = new org.joml.Vector4f();
            BEAM_F[i] = new org.joml.Vector4f();
            BEAM_G[i] = new org.joml.Vector4f();
        }
    }

    /** Clears the beam slots; the client calls this once per frame before walking its sources. */
    public static void beginBeams() {
        beamCount = 0;
        for (int i = 0; i < MAX_BEAMS; i++) {
            BEAM_A[i].set(0.0F, 0.0F, 0.0F, 0.0F);
            BEAM_B[i].set(0.0F, 0.0F, 0.0F, 0.0F);
            BEAM_C[i].set(0.0F, 0.0F, 0.0F, 0.0F);
            BEAM_D[i].set(0.0F, 0.0F, 0.0F, 0.0F);
            // Zero here is load bearing: w == 0 tells the composite this beam has no shadow map and must use
            // the cheap gate, so a slot that is not published this frame can never be read with a stale one.
            BEAM_E[i].set(0.0F, 0.0F, 0.0F, 0.0F);
            BEAM_F[i].set(0.0F, 0.0F, 0.0F, 0.0F);
            BEAM_G[i].set(0,0,0,0);
        }
    }

    /**
     * Publishes one spotlight for the composite pass: where its mouth is, which way it points, how wide its cone
     * opens and what colour it is.
     *
     * <p>Camera relative, like every other light, because the shader subtracts the same camera offset from the
     * scene position. {@code aperture} is the radius the glowing mouth is drawn at, in blocks.
     */
    public static int addBeam(net.minecraft.world.phys.Vec3 mouth, net.minecraft.world.phys.Vec3 axis,
                               double cosHalf, float aperture, float intensity, float red, float green, float blue,
                               float range, int falloff, int volumeMode) {
        if (beamCount >= MAX_BEAMS) return -1;
        int index = beamCount++;
        var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        BEAM_WORLD[index][0]=mouth.x;BEAM_WORLD[index][1]=mouth.y;BEAM_WORLD[index][2]=mouth.z;
        BEAM_A[index].set((float) (mouth.x - camera.x), (float) (mouth.y - camera.y), (float) (mouth.z - camera.z),
                Math.max(0.05F, aperture));
        // The axis is published scaled by the range: its length is the reach, so one vec4 carries both.
        double reach = Math.max(0.01, range);
        BEAM_B[index].set((float) (axis.x * reach), (float) (axis.y * reach), (float) (axis.z * reach),
                (float) Math.max(-0.99F, Math.min(0.999999F, cosHalf)));
        BEAM_C[index].set(red, green, blue, Math.max(0.0F, intensity));
        // Offset by one: the shader has to tell "mode 0" apart from "this jar never sent a mode", and an
        // unregistered uniform reads as zero. See docs/DEVELOPMENT.zh-CN.md 6.26.3.
        BEAM_D[index].set((float) falloff + 1.0F, Math.clamp(volumeMode,0,2), Math.max(0.0F, aperture), 0.0F);
        BEAM_B[index].w = BEAM_B[index].w;
        return index;

    }

    /**
     * Points one beam at its shadow map: the frame the map was cast in, and how far it reaches.
     *
     * <p>{@code w} of the frame vector is the map's row block plus one, so zero keeps meaning "no map" even
     * though row zero is a real row -- a beam that has never been cast must not read the first block of the
     * texture, which belongs to whichever lamp happened to be first.
     */
    static int beamCount() { return beamCount; }
    static org.joml.Vector4f beamAxis(int index) { return BEAM_B[index]; }
    static org.joml.Vector4f beamColor(int index) { return BEAM_C[index]; }

    static org.joml.Vector4f beamPosition(int index) {
        var camera=net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var world=BEAM_WORLD[index];var out=BEAM_A[index];
        return out.set((float)(world[0]-camera.x),(float)(world[1]-camera.y),(float)(world[2]-camera.z),out.w);
    }

    public static void beamShadow(int beam, double rightX, double rightY, double rightZ,
                                  double upX, double upY, double upZ, double range, double offsetX, double offsetY, double offsetZ, double tanHalf, double aperture) {
        if (beam < 0 || beam >= MAX_BEAMS) return;
        BEAM_E[beam].set((float) rightX, (float) rightY, (float) rightZ, beam + 1.0F);
        BEAM_F[beam].set((float) upX, (float) upY, (float) upZ, (float) range);
        BEAM_G[beam].set((float)offsetX,(float)offsetY,(float)offsetZ,(float)tanHalf);
        BEAM_D[beam].w=(float)aperture;
    }

    /** The reach of each beam, kept beside the vectors so the shader gets it as one packed float. */
    private static final float[] WAVE_BEAM_RANGE = new float[MAX_BEAMS];

    /**
     * The emissive ratio a lamp on the field model hands the shader, as {@code tint.w}.
     *
     * <p>The pack's own emissive blocks are drawn by {@code material.emission * emission_scale}, where
     * {@code emission_scale} is 40 and {@code material.emission} is a per-block coefficient taken from its
     * hardcoded table -- around 0.25 for the lower tiers and more for a glowstone. Taking the lamp's vanilla
     * light away means its block falls out of that table entirely, so the mod has to supply the coefficient
     * itself, and 0.375 is the tier that matched the block this was tuned against.
     *
     * <p>It is one number for every lamp today. The original block's type IS known when the stand-in is placed,
     * so a per-block table could replace this constant -- the plumbing is the {@code tint.w} slot, which is
     * already carrying it.
     */
    public static final float FIELD_EMISSION_RATIO = 0.375F;
    /**
     * The emissive ratio per original block, read off the pack's own hardcoded table.
     *
     * <p>Photon puts a vanilla light source on a tier and multiplies everything by 40: a torch and a glowstone
     * are not drawn equally hot in it, and a lamp that replaces one should not be either. These are ours,
     * transcribed by tier from {@code include/surface/material.glsl} rather than looked up, because the pack
     * keys on its own {@code material_mask} and the mod cannot read that.
     *
     * <p>Anything not listed -- which is every block in a modded pack -- gets {@link #FIELD_EMISSION_RATIO},
     * the tier that was tuned by eye against a glowstone and matched it.
     */
    private static final java.util.Map<String, Float> EMISSION_RATIOS = java.util.Map.ofEntries(
            java.util.Map.entry("minecraft:glowstone", 0.40F),
            java.util.Map.entry("minecraft:shroomlight", 0.40F),
            java.util.Map.entry("minecraft:sea_lantern", 0.40F),
            java.util.Map.entry("minecraft:jack_o_lantern", 0.35F),
            java.util.Map.entry("minecraft:lantern", 0.35F),
            java.util.Map.entry("minecraft:torch", 0.30F),
            java.util.Map.entry("minecraft:wall_torch", 0.30F),
            java.util.Map.entry("minecraft:soul_lantern", 0.30F),
            java.util.Map.entry("minecraft:soul_torch", 0.30F),
            java.util.Map.entry("minecraft:soul_wall_torch", 0.30F),
            java.util.Map.entry("minecraft:campfire", 0.25F),
            java.util.Map.entry("minecraft:soul_campfire", 0.25F),
            java.util.Map.entry("minecraft:crying_obsidian", 0.20F),
            java.util.Map.entry("minecraft:magma_block", 0.20F),
            java.util.Map.entry("minecraft:lava", 0.60F));

    /**
     * The emissive ratio for the block this lamp replaced, or the default when it is not a block we know.
     *
     * <p>The stand-in remembers the block it took over -- that is how it draws the original's model -- so the
     * answer is available at exactly this point and needed nowhere else.
     */
    private static float emissionRatio(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos at) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
        var standIn = level.getBlockEntity(at) instanceof LightSourceBlockEntity lamp
                ? lamp.originalState() : null;
        if (standIn == null || standIn.isAir()) return FIELD_EMISSION_RATIO;
        return EMISSION_RATIOS.getOrDefault(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(standIn.getBlock()).toString(),
                FIELD_EMISSION_RATIO);
    }
    private static final int STRIDE = 4;

    private record Frame(float[] position, float[] shape, float[] axis, float[] color, float[] decay,
                         int count, int cloudMask, float[] cloudReach, int[] style, float[] cones,
                         float[] bodies, String[] keys) {}

    /** The light key a slot holds, or null for a slot past the end of the list. */
    private static String keyAt(Frame frame, int index) {
        String[] keys = frame.keys();
        return keys != null && index < keys.length ? keys[index] : null;
    }

    /**
     * How far into the current tick this frame is drawn, which is the renderer's own fraction.
     *
     * <p>Zero when it cannot be read, which is the safe direction: every per-frame placement then lands on the
     * tick pose, exactly what this mod did before any of it, rather than on a guess.
     */
    private static double partialTick() {
        // What Sable drew the hull with, when it has drawn one. This is not a re-derivation: the mixin stores
        // the very argument ClientSubLevel.renderPose received, so there is nothing left to be out of phase with.
        float recorded = dev.sablelightoverhaul.client.SableRenderTick.last();
        if (Float.isFinite(recorded)) return Math.clamp(recorded, 0.0, 1.0);
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.getTimer() == null) return 1.0;
            // false, not true: this is the exact call Sable's own level renderer makes when it draws a section
            // (its bytecode passes iconst_0). The two agree only at a perfect twenty ticks a second; away from
            // that they differ by a fraction of a tick, and a lamp placed with the wrong one leads or trails the
            // hull it stands on -- which is the flicker, and the reason a field lamp never showed it: a field is
            // sampled relative to its own lamp, so an error in the lamp's absolute position cancels out.
            double value = client.getTimer().getGameTimeDeltaPartialTick(false);
            return Double.isFinite(value) ? Math.clamp(value, 0.0, 1.0) : 1.0;
        } catch (RuntimeException ex) {
            return 1.0;
        }
    }

    private static volatile Frame frame = new Frame(
            new float[MAX_LIGHTS * STRIDE], new float[MAX_LIGHTS * STRIDE], new float[MAX_LIGHTS * STRIDE],
            new float[MAX_LIGHTS * STRIDE], new float[MAX_LIGHTS], 0, 0, new float[MAX_LIGHTS],
            new int[MAX_LIGHTS], new float[MAX_LIGHTS * STRIDE], identityBodies(), new String[MAX_LIGHTS]);

    /** Eight identity quaternions: the world, which is what a lamp on the ground is measured in. */
    private static float[] identityBodies() {
        float[] q = new float[MAX_BODIES * STRIDE];
        for (int i = 0; i < MAX_BODIES; i++) q[i * STRIDE + 3] = 1.0F;
        return q;
    }

    /** The six faces this mod's masks use, in the shader's bit order (see slo_light.glsl). */
    public static final int STYLE_FACES = 63;
    /** The bit that says "lit like vanilla block light" rather than by the mod's shaped term. */
    public static final int STYLE_FLOOD = 64;
    /**
     * The sixth falloff profile: a point light whose brightness also falls with the square of the distance.
     *
     * <p>It is a profile, chosen per light the way the other five are, and not a global switch -- the term is
     * measured in blocks and is merciless (ten blocks out a light keeps one percent), so it is something an
     * owner asks for on one lamp rather than a law imposed on every lamp. It is read from the light's own
     * profile being negative, which is how mode 5 travels without a new field on every light constructor.
     */
    public static final int STYLE_PHYSICAL = 8192;
    /**
     * The bit that says "read this lamp's light field in its body's frame".
     *
     * <p>A field on a hull was flooded in the hull's plot, so the shader has to move the sample into that
     * frame -- but it must also know that this lamp is one of those, or a lamp on the ground would be
     * rotated by a body it is not on. The rotation itself rides in the light's own shape vector
     * ({@code yzw}), not in the shared body table: a field lamp never reads core/middle/tail, so three
     * floats are free, and the fourth component of a unit quaternion can be recovered from the other
     * three. That keeps the field independent of the table -- a table that is full, or a lookup that
     * returns the world slot, used to send the sample through an identity rotation, which looks exactly
     * like "the hull's shading changes when the hull turns".
     */
    /**
     * The bit that says "tint.w is an emissive ratio", i.e. draw this lamp's own block the way the block it
     * replaced was drawn.
     *
     * <p>Without it the shader has to guess what tint.w means, and the two meanings are both plausible
     * for a lamp: a ratio drawn as a self-cull flag lights the lamp's own block with its own light, and a
     * flag drawn as a ratio makes a black block. The bit is what makes the same look survive a page switching
     * between the two models.
     */
    public static final int STYLE_EMISSION = 32768;
    public static final int STYLE_FIELD_BODY = 16384;
    /** Where the shadow-cone count sits in the style word (three bits, 0..4 cones). */
    public static final int STYLE_CONE_SHIFT = 7;
    /** Where the physics body whose frame the cones are measured in sits (three bits, 0 = the world). */
    public static final int STYLE_BODY_SHIFT = 10;

    /**
     * One light's style word: bits 0..5 the faces it emits through, bit 6 the flood mode, bits 7..9 the
     * number of shadow cones, bits 10..12 the body its cones are measured in.
     *
     * <p>Packed here rather than in the light list because these bits are the shader's contract -- the
     * same layout the {@code SLO_FACE_*} and {@code SLO_STYLE_*} defines describe -- and a second place
     * to get them wrong is a second place to debug.
     */
    public static int style(AreaSnapshot snapshot) {
        int cones = 0;
        if (snapshot.coneA != 0) cones++;
        if (snapshot.coneB != 0) cones++;
        if (snapshot.coneC != 0) cones++;
        if (snapshot.coneD != 0) cones++;
        // A negative core is mode 5's flag (see BeamLight.profile): the shader's sphere branch reads this bit
        // and adds the physical term, and every other profile is unaffected because none of them is negative.
        boolean physical = snapshot.light != null && snapshot.light.core() < 0.0;
        return (snapshot.faces & STYLE_FACES)
                | (snapshot.flood ? STYLE_FLOOD : 0)
                | (physical ? STYLE_PHYSICAL : 0)
                | (snapshot.bodyQuat != null ? STYLE_FIELD_BODY : 0)
                | (cones << STYLE_CONE_SHIFT)
                | ((snapshot.bodySlot & 7) << STYLE_BODY_SHIFT);
    }

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
    /**
     * The distance ramp's knee, one float per light, packed four to a vec4.
     *
     * <p>A beam that fades along its length needs a second curve -- how fast -- beside the angular profile it
     * already carries, and a beam's four vectors are all spoken for. One float per light is enough, so four
     * uniforms carry every one of them rather than a new uniform per light.
     */
    private static final Vector4f[] DECAY = new Vector4f[MAX_LIGHTS / 4];
    /** The two cloud quality switches and the intensity, one vec4 for the whole frame. */
    private static final Vector4f CLOUD = new Vector4f();
    /**
     * One lamp's shadow cones: four packed words, one per vec4 component, each a float whose bits are the
     * four bytes of angles. Packed as float bits because Iris's uniform API has no integer vectors, and
     * because a float carries 32 bits exactly -- no precision is lost, it is just a carrier.
     */
    private static final Vector4f[] CONE = new Vector4f[MAX_LIGHTS];
    /** One slot's field origin as it is handed over, one vector per slot. */
    private static final Vector4f[] FIELD_SLOT = new Vector4f[MAX_LIGHTS];
    /**
     * The rotation of each physics body the cones are measured in, as a quaternion; body 0 is the world.
     *
     * <p>Published every frame even though the table is only filled by one caller: an unpublished uniform
     * reads back as all zeros, and a zero quaternion rotates a direction into nothing, so a lamp with cones
     * would test a different rectangle than the one its page was given. Identity is the honest default --
     * it means "the cones are in world angles", which is exactly right for a lamp on the ground.
     */
    public static final int MAX_BODIES = 8;
    private static final Vector4f[] BODY = new Vector4f[MAX_BODIES];

    static {
        for (int i = 0; i < MAX_LIGHTS; i++) {
            LIGHT[i] = new Vector4f();
            SHAPE[i] = new Vector4f();
            AXIS[i] = new Vector4f();
            TINT[i] = new Vector4f();
            CONE[i] = new Vector4f();
            FIELD_SLOT[i] = new Vector4f();
        }
        for (int i = 0; i < DECAY.length; i++) DECAY[i] = new Vector4f();
        for (int i = 0; i < MAX_BODIES; i++) BODY[i] = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
    }

    /** Per slot: the light field's origin (x, y, z) and its layer + 1, or zero for "no field". */
    private static final float[] FIELD_SLOTS = new float[MAX_LIGHTS * 4];
    /**
     * The scene-space copy of {@link #FIELD_SLOTS} that the uniform actually carries.
     *
     * <p>It has to be a different array. Iris calls a PER_FRAME supplier once for <em>every</em> program it
     * builds, and a frame builds many, so a supplier that rewrites its own source applies its transform once per
     * program rather than once per frame. Doing the camera shift in place turned the origins into "world origin
     * minus camera, seven times over" by the last program of a frame: every slot past the first two ended up tens
     * of thousands of blocks from the surface being shaded and the lamps that owned them lit nothing at all. The
     * two that survived were the two the light list rewrites every tick, which is why it read as "some lamps work
     * and some do not" instead of as arithmetic.
     */
    private static final float[] FIELD_PACKED = new float[MAX_LIGHTS * 4];
    /** The array the light list writes; world space, filled once a tick. */
    static float[] fieldSlots() {
        return FIELD_SLOTS;
    }

    /** What the pack was last handed: the same slots in scene space. For the debug line, not for writing. */
    static float[] packedSlots() {
        return FIELD_PACKED;
    }

    /**
     * The value each slot's field gate carries, and the lamp block the shader derives from its position.
     *
     * <p>This is the only honest view of the field handshake. The layer travels in the beam knee now, so a zero
     * gate means the pack draws the ray model for that lamp; a lamp that does not light is therefore either a
     * zero here or a fault after it, and those two want opposite fixes.
     */
    private static final float[] FIELD_DEBUG_GATE = new float[MAX_LIGHTS * 2];
    private static final float[] FIELD_DEBUG_ANCHOR = new float[MAX_LIGHTS * 3];
    /**
     * The rotation a field lamp asked the shader to read its field in, as x,y,z, and 0 when it asked for the
     * world's own frame. Printed so that "the hull's shading moves when the hull turns" can be told apart
     * from "the shader never got the rotation" without reading the shader.
     */
    private static final float[] FIELD_DEBUG_QUAT = new float[MAX_LIGHTS * 3];
    private static int currentCount;

    /**
     * What the shader was actually handed for each slot: the light vector as uploaded, the shape vector, the
     * style word and the decay word.
     *
     * <p>Worth the eight hundred bytes because every question about this mod's shading ends in one of two
     * answers -- the shader was given the right numbers and drew something else, or it was given something
     * nobody intended. Reading the values back from inside the uniform supplier is the only place that
     * distinction exists: the light list it came from may be right while the upload is not.
     */
    private static final float[] DEBUG_LIGHT = new float[MAX_LIGHTS * STRIDE];
    private static final float[] DEBUG_SHAPE = new float[MAX_LIGHTS * STRIDE];
    private static final int[] DEBUG_STYLE = new int[MAX_LIGHTS];
    private static final float[] DEBUG_AXIS = new float[MAX_LIGHTS * STRIDE];
    private static final float[] DEBUG_DECAY = new float[MAX_LIGHTS];

    static String describeUniforms() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < currentCount && i < 5; i++) {
            int b = i * STRIDE;
            text.append(' ').append(i)
                .append(":L=(").append(fmt(DEBUG_LIGHT[b])).append(',').append(fmt(DEBUG_LIGHT[b + 1]))
                .append(',').append(fmt(DEBUG_LIGHT[b + 2])).append(',').append(fmt(DEBUG_LIGHT[b + 3]))
                .append(") S=(").append(fmt(DEBUG_SHAPE[b])).append(',').append(fmt(DEBUG_SHAPE[b + 1]))
                .append(',').append(fmt(DEBUG_SHAPE[b + 2])).append(',').append(fmt(DEBUG_SHAPE[b + 3]))
                .append(") A=(").append(fmt(DEBUG_AXIS[b])).append(',').append(fmt(DEBUG_AXIS[b + 1]))
                .append(',').append(fmt(DEBUG_AXIS[b + 2])).append(',').append(fmt(DEBUG_AXIS[b + 3]))
                .append(") style=").append(DEBUG_STYLE[i])
                .append(" decay=").append(fmt(DEBUG_DECAY[i]));
            if (i == 0) {
                // Which clock the ship lamps are placed with: "s" = the partial tick Sable itself drew the hull
                // with (the mixin applied), "t" = the timer fallback (it did not). Without this mark a mixin that
                // silently failed to apply is indistinguishable from one that applied and changed nothing.
                text.append(" pose=").append(fmt((float) partialTick()))
                    .append(Float.isFinite(dev.sablelightoverhaul.client.SableRenderTick.last()) ? "s" : "t");
            }
        }
        return text.length() == 0 ? "none" : text.toString();
    }

    private static String fmt(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
    static String describeGate() {
        // The texture name itself: the shader's field sample reads magenta, which means the branch runs and the
        // sample is zero. Either the texture was never made, or the name the sampler hands Iris is not the one
        // being uploaded. A zero here is the answer to the first, and it is visible nowhere else.
        StringBuilder text = new StringBuilder("fieldTexture=").append(LightFields.texture())
                .append(' ').append(LightFields.describeUpload());
        for (int slot = 0; slot < currentCount && slot < MAX_LIGHTS; slot++) {
            text.append(" slot").append(slot)
                .append(":gate=").append(FIELD_DEBUG_GATE[slot * 2])
                .append(" flood=").append((int) FIELD_DEBUG_GATE[slot * 2 + 1]);
            if (FIELD_DEBUG_GATE[slot * 2] > 0.0F) {
                text.append(" anchor=(").append(Math.round(FIELD_DEBUG_ANCHOR[slot * 3]))
                    .append(',').append(Math.round(FIELD_DEBUG_ANCHOR[slot * 3 + 1]))
                    .append(',').append(Math.round(FIELD_DEBUG_ANCHOR[slot * 3 + 2])).append(')');
            }
            if (FIELD_DEBUG_GATE[slot * 2 + 1] > 0.5F && FIELD_DEBUG_GATE[slot * 2] > 0.0F) {
                text.append(" q=(").append(String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f",
                        FIELD_DEBUG_QUAT[slot * 3], FIELD_DEBUG_QUAT[slot * 3 + 1], FIELD_DEBUG_QUAT[slot * 3 + 2]))
                    .append(')');
            }
        }
        return text.length() == 0 ? " no lights" : text.toString();
    }

    /**
     * The camera offset of the frame being drawn, in blocks.
     *
     * <p>Every position this mod hands the pack goes through here, because the pack's {@code scene_pos} is
     * camera-relative while everything the mod computes is in world coordinates. Subtracting an origin from a
     * camera-relative position without doing the same to the origin yields the camera's own position, negated --
     * a number in the tens of blocks, growing with how far the player has walked, and identical for every surface
     * in the frame. That is what a light field looked like when its origin was published raw: not a wrong pool
     * but no pool at all, because the shader's bounds test against the 32-block box rejected every sample.
     */
    private static double[] cameraOffset() {
        var position = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return new double[] { position.x, position.y, position.z };
    }

    /**
     * Copy every field slot from world space into the pack's scene space.
     *
     * <p>Pure by construction, and that is the point: it reads {@link #FIELD_SLOTS} and writes
     * {@link #FIELD_PACKED}, so calling it any number of times in a frame gives the same answer. It is the camera
     * offset as an argument rather than a field so it can be tested without a running game -- the fault it
     * replaces was invisible in game, because a supplier that consumes its own source still returns a plausible
     * array, just a differently wrong one for each of the many programs a frame builds.
     *
     * <p>{@code lights} is how many slots this frame's light list filled. Everything past it is cleared, because
     * neither array is rebuilt from scratch: a slot that carried a lamp when more lights were in range keeps its
     * layer number, and the shader cannot tell a live field from the leftovers of one. Slots past the list were
     * publishing the same field origins under two or three different indices, which is what a lamp lighting the
     * ground from places it is not looks like.
     */
    static float[] toSceneSpace(double[] camera, int lights) {
        for (int slot = 0; slot < MAX_LIGHTS; slot++) {
            int base = slot * STRIDE;
            if (slot >= lights) {
                FIELD_PACKED[base] = FIELD_PACKED[base + 1] = FIELD_PACKED[base + 2] = FIELD_PACKED[base + 3] = 0.0F;
                continue;
            }
            FIELD_PACKED[base] = (float) (FIELD_SLOTS[base] - camera[0]);
            FIELD_PACKED[base + 1] = (float) (FIELD_SLOTS[base + 1] - camera[1]);
            FIELD_PACKED[base + 2] = (float) (FIELD_SLOTS[base + 2] - camera[2]);
            FIELD_PACKED[base + 3] = FIELD_SLOTS[base + 3];
        }
        return FIELD_PACKED;
    }

    private IrisMuzzleBridge() {}

    private static final Vector4f TANK_GAS_SCALE=new Vector4f(),BIG_GAS_SCALE=new Vector4f();
    private static final Vector4f AUTO_GAS_A=new Vector4f(),AUTO_GAS_B=new Vector4f();
    public static void register(UniformHolder holder, FrameUpdateNotifier notifier) {
        holder.uniform4f(UniformUpdateFrequency.PER_FRAME,"sloBigGasScale",()-> {
            float core=MuzzleClientConfig.BIG_CORE_SCALE.get().floatValue(),gas=MuzzleClientConfig.BIG_GAS_SCALE.get().floatValue(),jet=MuzzleClientConfig.BIG_JET_SCALE.get().floatValue();
            return BIG_GAS_SCALE.set(core,gas,jet,Math.max(core,Math.max(gas,jet)));
        });
        holder.uniform4f(UniformUpdateFrequency.PER_FRAME,"sloTankGasScale",()-> {
            float core=MuzzleClientConfig.TANK_CORE_SCALE.get().floatValue();
            float gas=MuzzleClientConfig.TANK_GAS_SCALE.get().floatValue();
            float jet=MuzzleClientConfig.TANK_JET_SCALE.get().floatValue();
            return TANK_GAS_SCALE.set(core,gas,jet,Math.max(core,Math.max(gas,jet)));
        });
        // Autocannon lobed field: one continuous gas body, not extra billboards. The shader treats
        // strength 0 as the exact 0.1.505 stream, so these two uniforms are also the live A/B switch.
        holder.uniform4f(UniformUpdateFrequency.PER_FRAME,"sloSmokeOptions",()-> SMOKE_OPTIONS.set(
            BigSmokeConfig.FLIPBOOK.get()?1:0,BigSmokeConfig.SIMPLE_LIGHTING.get()?1:0,
            BigSmokeConfig.AMBIENT_GAIN.get().floatValue(),BigSmokeConfig.EMISSION_GAIN.get().floatValue()));
        holder.uniform4f(UniformUpdateFrequency.PER_FRAME,"sloAutoGas",()-> AUTO_GAS_A.set(
            MuzzleClientConfig.AUTO_LOBE_COUNT.get().floatValue(),
            MuzzleClientConfig.AUTO_LOBE_STRENGTH.get().floatValue(),
            MuzzleClientConfig.AUTO_LOBE_TWIST.get().floatValue(),
            MuzzleClientConfig.AUTO_LOBE_LENGTH_SPREAD.get().floatValue()));
        holder.uniform4f(UniformUpdateFrequency.PER_FRAME,"sloAutoGas2",()-> AUTO_GAS_B.set(
            MuzzleClientConfig.AUTO_LOBE_SHARPNESS.get().floatValue(),
            MuzzleClientConfig.AUTO_CORE_FOCUS.get().floatValue(),
            MuzzleClientConfig.AUTO_EDGE_SOFTNESS.get().floatValue(),
            0.0F));
        // The vanilla light field, one 3D texture for every lamp that uses that model. Per slot: the
        // field's origin corner and its layer plus one, or all zero for a lamp that has no field and
        // therefore falls back to the ray model. The mod fills this array once a tick from the light list.
        // The origin is shifted by the camera here, per frame, exactly as every light position is: the
        // shader subtracts it from scene_pos, so the two have to be in the same space. Publishing it raw was
        // half of why the light field never drew anything -- see cameraOffset.
        //
        // toSceneSpace is pure: it reads FIELD_SLOTS and writes FIELD_PACKED, never in place. This supplier runs
        // once per program and a frame builds many, so an in-place shift subtracts the camera once per program
        // instead of once per frame -- see FIELD_PACKED for what that looked like in game.
        // No uniform at all for the field origin: it is derived in the shader from a channel that is already
        // proven to arrive.
        //
        // "sloFieldOrigin" was tried three ways and never delivered a value. As a packed array
        // (uniform4fArray) the shader read w==0 in every program for six rounds of testing, so the branch that
        // needs it was never entered; as "sloFieldOrigin[i]" and as "sloFieldOrigin0..31" Iris resolved no
        // location for any of them and both lamps went dark. Every other uniform in this class -- sloLightN,
        // sloShapeN, sloAxisN, sloTintN, sloStyleN, sloDecayN, sloBodyN -- has always worked, and the difference
        // is that each of those is a name the shader declares as one vec4.
        //
        // So the field travels in sloDecayN.w, which a flood lamp does not otherwise use: zero for a ray light,
        // layer + 1 for a lamp on the field model. The origin needs no channel of its own, because the field is
        // anchored on the lamp's own block and the lamp's position is already in sloLightN.xyz, which arrives.
        // Kept for compatibility: per-light colours below replaced the single global tint, and a
        // pack that still declares this uniform would otherwise lose its uniform registration.
        // The detonation wave, published as numbered vec4 pairs for the composite pass. The Iris types stay in this
        // class on purpose: the bytecode test forbids them anywhere else, and the wave itself only hands out vectors.
        for (int wave = 0; wave < ShockwaveMesh.SHADER_WAVES; wave++) {
            final int waveIndex = wave;
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloWaveA" + waveIndex,
                    () -> ShockwaveMesh.waveVector(waveIndex, true));
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloWaveB" + waveIndex,
                    () -> ShockwaveMesh.waveVector(waveIndex, false));
        }
        // Spotlight beams and their aperture glow, one numbered sextet per slot (A..F). Numbered vec4s, never
        // arrays: an array uniform has never resolved in this pack.
        for (int beam = 0; beam < MAX_BEAMS; beam++) {
            final int beamIndex = beam;
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloBeamA" + beamIndex, () -> beamPosition(beamIndex));
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloBeamB" + beamIndex, () -> BEAM_B[beamIndex]);
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloBeamC" + beamIndex, () -> BEAM_C[beamIndex]);
            // The lamp's own cross-section profile index. It has to arrive now: the shaft applies the same
            // curve the surface does (BeamLight.profile), and without it the shaft is drawn to the geometric
            // opening while the lit spot stops at 40% of it -- "the beam is wider than the light".
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloBeamD" + beamIndex, () -> BEAM_D[beamIndex]);
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloBeamE" + beamIndex, () -> BEAM_E[beamIndex]);
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloBeamF" + beamIndex, () -> BEAM_F[beamIndex]);
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloBeamG" + beamIndex, () -> BEAM_G[beamIndex]);
        }
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, "sloBeamVolume",
                () -> ExtraConfig.BEAM_VOLUME.get().floatValue());
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, "sloBeamGlow",
                () -> ExtraConfig.BEAM_GLOW.get().floatValue());
        // How much of the geometric cone the shaft is measured against, 1 = the whole of it. Only matters
        // for a lamp whose profile is a curve; mode 0 is flat and ignores it. See DEVELOPMENT 6.26.1.
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, "sloBeamWidth",
                () -> ExtraConfig.BEAM_WIDTH.get().floatValue());
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, "sloBeamSamples", () -> ExtraConfig.BEAM_SAMPLES.get().floatValue());

        // 1 replaces the shaft with a false colour that says which stage removed a pixel.
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, "sloBeamDebug",
                () -> ExtraConfig.BEAM_DEBUG.get().floatValue());
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME,"slo_flareGlowShrinkDistance",
                () -> ExtraConfig.FLARE_GLOW_SHRINK_DISTANCE.get().floatValue());
        for(int i=0;i<dev.sablelightoverhaul.FlarePayload.MAX_FLARES;i++) {
            final int index=i;
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME,"sloFlareA"+i,()->DebugFlares.position(index));
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME,"sloFlareC"+i,()->DebugFlares.color(index));
        }
        // Refraction and dispersion of the wave front, consumed by the patched shader pack.
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, "sloWaveRefract",
                () -> ExtraConfig.SHOCKWAVE_REFRACT.get().floatValue());
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, "sloWaveDispersion",
                () -> ExtraConfig.SHOCKWAVE_DISPERSION.get().floatValue());
        holder.uniform3f(UniformUpdateFrequency.PER_FRAME, "sloColor", () -> COLOR.set(
                ExtraConfig.GPU_RED.get().floatValue(),
                ExtraConfig.GPU_GREEN.get().floatValue(),
                ExtraConfig.GPU_BLUE.get().floatValue()));
        // FloatSupplier is named explicitly: uniform1f is also overloaded for IntSupplier/DoubleSupplier.
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, "sloIntensity",
                (FloatSupplier) () -> ExtraConfig.GPU_PACK_INTENSITY.get().floatValue());
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, "sloNormalInfluence",
                (FloatSupplier) () -> ExtraConfig.GPU_NORMAL.get().floatValue());
        // The mask travels with the list because its bits are the list's indices. Set by AdvancedLights
        // once a tick, with the frame-rate taper already applied where the slots were handed out.
        holder.uniform1i(UniformUpdateFrequency.PER_FRAME, "sloShadowMask", () -> shadowMask);
        // The tap ceiling is the player's and is read from the config every frame, so retuning it needs
        // no relog. A light may ask for less than this through axis.y and never for more.
        holder.uniform1i(UniformUpdateFrequency.PER_FRAME, "sloShadowSteps",
                () -> ExtraConfig.GPU_SHADOW_STEPS.get());
        // Long-range shadow simulation: 1 keeps drawing shadows past the ray's honest reach (the shipped
        // behaviour), 0 stops each ray at roughly 1.25 blocks per tap instead. Read per frame, so the
        // option takes effect without a relog.
        holder.uniform1i(UniformUpdateFrequency.PER_FRAME, "sloShadowLongRange",
                () -> ExtraConfig.GPU_SHADOW_LONG_RANGE.get() ? 1 : 0);
        // Quality mode: equal steps of a fixed size instead of the tapered march. Read per frame, so the
        // switch and the step size take effect without a relog.
        holder.uniform1i(UniformUpdateFrequency.PER_FRAME, "sloQualitySteps",
                () -> ExtraConfig.GPU_QUALITY_STEPS.get() ? 1 : 0);
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, "sloStepSize",
                (FloatSupplier) () -> ExtraConfig.GPU_STEP_SIZE.get().floatValue());
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, "sloQualityThickness",
                (FloatSupplier) () -> ExtraConfig.GPU_QUALITY_THICKNESS.get().floatValue());
        // How many slots carry a light this frame: the patch bounds its loop by this instead of by the
        // array size, so a frame with three lights costs three iterations. It lives in the frame record
        // rather than in a field of its own so the count and the arrays it bounds are always published
        // together -- a shader that read a fresh count against last frame's arrays would light slots
        // that hold stale data. A count of 0 is the safe direction: the patch then walks every slot.
        holder.uniform1i(UniformUpdateFrequency.PER_FRAME, "sloLightCount", () -> frame.count());
        // Cloud lighting: one bit per slot for "this light may light the clouds" (same indexing as the
        // arrays and the shadow mask) and one vec4 for the two quality switches plus the intensity. Both
        // are published inside the frame record so a fresh mask can never be read against stale reaches.
        holder.uniform1i(UniformUpdateFrequency.PER_FRAME, "sloCloudMask", () -> frame.cloudMask());
        holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloCloudParams", () -> CLOUD.set(
                ExtraConfig.CLOUDS_ENABLED.get() ? 1.0F : 0.0F,
                ExtraConfig.CLOUDS_PHASE.get() ? 1.0F : 0.0F,
                ExtraConfig.CLOUDS_MULTI_SCATTER.get() ? 1.0F : 0.0F,
                ExtraConfig.CLOUDS_INTENSITY.get().floatValue()));
        // How much of the cloud term stays integrated in the cloud march instead of being evaluated per
        // pixel after the pack's temporal upsampling. Read per frame so the slider can be tuned live;
        // the shader reads it in two places (the march and the composite pass) and they have to agree,
        // which is exactly why it is one uniform rather than two keys.
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, "sloCloudSplit",
                (FloatSupplier) () -> (float) Math.clamp(ExtraConfig.CLOUDS_INTEGRATION.get(), 0.0, 1.0));
        // The width of every light's cloud patch, as a multiple of that light's own size (its radius, or
        // a beam's range). Published every frame like the rest, so the slider can be dragged while looking
        // at the cloud and so Iris never has to resolve an unpublished uniform by name.
        holder.uniform1f(UniformUpdateFrequency.PER_FRAME, "sloCloudFalloff",
                (FloatSupplier) () -> (float) Math.clamp(ExtraConfig.CLOUDS_FALLOFF_SCALE.get(), 0.0, 16.0));
        // A lamp's own light style: the faces it emits through and whether it is lit like vanilla block
        // light. One int per slot rather than a vec4 per four slots because the shader names each slot's
        // uniforms anyway, and a slot that reads the wrong word is a face that does not light.
        for (int i = 0; i < MAX_LIGHTS; i++) {
            final int index = i;
            holder.uniform1i(UniformUpdateFrequency.PER_FRAME, "sloStyle" + index,
                    // Slots past the end of the list hold no light (their brightness is 0), so any style
                    // will do; "every face, shaped" is the one that changes nothing if that ever stops
                    // being true. The list is shorter than 32 whenever fewer lights are in the frame.
                    () -> index < frame.style().length ? frame.style()[index] : STYLE_FACES);
        }
        for (int i = 0; i < MAX_BODIES; i++) {
            final int index = i;
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloBody" + index, () -> {
                Frame f = frame;
                float[] q = f.bodies();
                int b = index * STRIDE;
                return BODY[index].set(q[b], q[b + 1], q[b + 2], q[b + 3]);
            });
        }
        for (int i = 0; i < MAX_LIGHTS; i++) {
            final int index = i;
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloCone" + index, () -> {
                Frame f = frame;
                int b = index * STRIDE;
                return CONE[index].set(f.cones()[b], f.cones()[b + 1], f.cones()[b + 2], f.cones()[b + 3]);
            });
        }
        for (int i = 0; i < MAX_LIGHTS; i++) {
            final int index = i;
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloLight" + index, () -> {
                Frame f = frame;
                int b = index * STRIDE;
                // The camera offset is applied here, every frame, so the light and the pack's scene_pos are
                // always expressed against the same camera pose. See the type comment for what baking it
                // into the array cost.
                double[] camera = cameraOffset();
                float x = f.position[b], y = f.position[b + 1], z = f.position[b + 2];
                // A lamp on a hull is re-placed between the two ticks that bracket this frame. Both ends are
                // poses this mod already trusted for everything else, so the answer can only ever be somewhere
                // the hull actually is: the earlier attempt at this asked Sable for a render pose and used it
                // in the wrong space, which put ship lamps' samples outside their own field and turned their
                // light off completely. Nothing here can move a lamp further than one tick of travel.
                // Position interpolation applies to every ship light; only shape rotation is flood-only.
                float[][] live = ExtraLights.livePose(keyAt(f, index), partialTick());
                if (live != null) { x = live[0][0]; y = live[0][1]; z = live[0][2]; }
                float brightness=f.position[b+3];
                if(HealthbarFlightBridge.mathKey(keyAt(f,index))){
                    var math=HealthbarFlightBridge.position(keyAt(f,index),(float)partialTick());
                    if(math==null)brightness=0;else{x=(float)math.x;y=(float)math.y;z=(float)math.z;}
                }
                LIGHT[index].set((float) (x - camera[0]), (float) (y - camera[1]), (float) (z - camera[2]), brightness);
                DEBUG_LIGHT[b] = LIGHT[index].x; DEBUG_LIGHT[b + 1] = LIGHT[index].y;
                DEBUG_LIGHT[b + 2] = LIGHT[index].z; DEBUG_LIGHT[b + 3] = LIGHT[index].w;
                DEBUG_DECAY[index] = f.decay()[index];
                DEBUG_STYLE[index] = f.style().length > index ? f.style()[index] : 0;
                return LIGHT[index];
            });
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloShape" + index, () -> {
                Frame f = frame;
                int b = index * STRIDE;
                // The override is for a lamp whose field is read in a hull's frame, and for nothing else: a
                // ray lamp's shape vector is its falloff profile (core, middle, tail), so writing a rotation
                // into it turns a smooth curve into a nonsense one -- and a stale entry from a lamp that was
                // on the field model a moment ago does exactly that. The style check is the belt to the
                // map's braces.
                float[][] live = (f.style().length > index && (f.style()[index] & STYLE_FLOOD) != 0)
                        ? ExtraLights.livePose(keyAt(f, index), partialTick()) : null;
                if (live != null) {
                    // The field is read in the hull's frame, so its rotation has to be the one this frame is
                    // drawn with too -- otherwise a turning hull drags its own lit patch around a tick late.
                    SHAPE[index].set(f.shape[b], live[1][0], live[1][1], live[1][2]);
                    DEBUG_SHAPE[b] = SHAPE[index].x; DEBUG_SHAPE[b + 1] = SHAPE[index].y;
                    DEBUG_SHAPE[b + 2] = SHAPE[index].z; DEBUG_SHAPE[b + 3] = SHAPE[index].w;
                    return SHAPE[index];
                }
                SHAPE[index].set(f.shape[b], f.shape[b + 1], f.shape[b + 2], f.shape[b + 3]);
                DEBUG_SHAPE[b] = SHAPE[index].x; DEBUG_SHAPE[b + 1] = SHAPE[index].y;
                DEBUG_SHAPE[b + 2] = SHAPE[index].z; DEBUG_SHAPE[b + 3] = SHAPE[index].w;
                return SHAPE[index];
            });
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloAxis" + index, () -> {
                Frame f = frame;
                int b = index * STRIDE;
                AXIS[index].set(f.axis[b], f.axis[b + 1], f.axis[b + 2], f.axis[b + 3]);
                DEBUG_AXIS[b] = AXIS[index].x; DEBUG_AXIS[b + 1] = AXIS[index].y;
                DEBUG_AXIS[b + 2] = AXIS[index].z; DEBUG_AXIS[b + 3] = AXIS[index].w;
                return AXIS[index];
            });
            final Vector4f sourceGlow=new Vector4f();
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME,"sloSourceGlow"+index,()->{
                Frame f=frame;
                var a=ExtraLights.appearance(keyAt(f,index));
                if(index>=f.count()||!a.glow()||(!a.ring()&&!a.axis()))return sourceGlow.zero();
                return sourceGlow.set(a.ring()?(float)a.ringSize():0,a.axis()?(float)a.axisLength():0,(float)a.axisWidth(),1);
            });
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloTint" + index, () -> {
                Frame f = frame;
                int b = index * STRIDE;
                return TINT[index].set(f.color[b], f.color[b + 1], f.color[b + 2], f.color[b + 3]);
            });
        }
        for (int group = 0; group < DECAY.length; group++) {
            final int slot = group;
            final int base = group * 4;
            holder.uniform4f(UniformUpdateFrequency.PER_FRAME, "sloDecay" + group, () -> {
                Frame f = frame;
                return DECAY[slot].set(f.decay[base], f.decay[base + 1], f.decay[base + 2], f.decay[base + 3]);
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
    static void update(List<AreaSnapshot> lights,int cloudMask,float[] cloudReach,int mask,double[] softness,int[] steps,int[] style,float[] bodies,java.util.Map<AreaSnapshot,String> lightKeys) {
        shadowMask=mask;
        float[] position = new float[MAX_LIGHTS * STRIDE];
        float[] shape = new float[MAX_LIGHTS * STRIDE];
        float[] axis = new float[MAX_LIGHTS * STRIDE];
        float[] color = new float[MAX_LIGHTS * STRIDE];
        // One knee per light: nonzero only for a beam that fades along its length.
        float[] decay = new float[MAX_LIGHTS];
        // Four packed cone words per light, carried as float bits (see CONE).
        float[] cones = new float[MAX_LIGHTS * STRIDE];
        // Flashing tints step every SourceColor.FLASH_TICKS ticks, so the phase is shared by every
        // light in one frame; a colour can never change between the lights of one draw.
        long tick = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
        int phase = (int) (tick / SourceColor.FLASH_TICKS);
        float[] rgb = new float[3];
        float defaultRed = ExtraConfig.GPU_RED.get().floatValue();
        float defaultGreen = ExtraConfig.GPU_GREEN.get().floatValue();
        float defaultBlue = ExtraConfig.GPU_BLUE.get().floatValue();
        int count = Math.min(lights.size(), MAX_LIGHTS);
        currentCount = count;
        for (int i = 0; i < count; i++) {
            AreaSnapshot snapshot = lights.get(i);
            AreaLight light = snapshot.light;
            int b = i * STRIDE;
            // WORLD space. The camera offset is applied per frame in the uniform supplier, against the camera
            // pose of the frame being drawn -- baking it here froze it at the pose this update happened to see,
            // and every frame until the next update then drew the light displaced by however far the camera had
            // travelled since, against the direction of travel. On a lamp standing on a floor that displacement
            // sinks it through the floor, the floor's terminator puts the light behind it, and the patch of
            // floor goes black -- worse the faster the player moves, and untouched by any shader change.
            position[b] = (float) light.x();
            position[b + 1] = (float) light.y();
            position[b + 2] = (float) light.z();
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
                // axis.z is the one component of a sphere's axis vector nobody else uses (axis.w == 0 is
                // what marks a light as a sphere and must stay zero), so the cloud reach rides here. The
                // default of 0 is exactly "this light does not light the clouds".
                axis[b + 2] = i < cloudReach.length ? cloudReach[i] : 0.0F;
            }
            shape[b + 1] = (float) light.core();
            shape[b + 2] = (float) light.middle();
            shape[b + 3] = (float) light.tail();
            // A lamp whose field was flooded in a body's plot carries that body's rotation here, in the three
            // shape components a field lamp never reads (core/middle/tail belong to the ray model, and a field
            // lamp returns before it). The fourth component is recovered in the shader from the other three,
            // which works because a unit quaternion can always be written with w >= 0.
            if (snapshot.bodyQuat != null) {
                float[] q = snapshot.bodyQuat;
                shape[b + 1] = q[0];
                shape[b + 2] = q[1];
                shape[b + 3] = q[2];
            }
            // A lamp somebody configured takes its colour from the palette, so it can be left on 原版 and match
            // vanilla block light. A muzzle flash, a tracer, a shot or a plume has no page and keeps the
            // client's configured overlay colour -- see AdvancedLights.isPaged for why the two cannot share the
            // palette's first slot any more.
            String colourKey = lightKeys == null ? null : lightKeys.get(snapshot);
            if (AdvancedLights.isPaged(colourKey)) {
                SourceColor.rgb(snapshot.color, phase, rgb, defaultRed, defaultGreen, defaultBlue);
                color[b] = rgb[0];
                color[b + 1] = rgb[1];
                color[b + 2] = rgb[2];
            } else {
                color[b] = defaultRed;
                color[b + 1] = defaultGreen;
                color[b + 2] = defaultBlue;
            }
            color[b]*=snapshot.radiance;
            color[b+1]*=snapshot.radiance;
            color[b+2]*=snapshot.radiance;
            // tint.w is the emissive ratio when this lamp stands in for an emissive block, and the self-cull flag
            // when it does not. Both say "treat this lamp's own block specially", which is why one slot can carry
            // either: the shader tests it once. The ratio used to be the field model's alone, so switching a
            // lamp's page from 光场 to 射线光 silently turned its own block from a glowing block into a black one.
            boolean onFieldModel = (style[i] & STYLE_FLOOD) != 0;
            // The block whose emission tier this lamp stands in for. On a body the lamp is not at its world
            // position -- its block is in the body's plot -- so the plot block is the one to ask, or a hull
            // lamp standing in for a sea lantern would be drawn at the default tier instead of its own.
            var lampBlock = ExtraLights.sourceBlock(colourKey);
            var emissionBlock = lampBlock != null ? lampBlock
                    : BlockPos.containing(light.x(), light.y(), light.z());
            float ratio = AdvancedLights.isPaged(colourKey) && !ExtraLights.appearance(colourKey).shifted()
                    ? emissionRatio(Minecraft.getInstance().level, emissionBlock) : 0.0F;
            if (ratio > 0.0F) style[i] |= STYLE_EMISSION;
            color[b + 3] = ratio > 0.0F ? ratio
                    : (snapshot.selfCull && ExtraConfig.GPU_SELF_CULL.get() ? 1.0F : 0.0F);
            // sloDecayN.w is the beam's length knee for a beam and unused for everything else, so it carries the
            // lamp's field layer + 1 instead. This is the whole field handshake: zero means the pack draws the
            // ray model, non-zero means the lamp is on the vanilla light model and the field is in that layer.
            // See the note above register() for why this travels here rather than in a uniform of its own.
            String fieldKey = colourKey;
            decay[i] = onFieldModel ? LightFields.layerFor(fieldKey) : (float) (light.signalAngle()>0 ? -light.signalAngle() : light.distanceKnee());
            // Recorded for the debug line, so a lamp that does not light can be told apart from a lamp the pack
            // was never told about. The anchor is what the shader will derive: the lamp's world block.
            //
            // Floor, not round. A cell's centre is at x + 0.5, so rounding printed cell 32 as "33" -- one more
            // than the cell the field was actually built around (LightFields.slot takes BlockPos.containing,
            // which floors). That made the gate line and the fields line look like they disagreed by one block,
            // which is precisely the symptom of a field whose origin has gone stale, and it was chased once
            // before the arithmetic was checked. A debug number that can only be wrong in a way that looks like
            // a real bug is worse than no number.
            FIELD_DEBUG_GATE[i * 2] = decay[i];
            FIELD_DEBUG_GATE[i * 2 + 1] = onFieldModel ? 1.0F : 0.0F;
            FIELD_DEBUG_QUAT[i * 3] = snapshot.bodyQuat == null ? 0.0F : snapshot.bodyQuat[0];
            FIELD_DEBUG_QUAT[i * 3 + 1] = snapshot.bodyQuat == null ? 0.0F : snapshot.bodyQuat[1];
            FIELD_DEBUG_QUAT[i * 3 + 2] = snapshot.bodyQuat == null ? 0.0F : snapshot.bodyQuat[2];
            FIELD_DEBUG_ANCHOR[i * 3] = (float) Math.floor(light.x());
            FIELD_DEBUG_ANCHOR[i * 3 + 1] = (float) Math.floor(light.y());
            FIELD_DEBUG_ANCHOR[i * 3 + 2] = (float) Math.floor(light.z());
            int c = i * STRIDE;
            cones[c] = Float.intBitsToFloat(snapshot.coneA);
            cones[c + 1] = Float.intBitsToFloat(snapshot.coneB);
            cones[c + 2] = Float.intBitsToFloat(snapshot.coneC);
            cones[c + 3] = Float.intBitsToFloat(snapshot.coneD);
        }
        // One key per slot, so the uniform suppliers can find a ship lamp's motion over the tick that just
        // ended and place it where the hull is drawn rather than where it was at the boundary.
        String[] keys = new String[MAX_LIGHTS];
        if (lightKeys != null) {
            for (int i = 0; i < count; i++) keys[i] = lightKeys.get(lights.get(i));
        }
        // The body tables come from the caller: they are rebuilt once per frame from the hulls that carry
        // cone-bearing lamps or lamps on the vanilla light model. The quaternions turn a direction (a cone)
        // and the positions turn a point (a field's sample), and both are shared by every lamp on the hull.
        frame = new Frame(position, shape, axis, color, decay, count, cloudMask, cloudReach, style, cones,
                bodies != null ? bodies : frame.bodies(), keys);
    }
}
