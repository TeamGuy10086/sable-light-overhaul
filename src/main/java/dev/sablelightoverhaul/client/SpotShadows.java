package dev.sablelightoverhaul.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * The spot shadow map: where each beam's light is stopped, cast from the lamp, fetched once per pixel.
 *
 * <p><b>Why a map and not a test.</b> A pixel of a beam is not asking "can the camera see this point" -- the
 * depth buffer already answers that, and it is what clips the volume to the scene. The question is "can the
 * <em>lamp</em> see this point", and only geometry known in the lamp's own frame can answer it. Every attempt to
 * answer it from the camera's depth buffer failed for the same reason (see {@code docs/DEVELOPMENT.zh-CN.md}
 * 6.24.1): the wall between a lamp and a patch of air is not necessarily anywhere near that patch on screen.
 *
 * <p><b>What is cast.</b> A fan of {@link #RAYS}&times;{@link #RAYS} rays inside the lamp's own cone, in the
 * cone's tangent plane so that the cells are uniform in angle: cell (u, v) of a map holds the distance to the
 * first solid block along that direction, or zero when the ray leaves the map's range without hitting anything.
 * Two bytes per cell, one row block of {@link #RAYS} rows per beam, eight beams deep: a 32&times;256 {@code R16}
 * image, sixteen kilobytes.
 *
 * <p><b>Why the CPU.</b> Iris offers a composite pass no way to render geometry, and the pack has no free render
 * target to hold a lamp-space depth image (6.24.1). A mod-owned texture, bound as a dynamic sampler, is the one
 * channel that exists -- the light field already travels that way. So the map is cast here, on this thread, and
 * the shader only reads it.
 *
 * <p><b>Cost.</b> One cast is {@code 1024} rays of a voxel walk, and it happens only when the lamp has moved (by
 * {@link #MOVE_BLOCKS}), turned (by {@link #TURN}), or had a block changed within its range -- at most
 * {@code beam.shadowBudget} casts per refresh, so a lamp that is moving every frame still gets a fresh map every
 * frame or two and a lamp standing still costs nothing at all. A beam whose map is not ready stays absent until its own map is ready;
 * it must never use another lamp's map or a camera-dependent whole-beam gate.
 */
public final class SpotShadows {
    /**
     * Rays per side of one beam's map. Keep in sync with the patched composite's constant.
     *
     * <p>At 32 the angular sampling is finer than the former 24-wide map. Thin occluders can still fall
     * between rays; filtering improves continuity, not missing geometry. Rebuild cost is bounded by shadowBudget.
     */
    static final int RAYS = 32;
    /** One row block per beam slot. */
    static final int SLOTS = IrisMuzzleBridge.MAX_BEAMS;
    static final int WIDTH = RAYS;
    static final int HEIGHT = RAYS * SLOTS;

    /**
     * A map cell is {@code 1 + round(dist / range * 65534)}, so 1 is "the lamp touches this surface" and 65535 is
     * "the hit is at the far edge of the range". Zero is the only value that means "nothing was hit", which is
     * why a freshly created texture is all zeros: a missing map must read as "no occluder", never as "the beam
     * is blocked at zero distance".
     */

    /** Rebuild thresholds. A map that saw nothing is rebuilt far more reluctantly: it is the sky, not a wall. */
    private static final double MOVE_BLOCKS = 0.75;
    private static final double TURN = 0.0005;
    private static final double MOVE_BLOCKS_EMPTY = 4.0;
    private static final double TURN_EMPTY = 0.005;
    private static final long EMPTY_TICKS = 4;

    /** One beam's map, its pose when it was cast, and the key of the lamp that owns it. */
    private static final class Slot {
        final short[] map = new short[RAYS * RAYS];
        int key;
        boolean valid;
        boolean empty;
        boolean dirty = true;
        double ox, oy, oz;
        double ax, ay, az;
        double range, cosHalf, aperture;
        long builtTick;
        int casts;
    }

    private static final Slot[] SLOTS_ARRAY = new Slot[SLOTS];
    private static int texture;
    private static boolean textureFailed;
    private static int budget;
    private static long tick;
    /** Casts and uploads since the client started, for the per-frame diagnostic line. */
    private static long casts, uploads;

    static {
        for (int i = 0; i < SLOTS; i++) SLOTS_ARRAY[i] = new Slot();
    }

    private SpotShadows() {}

    /** The GL texture the shader samples, or 0 when there is nothing (or no context) to give. */
    public static int texture() {
        createTexture();
        return texture;
    }

    /** Create the texture if it does not exist yet. Called while the pipeline is being built, before any map. */
    public static void ensureTexture() {
        createTexture();
    }

    private static void createTexture() {
        if (texture != 0 || textureFailed) return;
        try {
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL30.GL_R16, WIDTH, HEIGHT, 0,
                    GL11.GL_RED, GL11.GL_UNSIGNED_SHORT, (java.nio.ByteBuffer) null);
            // The image is created empty on purpose, then cleared through the same path an upload takes, so that
            // "nothing has been cast yet" and "nothing was hit" are the same bytes.
            short[] zeros = new short[WIDTH * HEIGHT];
            uploadAll(zeros);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            org.slf4j.Logger log = com.mojang.logging.LogUtils.getLogger();
            log.info("Sable Light Overhaul: spot shadow map {}x{} (gl {}), {} beam slots of {}x{}",
                    WIDTH, HEIGHT, texture, SLOTS, RAYS, RAYS);
        } catch (RuntimeException | LinkageError ex) {
            textureFailed = true;
            com.mojang.logging.LogUtils.getLogger().error(
                    "Sable Light Overhaul: the spot shadow map could not be created", ex);
        }
    }

    /**
     * Allocated once: LWJGL wants a direct buffer, not an array. It has to hold the largest upload this class
     * makes, and the largest is the whole cleared image, not one beam's block -- sizing it for one block threw a
     * buffer overflow from inside the texture's own constructor, which the catch below would have turned into
     * "no shadow map at all".
     */
    private static final java.nio.ShortBuffer PIXELS = java.nio.ByteBuffer.allocateDirect(WIDTH * HEIGHT * 2).order(java.nio.ByteOrder.nativeOrder()).asShortBuffer();

    private static void uploadAll(short[] data) {
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        PIXELS.clear();
        PIXELS.put(data);
        PIXELS.flip();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, WIDTH, HEIGHT,
                GL11.GL_RED, GL11.GL_UNSIGNED_SHORT, PIXELS);
    }

    private static void upload(int slot, short[] data) {
        createTexture();
        if (texture == 0) return;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        PIXELS.clear();
        PIXELS.put(data);
        PIXELS.flip();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, slot * RAYS, WIDTH, RAYS,
                GL11.GL_RED, GL11.GL_UNSIGNED_SHORT, PIXELS);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        uploads++;
    }

    /** Called once per beam refresh (the client rebuilds its light table once a tick), before the beams are walked. */
    public static void beginFrame() {
        budget = Math.max(1, ExtraConfig.BEAM_SHADOW_BUDGET.get());
        tick++;
    }

    /**
     * A block changed somewhere. Only the lamps whose map range reaches it have to be recast; every other beam
     * keeps the map it has, which is the whole point of asking about the position.
     */
    public static void blockChanged(BlockPos pos) {
        for (int i = 0; i < SLOTS; i++) {
            Slot s = SLOTS_ARRAY[i];
            if (!s.valid || s.range < 0.5) continue;
            double dx = pos.getX() + 0.5 - s.ox;
            double dy = pos.getY() + 0.5 - s.oy;
            double dz = pos.getZ() + 0.5 - s.oz;
            double r = s.range + 2.0;
            if (dx * dx + dy * dy + dz * dz <= r * r) s.dirty = true;
        }
    }

    /**
     * Brings one beam's map up to date and tells the shader where to read it.
     *
     * @param beam  the beam slot, which is also the map's row block
     * @param key   the lamp's identity, so a slot reused by another lamp is never read with the old map
     * @return the map's range in blocks when the shader may read this slot, or 0 when it must not
     */
    public static double attach(int beam, int key, ClientLevel level, Vec3 origin, Vec3 axis,
                                double cosHalf, double range, int volumeMode, double aperture) {
        if (beam < 0 || beam >= SLOTS) return 0.0;
        if (volumeMode < 2 || ClientFlashes.sloOnly()) return 0.0;
        Slot s = SLOTS_ARRAY[beam];
        if (s.key != key) {
            s.key = key;
            s.valid = false;
            s.dirty = true;
        }
        double mapRange = Math.min(Math.max(2.0, range), ExtraConfig.BEAM_SHADOW_RANGE.get());
        // The basis the shader rebuilds the angular coordinates from. It must be the same one the fan was cast
        // with, which is why it is published rather than derived twice.
        Vec3 right = basisRight(axis);
        Vec3 up = right.cross(axis).normalize();

        double moved2 = s.valid
                ? sq(origin.x - s.ox) + sq(origin.y - s.oy) + sq(origin.z - s.oz) : Double.MAX_VALUE;
        double turned = s.valid ? 1.0 - (axis.x * s.ax + axis.y * s.ay + axis.z * s.az) : 1.0;
        double moveLimit = s.empty ? MOVE_BLOCKS_EMPTY : MOVE_BLOCKS;
        double turnLimit = s.empty ? TURN_EMPTY : TURN;
        boolean stale = moved2 > moveLimit * moveLimit || turned > turnLimit
                || Math.abs(mapRange - s.range) > 0.5 || Math.abs(cosHalf - s.cosHalf) > 1e-6 || Math.abs(aperture-s.aperture)>1e-6;
        if (s.empty && s.valid) stale = stale || tick - s.builtTick > EMPTY_TICKS;
        if (stale) s.dirty = true;

        if (s.dirty && budget > 0) {
            budget--;
            cast(level, beam, s, origin, axis, right, up, cosHalf, mapRange, aperture);
            s.dirty = false;
            s.valid = true;
        }
        if (!s.valid) return 0.0;
        // Cached distances must be queried in the pose that produced them, not this frame's lamp pose.
        Vec3 mapAxis = new Vec3(s.ax,s.ay,s.az);
        Vec3 mapRight = basisRight(mapAxis), mapUp = mapRight.cross(mapAxis).normalize();
        double mapTan = Math.sqrt(Math.max(1-s.cosHalf*s.cosHalf,1e-6))/Math.max(s.cosHalf,1e-3);
        IrisMuzzleBridge.beamShadow(beam,mapRight.x,mapRight.y,mapRight.z,mapUp.x,mapUp.y,mapUp.z,s.range,
            origin.x-s.ox,origin.y-s.oy,origin.z-s.oz,mapTan,s.aperture);
        return s.range;
    }

    private static double sq(double v) {
        return v * v;
    }

    /** A unit vector across the beam, with the pole degeneracy moved to +Y where no beam ever points. */
    private static Vec3 basisRight(Vec3 axis) {
        Vec3 other = Math.abs(axis.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 right = axis.cross(other);
        return right.lengthSqr() < 1.0e-9 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
    }

    /**
     * Cast one fan and upload it.
     *
     * <p>The fan is built in the cone's tangent plane -- {@code dir = axis + right * u * tan + up * v * tan} with
     * {@code u, v} in {@code [-1, 1]} -- so the shader recovers the same coordinates from a point with two dots
     * and no trigonometry. The tangent is taken from the <em>unshrunk</em> half angle: the map has to be at least
     * as wide as the volume that reads it, and that volume deliberately draws a cone eight percent narrower than
     * the lit one.
     */
    private static void cast(ClientLevel level, int beam, Slot s, Vec3 origin, Vec3 axis, Vec3 right, Vec3 up,
                             double cosHalf, double mapRange, double aperture) {
        double tan = Math.sqrt(Math.max(1.0 - cosHalf * cosHalf, 1.0e-6)) / Math.max(cosHalf, 1.0e-3);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int hits = 0;
        for (int j = 0; j < RAYS; j++) {
            double v = ((j + 0.5) / RAYS * 2.0 - 1.0) * tan;
            for (int i = 0; i < RAYS; i++) {
                double u = ((i + 0.5) / RAYS * 2.0 - 1.0) * tan;
                double dx = axis.x + right.x * u + up.x * v;
                double dy = axis.y + right.y * u + up.y * v;
                double dz = axis.z + right.z * u + up.z * v;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1.0e-6) {
                    s.map[j * RAYS + i] = 0;
                    continue;
                }
                // Each ray starts on its own point of the lens plane, then diverges.
                // The shader projects using radius = aperture + axial*tan, exactly the same fan.
                double sx=(right.x*u+up.x*v)*aperture/tan;
                double sy=(right.y*u+up.y*v)*aperture/tan;
                double sz=(right.z*u+up.z*v)*aperture/tan;
                double alongStart=(sx*dx+sy*dy+sz*dz)/len;
                double travelLimit=Math.max(0,-alongStart+Math.sqrt(Math.max(0,
                    alongStart*alongStart+mapRange*mapRange-sx*sx-sy*sy-sz*sz)));
                double dist = walk(level, cursor, origin.x+sx, origin.y+sy, origin.z+sz,
                        dx / len, dy / len, dz / len, travelLimit);
                if (dist <= 0.0) {
                    s.map[j * RAYS + i] = 0;
                } else {
                    hits++;
                    dist=Math.sqrt(sq(sx+dx/len*dist)+sq(sy+dy/len*dist)+sq(sz+dz/len*dist));
                    s.map[j * RAYS + i] = (short) dev.sablelightoverhaul.SpotShadowDepth.encode(dist,mapRange);
                }
            }
        }
        s.empty = hits == 0;
        s.ox = origin.x;
        s.oy = origin.y;
        s.oz = origin.z;
        s.ax = axis.x;
        s.ay = axis.y;
        s.az = axis.z;
        s.range = mapRange;
        s.cosHalf = cosHalf;
        s.aperture = aperture;
        s.builtTick = tick;
        s.casts++;
        casts++;
        upload(beam, s.map);
    }

    /** Casts and uploads since the client started, printed by the frame diagnostic so a map that never rebuilds is visible. */

    /**
     * Walk one ray through the block grid and return the distance to the first block that stops light, or zero.
     *
     * <p>A block is solid for this purpose when it occludes: a full cube. Glass, water, leaves that are not
     * solid and every non-full shape let the beam through, which is what a spotlight in a cockpit window needs.
     * Hits closer than {@link #NEAR_SKIP} are ignored -- that is the lamp's own housing lip, not a wall -- and
     * so is the block the lamp stands in.
     */
    private static double walk(ClientLevel level, BlockPos.MutableBlockPos cursor,
                               double ox, double oy, double oz,
                               double dx, double dy, double dz, double range) {
        int x = Mth.floor(ox), y = Mth.floor(oy), z = Mth.floor(oz);
        int stepX = dx >= 0.0 ? 1 : -1;
        int stepY = dy >= 0.0 ? 1 : -1;
        int stepZ = dz >= 0.0 ? 1 : -1;
        double invX = Math.abs(dx) < 1.0e-9 ? Double.MAX_VALUE : 1.0 / Math.abs(dx);
        double invY = Math.abs(dy) < 1.0e-9 ? Double.MAX_VALUE : 1.0 / Math.abs(dy);
        double invZ = Math.abs(dz) < 1.0e-9 ? Double.MAX_VALUE : 1.0 / Math.abs(dz);
        double maxX = dx >= 0.0 ? (x + 1 - ox) * invX : (ox - x) * invX;
        double maxY = dy >= 0.0 ? (y + 1 - oy) * invY : (oy - y) * invY;
        double maxZ = dz >= 0.0 ? (z + 1 - oz) * invZ : (oz - z) * invZ;
        double t = 0.0;
        // A ray longer than the map's range has nothing to say about occlusion inside it.
        int guard = 8 + 3 * (int) Math.ceil(range);
        while (guard-- > 0) {
            if (maxX < maxY && maxX < maxZ) {
                x += stepX;
                t = maxX;
                maxX += invX;
            } else if (maxY < maxZ) {
                y += stepY;
                t = maxY;
                maxY += invY;
            } else {
                z += stepZ;
                t = maxZ;
                maxZ += invZ;
            }
            if (t > range) return 0.0;
            if (t < NEAR_SKIP) continue;
            BlockState state = level.getBlockState(cursor.set(x, y, z));
            if (!state.isAir() && state.canOcclude()) return t;
        }
        return 0.0;
    }

    /** The lamp's own block and the lip of its housing are not occluders. */
    private static final double NEAR_SKIP = 0.55;

    /** Cast and upload counts, printed by the frame diagnostic so a map that never rebuilds is visible. */
    public static String stats() {
        return casts + " casts, " + uploads + " uploads";
    }
}
