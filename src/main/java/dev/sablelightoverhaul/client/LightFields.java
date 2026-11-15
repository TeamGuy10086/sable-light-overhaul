package dev.sablelightoverhaul.client;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * The vanilla light field of one lamp, computed on this client and handed to the shader as a 3D texture.
 *
 * <p><b>What this is for.</b> A ray from a surface to a lamp is the wrong model for this game. Minecraft's
 * light is a field that spreads cell by cell through air and transparent blocks, is stopped by anything
 * opaque, wraps around corners and fades by exactly one level per cell -- which is why a vanilla-lit room
 * reads the way players expect and a ray-lit one never does. The alternative, the flat "flood" look, threw
 * the occlusion away entirely. So a lamp whose page asks for the vanilla model gets its own small light
 * field instead: a breadth-first flood over the cells around it, exactly vanilla's rule.
 *
 * <p><b>Why it is cheap.</b> Vanilla light levels run 0..15, so a level 15 lamp cannot reach further than 15
 * blocks, and the volume is a fixed 32<sup>3</sup> box (one guard cell on each side) whatever the lamp is.
 * One byte per cell, sixteen lamps per texture, is half a megabyte. The flood only ever visits the cells it
 * lights, and the expensive part -- asking the level what block is there -- happens once per cell when the
 * field is built, never inside the flood itself.
 *
 * <p><b>Why it can see a ship.</b> Sable makes the level's own block getter answer for the cells a sub-level
 * occupies, so an ordinary {@code level.getBlockState} at a world position already returns the hull's block.
 * That is what lets the whole thing run in world space: no per-body lighting, no local frames, and two
 * interpenetrating bodies occlude each other because they are simply blocks in the same flood.
 *
 * <p><b>What it deliberately ignores.</b> Shadow cones and emit faces are ideas that only a ray has. A field
 * has no cone and no face: it spreads everywhere it can, which is what "like vanilla block light" means. The
 * page greys those rows out for a lamp on this model, and this class never reads them.
 *
 * <p><b>What it is not.</b> Not a ray, not a shadow map, and not per-frame: a field is rebuilt only when the
 * lamp or the blocks around it change, at most one lamp per tick, and uploaded only when it changed.
 */
public final class LightFields {
    /** One guard cell on each side, so the trilinear fetch never leaves its own slab. */
    static final int SIZE = 32;
    /** Vanilla's ceiling: a level 15 lamp reaches 15 cells, so the box is 31 wide and 32 covers it. */
    static final int MAX_LEVEL = 15;
    /** Layers in the shared texture. Each layer is one lamp's 31-block box. */
    static final int MAX_FIELDS = 16;

    private static final int CELLS = SIZE * SIZE * SIZE;

    /** One lamp's field: the levels, the block cost cache it was flooded over, and where it belongs. */
    private static final class Field {
        /**
         * The lattice the field lives on, which is also the lattice the shader samples: cell (15,15,15)
         * sits on the lamp's own block. On the ground that is a world block; on a physics body it is the
         * lamp's block <em>in the body's plot</em>, because that is where the hull's blocks are.
         */
        BlockPos origin;
        final byte[] levels = new byte[CELLS];
        final byte[] cost = new byte[CELLS];
        final int[] queue = new int[CELLS];
        int level;
        int layer = -1;
        long builtTick = Long.MIN_VALUE;
        /** The flood has to be recomputed: the lamp, its brightness or a block inside its box changed. */
        boolean dirty = true;
        /** False when the flood could not leave the lamp's own cell, which is a lamp that would look unlit. */
        boolean useful = false;
        /**
         * The columns the body's plot owns, or null on the ground.
         *
         * <p>The lattice is the plot, and a plot is a pocket cut out of the same level. Outside these
         * columns the level's own ground sits at those coordinates -- rock that has nothing to do with this
         * ship -- so the flood treats it as air. Without that the light stopped dead at the hull's edge.
         */
        dev.sablelightoverhaul.lighting.Pocket plot;
        Field(BlockPos lamp, dev.sablelightoverhaul.lighting.Pocket plot) {
            // The box is centred on the lamp: cell (15,15,15) is the lamp's own cell.
            this.origin = lamp.offset(-MAX_LEVEL, -MAX_LEVEL, -MAX_LEVEL);
            this.plot = plot;
        }
    }

    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static int texture = 0;
    private static boolean textureFailed = false;
    private static long tick = 0;

    /**
     * The invalidation chain, one counter per link: a block change was seen, a chunk arrived, a field was
     * marked, a field was rebuilt. Every one of them is a place this chain has silently stopped before, and a
     * zero names the link instead of leaving "the light did not move" to be guessed at from the picture. They
     * are printed on the per-frame field line, which already runs, so they cost nothing and cannot be switched
     * off.
     */
    private static long blockChanges;
    private static long chunkLoads;
    private static long dirtyMarks;
    private static long rebuilds;
    /** Brightness changes seen. Its own counter because it is its own way for a field to go stale. */
    private static long levelChanges;

    private LightFields() {}

    /** The GL texture the shader samples, or 0 when there is nothing (or no context) to give. */
    public static int texture() {
        // DIAGNOSTIC: while non-zero this hands the sampler a 1x1 white texture instead of the field. It separates
        // two faults that have produced the same black screen for several rounds -- "Iris never binds what this
        // supplier returns" and "this particular texture is the problem". A 1x1 white texture is the smallest
        // thing that can possibly work: if the shader still reads zero through it, the sampler is not delivering at
        // all and no amount of work on the field texture can help.
        if (DIAG_WHITE) return whiteTexture();
        return texture;
    }

    private static final boolean DIAG_WHITE = false;
    private static int white = -1;

    private static int whiteTexture() {
        if (white > 0) return white;
        try {
            java.nio.ByteBuffer pixel = java.nio.ByteBuffer.allocateDirect(4);
            pixel.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) 255).flip();
            white = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, white);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        } catch (RuntimeException | LinkageError ex) {
            white = -1;
        }
        return white;
    }

    /**
     * The origin and layer of the field a slot should use, as (x, y, z, layer + 1); all zero for none.
     *
     * @param lamp the lamp's block <em>in the lattice the field lives on</em>: the world block on the ground,
     *             and the block in its body's plot for a lamp on a physics body. That is the one coordinate
     *             the shader will derive from the lamp's own position after it has moved the sample into the
     *             body's frame, so it has to be the same lattice the flood ran on.
     * @param plot the block columns the body's plot owns, or null on the ground: everything outside them is
     *             treated as air, because the level's own ground out there is not this ship's world
     */
    static void slot(String key, ClientLevel level, BlockPos lamp, int lightLevel,
                     dev.sablelightoverhaul.lighting.Pocket plot, float[] out, int slot) {
        int base = slot * 4;
        if (key == null || level == null || lamp == null) {
            out[base] = out[base + 1] = out[base + 2] = out[base + 3] = 0.0F;
            return;
        }
        Field field = FIELDS.get(key);
        if (field == null) {
            field = new Field(lamp.immutable(), plot);
            FIELDS.put(key, field);
        } else {
            // The lamp's own block moved: on the ground that is the lamp being placed somewhere else, and in
            // a plot it is the lamp being moved on the hull. Either way the slab has to be re-flooded around
            // the new cell, because the shader derives the anchor from the lamp's position and a stale lattice
            // is read one cell out in every direction.
            BlockPos origin = lamp.offset(-MAX_LEVEL, -MAX_LEVEL, -MAX_LEVEL);
            if (!field.origin.equals(origin)) {
                field.origin = origin;
                field.dirty = true;
            }
            // The pocket moved (a plot can grow), so the air rule changed with it.
            if (plot != null && !plot.equals(field.plot)) {
                field.plot = plot;
                field.dirty = true;
            }
        }
        // The lamp's brightness IS the field's level now, so it is also its reach: a level of twelve is twelve
        // cells. That makes a change to it a change to the whole flood, and the field was not noticing -- the
        // new level was stored and the texture kept the flood that was computed from the old one, so turning a
        // lamp up or down appeared to do nothing until some block happened to change nearby. Worse, turning a
        // lamp *down* past the point where the old flood covered the surface left the surface reading the old
        // field, which is the report "adjusting brightness sometimes leaves the lamp with no light at all".
        int wanted = Math.max(1, Math.min(MAX_LEVEL, lightLevel));
        if (field.level != wanted) {
            field.level = wanted;
            field.dirty = true;
            levelChanges++;
        }
        // Zero means "no field", so the layer is stored one-based. Two different things can leave it at zero and
        // both have to keep it there, which is what `useful` exists to tell apart:
        //   no layer yet  -- the field has not been built, so the texture holds nothing at this layer and a
        //                    lamp that sampled here would read a zero and go black for the tick it arrived on;
        //   never spread  -- the flood could not leave the lamp's own cell (a lamp sealed inside a full block),
        //                    so a non-zero w would publish an all-zero field and the lamp's light would vanish
        //                    instead of falling back to the ray model.
        // The layer is assigned in upload(), one tick behind this call, so a lamp is published one tick after it
        // first appears. `useful` is likewise only set by the flood, so this reads the previous tick's answer --
        // which is exactly right, because the texture it would sample was written in that same tick.
        boolean published = field.layer >= 0 && field.useful;
        // The origin is written only when the slot will actually be read. The array keeps its values from the
        // previous tick, so a slot that stops carrying a field would otherwise hand the shader an origin it is
        // never meant to combine with any w -- and a light that moved to a different slot leaves its old origin
        // behind for whoever lands there.
        out[base] = published ? field.origin.getX() : 0.0F;
        out[base + 1] = published ? field.origin.getY() : 0.0F;
        out[base + 2] = published ? field.origin.getZ() : 0.0F;
        out[base + 3] = published ? field.layer + 1 : 0.0F;
    }

    /** Say that a slot carries no field: the shader then keeps the behaviour it had before fields existed. */
    static void clearSlot(float[] out, int slot) {
        int base = slot * 4;
        out[base] = out[base + 1] = out[base + 2] = out[base + 3] = 0.0F;
    }

    /**
     * Clear the whole world-space array before a frame fills it.
     *
     * <p>The light list only writes the slots its own lights occupy, and that number moves: a frame with six
     * lamps in range leaves six slots filled, and the next frame with two leaves the other four holding whatever
     * they held before -- a live layer number and an origin, which is indistinguishable from a real field to
     * everything downstream. The packed copy is already cleared past the light count, so the shader is not
     * affected; the source array is what the debug line reads, and it showed six fields where two existed.
     */
    static void clearAll(float[] out) {
        java.util.Arrays.fill(out, 0.0F);
    }
    /** Drop the fields of lamps that are no longer in the list, and keep the texture in step. */
    static void retain(java.util.Set<String> live) {
        FIELDS.keySet().removeIf(key -> !live.contains(key));
    }

    /**
     * Rebuild and upload every field that is dirty. Called once a tick from the light list.
     *
     * <p>Bounded by MAX_FIELDS rebuilt boxes per tick, and a field whose lamp and blocks have not changed is not
     * touched at all, so the usual cost is zero. It used to stop after the first one, which starved the queue -- see
     * the note in the body.
     */
    static void tick(ClientLevel level) {
        tick++;
        // Re-asserted every tick: the unit is this mod's own, and binding it costs two state changes. If anything
        // resets the texture unit table the field would otherwise silently stop arriving, which is the one failure
        // this whole hunt kept running into.
        bindFieldUnit();
        if (level == null || FIELDS.isEmpty()) return;
        // Every dirty field, not one per tick.
        //
        // This used to return after the first rebuild, so the queue was served one lamp per tick. That is fine
        // with one lamp and wrong with several: placing or breaking blocks marks every field whose box contains the
        // change, and a newly placed lamp -- which starts dirty and has nothing in its layer yet -- simply waited
        // behind them. Its layer stayed zero, so it lit nothing that samples the field, while everything drawn by
        // another path still responded. Sixteen rebuilds in one tick is the worst case (MAX_FIELDS), and a rebuild
        // is a 32^3 walk, so serving them all is cheaper than a lamp that never arrives.
        for (Field field : FIELDS.values()) {
            if (!field.dirty) continue;
            field.dirty = false;
            rebuilds++;
            // A block change inside the box makes the cached costs stale; re-read them, then flood.
            fillCosts(level, field);
            flood(field);
            upload(field);
        }
    }

    /**
     * Mark every field whose box contains this position: a changed block changes the light around it.
     *
     * <p>This is the other half of a field's lifetime, and it was missing for a long time. A field's cached
     * block costs are read once, when it is built, so anything that changes a block inside its box makes the
     * flood that was computed from those costs a lie. Placing a lamp looked like it worked -- a new lamp is a
     * new field, and a new field is built from scratch -- but breaking a block changed nothing at all: the
     * lamp kept lighting the room the way it had when the wall was still there. From the player's seat that
     * reads as "the light does not update", and nothing in the log said so, because the flood was correct:
     * correct for a world that no longer existed.
     *
     * <p>Called from a client mixin on {@code ClientLevel.setBlocksDirty}, which is the one place every block
     * change on this side passes through -- the server's own update packet, a section update packet, and the
     * client's own prediction all end up there.
     */
    public static void blockChanged(BlockPos pos) {
        blockChanges++;
        for (Field field : FIELDS.values()) {
            // The field's own lattice: a lamp on the ground floods where it is drawn, and a lamp on a body
            // floods at its plot. A block placed on a hull arrives in plot coordinates, which is exactly the
            // lattice such a field lives on -- so one test serves both, and it has to be the field's own
            // origin rather than a world position.
            if (inside(field.origin, pos)) {
                field.dirty = true;
                dirtyMarks++;
            }
        }
    }

    /** Whether one block position falls inside a field's 32^3 box. */
    private static boolean inside(BlockPos origin, BlockPos pos) {
        return pos.getX() >= origin.getX() && pos.getX() < origin.getX() + SIZE
                && pos.getY() >= origin.getY() && pos.getY() < origin.getY() + SIZE
                && pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + SIZE;
    }

    /**
     * A whole chunk of block data just arrived on this client: mark every field whose box overlaps its column.
     *
     * <p>This is not the same fault as {@link #blockChanged}, but it has the same symptom, and it is worth
     * being precise about why. A chunk that has not arrived yet is not "unknown" to the level -- it reads as
     * air -- so a field built at that moment floods straight through the missing terrain and caches it as
     * free space. When the chunk does arrive, nothing marks the field dirty, so the flood stays wrong for as
     * long as the lamp lives there. Walking towards a lamp is exactly the case that produces it.
     *
     * <p>Hooked on {@code ClientChunkCache.replaceWithPacketData} rather than on {@code onChunkLoaded},
     * because the biome-only packet also calls {@code onChunkLoaded} and a biome change cannot change a
     * flood. Only real block data does.
     */
    public static void chunkData(int chunkX, int chunkZ) {
        chunkLoads++;
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (Field field : FIELDS.values()) {
            // A field on a body lives at the body's plot, so its plot column is the one that matters; a
            // ground lamp's own column is the world's. Both are simply `origin`, which is the point.
            if (overlaps(field.origin, minX, minZ)) { field.dirty = true; dirtyMarks++; }
        }
    }

    /** Whether a chunk's column overlaps a field's box. */
    private static boolean overlaps(BlockPos origin, int minX, int minZ) {
        return minX < origin.getX() + SIZE && minX + 16 > origin.getX()
                && minZ < origin.getZ() + SIZE && minZ + 16 > origin.getZ();
    }

    /** The lamp moved: its box is somewhere else, so it is a new field. */
    static void moved(String key, BlockPos lamp, int lightLevel) {
        Field field = FIELDS.get(key);
        if (field == null || !field.origin.offset(MAX_LEVEL, MAX_LEVEL, MAX_LEVEL).equals(lamp)) {
            FIELDS.put(key, new Field(lamp.immutable(), null));
            Field fresh = FIELDS.get(key);
            fresh.level = Math.max(1, Math.min(MAX_LEVEL, lightLevel));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // The flood itself
    // ------------------------------------------------------------------------------------------------

    /**
     * How much light one cell of this block costs to pass through, which is exactly vanilla's own number:
     * air and glass cost one (so they are only the step itself), leaves and water cost more, and anything
     * that blocks light outright is the ceiling. Read from the level, not from a table, so a packed ice
     * pane or a modded transparent block behaves the way the game says it does.
     */
    private static void fillCosts(ClientLevel level, Field field) {
        // Only the cells the flood can possibly reach.
        //
        // The box is 32 cells a side because that is what a level-15 light needs: vanilla decays one level per
        // block, so fifteen levels reach fifteen blocks and the box must be 2*15+1 = 31 across (32 when rounded).
        // A 16-cell box would cut a level-15 light off at seven and a half blocks and lose its outer ring entirely,
        // which is why the storage stays 32.
        //
        // The WORK, though, has nothing to do with the box: the flood stops when the level runs out, so reading
        // block states more than `level` cells from the lamp is wasted. This used to read all 32768 cells whatever
        // the lamp's strength was. A dim lamp now costs the small fraction it should, and the count only grows with
        // the reach that actually propagates.
        java.util.Arrays.fill(field.cost, (byte) 0);
        int reach = Math.max(1, Math.min(MAX_LEVEL, field.level));
        int lo = Math.max(0, MAX_LEVEL - reach);
        int hi = Math.min(SIZE - 1, MAX_LEVEL + reach);
        for (int z = lo; z <= hi; z++) {
            for (int y = lo; y <= hi; y++) {
                int rowBase = y * SIZE + z * SIZE * SIZE;
                for (int x = lo; x <= hi; x++) {
                    // Manhattan distance is the number of steps the flood needs to get here, walking one axis at a
                    // time, so anything beyond `reach` steps cannot be lit however empty the space is.
                    if (Math.abs(x - MAX_LEVEL) + Math.abs(y - MAX_LEVEL) + Math.abs(z - MAX_LEVEL) > reach) continue;
                    // The field's own lattice: on the ground the world's blocks, on a body the blocks in its
                    // plot, which is the only place a hull exists at all.
                    BlockPos pos = field.origin.offset(x, y, z);
                    // Outside the plot, the level's ground at those coordinates is not this ship's world. It is
                    // rock that happens to live twenty million blocks away, and letting it stop the flood is
                    // what kept a ship lamp's light inside the hull. Treat it as air: the hull still occludes
                    // (it is inside the pocket), the falloff is still one level a cell, but the pocket's edge is
                    // no longer a wall.
                    if (field.plot != null && !field.plot.contains(pos.getX(), pos.getZ())) continue;
                    var state = level.getBlockState(pos);
                    int cost = state.getLightBlock(level, pos);
                    field.cost[rowBase + x] = (byte) Math.max(0, Math.min(15, cost));
                }
            }
        }
    }

    /** Breadth-first, vanilla's rule: every neighbour costs one plus its own block's cost. */
    private static void flood(Field field) {
        java.util.Arrays.fill(field.levels, (byte) 0);
        // Re-decided by this flood, never inherited from the last one. A lamp whose brightness was turned down
        // to the point where it cannot reach anything used to keep the previous flood's useful=true and publish
        // an empty field, which reads as "the lamp is on, the light is here, and it lights nothing".
        field.useful = false;
        int[] queue = field.queue;
        int head = 0;
        int tail = 0;
        int lamp = index(MAX_LEVEL, MAX_LEVEL, MAX_LEVEL);
        // The lamp's own cell must not charge for itself. A stand-in is a full block, and a full block's own
        // light cost would stop the flood at its first step and leave an empty field -- which reads exactly
        // like a lamp that does not light at all. Vanilla never has this problem because its light sources
        // are not full blocks; ours are, so the origin is exempted here.
        field.cost[lamp] = 0;
        field.levels[lamp] = (byte) field.level;
        queue[tail++] = lamp;
        while (head < tail) {
            int cell = queue[head++];
            int level = field.levels[cell] & 0xFF;
            if (level <= 1) continue;
            int x = cell & 31;
            int y = (cell >> 5) & 31;
            int z = (cell >> 10) & 31;
            if (x > 0) tail = spread(field, queue, tail, cell - 1, level);
            if (x < SIZE - 1) tail = spread(field, queue, tail, cell + 1, level);
            if (y > 0) tail = spread(field, queue, tail, cell - 32, level);
            if (y < SIZE - 1) tail = spread(field, queue, tail, cell + 32, level);
            if (z > 0) tail = spread(field, queue, tail, cell - 1024, level);
            if (z < SIZE - 1) tail = spread(field, queue, tail, cell + 1024, level);
        }
        markSolid(field);
    }

    /**
     * Bit 4 of a cell's byte: the block there is opaque, so it has no light of its own and never will.
     *
     * <p>Levels only use 0..15, so four bits of every byte have been carrying nothing, and this is what they are
     * for. The shader needs to tell two cells apart that both read zero -- a wall, and an air cell the flood has
     * not reached -- because they must be treated in opposite ways: a wall has to be replaced by the face's own
     * level, and an unlit air cell has to be left alone so the light can fade into it. Without the distinction
     * the edge of every lit pool is flattened, because that edge is nothing but unlit air cells, and a flattened
     * neighbourhood has no gradient left -- its contour collapses onto the cell grid, which is what the sawtooth
     * along the edge of the light was.
     *
     * <p>Applied after the flood, never before: {@code spread} compares the byte against the level it wants to
     * write, so a flag set earlier would make every opaque cell look as though it were already lit.
     */
    private static void markSolid(Field field) {
        for (int cell = 0; cell < CELLS; cell++) {
            if ((field.cost[cell] & 0xFF) >= MAX_LEVEL) field.levels[cell] |= SOLID_BIT;
        }
    }

    /** Set in a cell's byte when the block there is opaque. Levels and this flag share one byte. */
    static final int SOLID_BIT = 16;

    /** The light level in a cell's byte, with the solid flag removed. */
    static int levelOf(byte value) {
        return value & (SOLID_BIT - 1);
    }

    private static int spread(Field field, int[] queue, int tail, int cell, int level) {
        int next = level - 1 - (field.cost[cell] & 0xFF);
        if (next <= 0) return tail;
        if ((field.levels[cell] & 0xFF) >= next) return tail;
        field.levels[cell] = (byte) next;
        field.useful = true;
        if (tail < queue.length) queue[tail++] = cell;
        return tail;
    }

    private static int index(int x, int y, int z) {
        return x | (y << 5) | (z << 10);
    }

    // ------------------------------------------------------------------------------------------------
    // The texture
    // ------------------------------------------------------------------------------------------------

    /**
     * Create the field texture if it does not exist yet, without needing a field to put in it.
     *
     * <p>Called while the pipeline is being built, not only when a field is first uploaded. The upload used to be
     * the only creator, and that is late: the sampler is registered at pipeline build time, so the name the
     * supplier reported then was 0, and Iris had nothing to bind. All sixteen layers start as zeros, which is
     * exactly what "no field here" means to the shader.
     */
    public static void ensureTexture() {
        createTexture();
        registerDynamicTexture();
    }

    /** Set once the field is visible to Iris as {@code minecraft:dynamic/slo_field}. */
    private static boolean registered;

    /**
     * Register the field with Minecraft's texture manager under a dynamic name, so the pack can reach it the one
     * way Iris documents.
     *
     * <p>A pack gets a texture other than its own through {@code shaders.properties}:
     * {@code customTexture.sloField = <path>}. The path may be a pack file, a resource pack image or a
     * <em>dynamic</em> texture ({@code minecraft:dynamic/lightmap_1} is the documented example, and the lightmap
     * is rewritten every frame). This mod's field is rewritten every time a lamp's flood changes, so a dynamic
     * registration is the only kind that fits -- and that is why the field is a 2D image now: a dynamic texture is
     * a 2D texture.
     *
     * <p>The texture manager holds the registration, but this mod owns the GL name: {@link FieldTexture#getId()}
     * returns the texture this class uploads into, so Iris binds a name whose contents are already current.
     */
    private static void registerDynamicTexture() {
        if (registered) return;
        registered = true;
        try {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation
                    .fromNamespaceAndPath("minecraft", "dynamic/slo_field");
            net.minecraft.client.Minecraft.getInstance().getTextureManager().register(id, new FieldTexture());
            com.mojang.logging.LogUtils.getLogger().info(
                    "Sable Light Overhaul: field texture registered as {} (gl {}) for customTexture.sloField",
                    id, texture);
        } catch (RuntimeException | LinkageError ex) {
            com.mojang.logging.LogUtils.getLogger().error(
                    "Sable Light Overhaul: the field could not be registered as a dynamic texture", ex);
        }
    }

    /** The texture manager's view of the field: an id this class owns and updates. */
    private static final class FieldTexture extends net.minecraft.client.renderer.texture.AbstractTexture {
        @Override
        public int getId() {
            createTexture();
            return texture;
        }

        @Override
        public void load(net.minecraft.server.packs.resources.ResourceManager manager) {
            // Nothing to read: the contents come from the light fields, not from a file.
        }
    }

    /** A layer of zeros, reused when the texture is created: the largest buffer this class allocates is one field. */
    private static byte[] ZEROS;

    /**
     * Diagnostic: when non-zero, every texel of every layer is created with this level and the real per-lamp
     * uploads are skipped. Set back to 0 for normal operation.
     *
     * <p>Back to 0: the shader no longer needs the texture to be a constant to demonstrate that it arrived. It
     * now falls back to a quantised distance approximation when the sample is missing and marks that fallback
     * blue, so a real field arriving is visible as normal light and a missing one as blue steps -- and the build
     * is useful either way instead of producing nothing.
     */
    private static final int FILL_LEVEL = 0;

    /**
     * The texture unit this mod owns and binds the field to.
     *
     * <p>The field reaches the pack as an <em>external</em> sampler: Iris is told that the name {@code sloField}
     * reads this unit, and this mod binds the texture to it. The alternative -- a dynamic sampler, where Iris
     * binds the texture itself from a supplier -- reported success in 22 programs and delivered zero even when
     * every texel of the texture held the maximum level, so its bookkeeping is the part that failed.
     *
     * <p>The unit is the last one the GL implementation reports. Iris allocates its own units from zero upwards,
     * so the last is the one least likely to collide, and it is a fixed choice rather than a search: a unit that
     * changes between launches would make a failure impossible to reproduce.
     */
    private static int fieldUnit = -1;

    public static int fieldUnit() {
        if (fieldUnit < 0) {
            int units = 32;
            try {
                units = GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
            } catch (RuntimeException | LinkageError ignored) {
                // No context: a value the caller will never bind to anyway. Kept away from zero on purpose.
            }
            fieldUnit = Math.max(1, units - 1);
        }
        return fieldUnit;
    }

    /**
     * Make this mod's own texture unit hold the field texture.
     *
     * <p>Called after the texture is created and after every upload, and once per client tick. The unit is ours,
     * so nothing else is expected to disturb it; re-binding on a tick is cheap insurance for the case where
     * something resets the whole texture unit table.
     */
    public static void bindFieldUnit() {
        if (texture == 0 || textureFailed) return;
        int unit = fieldUnit();
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    /**
     * The field, as a 2D image: {@link #WIDTH} by {@link #HEIGHT}, one byte per cell.
     *
     * <p>It was a 3D texture, and that is what stopped it from reaching the pack. Every route a mod can use to
     * hand a 3D texture to an Iris pack was tried and measured: a dynamic sampler (reported success in 22 programs,
     * sampled zero), and an external sampler on this mod's own unit (same). What does work, per Iris' own
     * documentation, is a custom texture declared in {@code shaders.properties} -- and the only source that can be
     * updated every frame is a Minecraft dynamic texture, which is a 2D texture.
     *
     * <p>The layout is the array's own order, widened: cell (x, y, z) of a layer is at column {@code x + y * 32}
     * and row {@code z} of that layer, so one 32768-byte field is one 1024 x 32 image and the sixteen layers stack
     * vertically. The CPU buffer needs no reshuffling to match: index(x, y, z) = x | (y << 5) | (z << 10) is
     * already row-major at width 1024.
     */
    static final int WIDTH = SIZE * SIZE;
    static final int HEIGHT = SIZE * MAX_FIELDS;

    /** The GL texture, made on first use. Separate from upload() so it can be made before a field exists. */
    private static void createTexture() {
        if (texture != 0 || textureFailed) return;
        try {
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            // Nearest, deliberately. Linear filtering cannot smooth this layout: a row packs x + y * 32, so two
            // cells that are neighbours in the world are 32 texels apart inside a row, and interpolating would mix
            // unrelated cells. The sampler also lands exactly on texel centres, where a linear filter returns the
            // stored value unchanged anyway. Smoothing has to come from a real 3D layout, not from the filter.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            // Nearest filtering on purpose: a data texture read by computed texel, so interpolating between cells
            // would invent light levels that were never flooded.
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, WIDTH, HEIGHT, 0,
                    GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            if (ZEROS == null) {
                ZEROS = new byte[CELLS];
                // SLO diagnostic: fill the whole texture with the lamp's own level instead of zeros, so that a
                // sample ANYWHERE returns a non-zero value. Set to 0 to get the real behaviour back.
                //
                // This exists because every link in the chain has now been measured and all of them agree: the
                // field is built (lit=2633), it is uploaded (uploads=1, and the lamp's own cell in the buffer that
                // was sent reads 15), the sampler reports a bind in 22 programs, and the shader still samples
                // zero. With the whole texture non-zero there is nothing left for a coordinate, a layer or a
                // cell-index error to do: a sample of any kind returns non-zero, or the sampler is not delivering
                // the texture at all. That is the one question the previous measurements cannot answer, and this
                // answers it in a single frame.
                if (FILL_LEVEL > 0) java.util.Arrays.fill(ZEROS, (byte) FILL_LEVEL);
            }
            java.nio.ByteBuffer layer = java.nio.ByteBuffer.allocateDirect(CELLS);
            for (int l = 0; l < MAX_FIELDS; l++) {
                layer.clear();
                layer.put(ZEROS).flip();
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, l * SIZE, WIDTH, SIZE,
                        GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, layer);
            }
        } catch (RuntimeException | LinkageError ex) {
            textureFailed = true;
            com.mojang.logging.LogUtils.getLogger().error(
                    "Sable Light Overhaul: the light field texture could not be made; lamps keep their ray model", ex);
        }
    }

    /**
     * Put the unpack state back to the only values a tightly packed upload can use.
     *
     * <p>These are global, not per-texture, and Minecraft's renderer rewrites them constantly: Sodium sets a row
     * length when it uploads a sprite sheet, Iris sets skips for its buffers. An upload that assumes the defaults
     * gets whatever the last user left behind, and the failure is silent -- the driver reads the buffer with the
     * wrong stride and the texture ends up holding shifted rows or nothing at all.
     */
    private static void unpackState() {
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL30.GL_UNPACK_SKIP_IMAGES, 0);
        GL11.glPixelStorei(GL30.GL_UNPACK_IMAGE_HEIGHT, 0);
    }

    private static void upload(Field field) {
        if (textureFailed) return;
        // Diagnostic build: the texture is a recognised constant, so the real upload must not overwrite it.
        if (FILL_LEVEL > 0) return;
        if (field.layer < 0) {
            field.layer = nextFreeLayer();
            if (field.layer < 0) {
                // More lamps want a field than the texture holds: the extras keep their ray model, which is
                // the honest failure -- a lamp that cannot have a field must not look like one that can.
                field.layer = -1;
                return;
            }
        }
        if (texture == 0) {
            createTexture();
            if (textureFailed) return;
        } else {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        }
        try {
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(CELLS);
            buffer.put(field.levels).flip();
            // The unpack state is global and belongs to whoever used it last. Sodium and Iris drive texture uploads
            // with GL_UNPACK_ROW_LENGTH and the SKIP_* values set to anything at all, and this upload happens on a
            // client tick -- hundreds of frames after the texture was created, so the alignment set back then says
            // nothing about now. With a wrong row length the driver reads the 32768 bytes with the wrong stride and
            // writes shifted or empty rows, which is indistinguishable from "the upload never ran".
            unpackState();
            // One field is a 1024 x 32 image, so a layer is a horizontal band at row layer * 32.
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, field.layer * SIZE, WIDTH, SIZE,
                    GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, buffer);
            // What the driver made of it, and what the texture actually is. Both are one cheap query and neither
            // reads any pixels back, which is what crashed this class once before. A non-zero error here names the
            // upload; a size other than 1024 x 512 names the allocation; and if both are clean then the bytes are
            // in the texture and the fault is the address the shader asks for.
            lastUploadError = GL11.glGetError();
            lastTextureWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            lastTextureHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            // Recorded, not just done. "The shader reads zero" has two possible causes and this is the one that
            // says whether the upload side is even in play: the count, the layer, and the value at the lamp's own
            // cell, which is what the shader now probes for. If the lamp cell is non-zero here and the shader
            // still reads zero, the fault is entirely on the sampler side and nothing about the upload matters.
            uploads++;
            lastUploadLayer = field.layer;
            lastUploadLampCell = levelOf(field.levels[index(MAX_LEVEL, MAX_LEVEL, MAX_LEVEL)]);
            // The upload left the texture bound on whatever unit was active; put it back on this mod's own unit.
            bindFieldUnit();
        } catch (RuntimeException | LinkageError ex) {
            textureFailed = true;
            com.mojang.logging.LogUtils.getLogger().error(
                    "Sable Light Overhaul: the light field texture could not be made; lamps keep their ray model", ex);
        }
    }

    /** How many layer uploads have run, and the last one's layer and lamp cell. For the diagnostic line only. */
    private static long uploads;
    private static int lastUploadLayer = -1;
    private static int lastUploadLampCell = -1;
    private static int lastUploadError;
    private static int lastTextureWidth = -1;
    private static int lastTextureHeight = -1;

    static String describeUpload() {
        return "uploads=" + uploads + " lastLayer=" + lastUploadLayer
                + " lastLampCell=" + lastUploadLampCell
                + " glError=" + lastUploadError
                + " tex=" + lastTextureWidth + "x" + lastTextureHeight;
    }

    /*
     * REMOVED, and why it must not come back in this shape.
     *
     * A glGetTexImage round trip lived here, to tell "the data never reached the GPU" from "the data is not where
     * it is sampled". It crashed the driver and then the game, with no crash report, and it was reported as a
     * memory/GPU fault coming from outside Java. The report names it exactly:
     *
     *   EXCEPTION_ACCESS_VIOLATION ... Problematic frame: C [nvoglv64.dll+0x15c3375]
     *     j org.lwjgl.opengl.GL11.glGetTexImage
     *     j dev.sablelightoverhaul.client.LightFields.verifyLayerOnGpu
     *     j dev.sablelightoverhaul.client.IrisMuzzleBridge.describeGate
     *     j dev.sablelightoverhaul.client.AdvancedLights.gpuUpdate
     *
     * glGetTexImage writes the ENTIRE texture -- SIZE * SIZE * SIZE * MAX_FIELDS, about 16.7 MB -- into the buffer
     * it is handed. The buffer here was one layer, CELLS = 32 KB. The driver wrote 16.7 MB into a 32 KB heap
     * allocation: heap corruption, then an access violation inside the driver.
     *
     * Two rules came out of it, both in the development document:
     *   1. A GL readback buffer is sized to what the API writes, never to what the caller needs.
     *   2. A raw GL call issued from a client tick is not safe. Iris owns the texture bindings and the active unit
     *      while it renders, so "bind it and read it back" asserts ownership of state this mod does not have.
     */

    /**
     * The layer + 1 this lamp's field lives in, or 0 when it has none.
     *
     * <p>This is what the shader is given instead of a field origin. It travels in a channel that is already
     * known to reach the pack -- the fourth component of a per-slot uniform -- because the origin uniform was
     * tried in three shapes and never arrived, and a value that does not arrive is indistinguishable from a lamp
     * that was never switched on.
     */
    static float layerFor(String key) {
        if (key == null) return 0.0F;
        Field field = FIELDS.get(key);
        if (field == null || field.layer < 0 || !field.useful) return 0.0F;
        return field.layer + 1.0F;
    }

    private static int nextFreeLayer() {
        boolean[] used = new boolean[MAX_FIELDS];
        for (Field field : FIELDS.values()) {
            if (field.layer >= 0 && field.layer < MAX_FIELDS) used[field.layer] = true;
        }
        for (int i = 0; i < MAX_FIELDS; i++) if (!used[i]) return i;
        return -1;
    }

    /**
     * One line describing every field this client is holding, for the Advanced debug switch.
     *
     * <p>This exists because the field is the one part of this mod whose failures are invisible: the shader
     * branch it feeds simply does nothing, which in game looks exactly like a lamp that was never asked to
     * light. The three ways it goes quiet are a field that never spread ({@code useful=false}), a field that
     * never got a layer because sixteen other lamps hold them all, and a field whose slot was never published
     * at all -- and {@code w} is the only one of the three a log line can prove, because it is what the shader
     * reads. Painting the field's own value into the colour (the way the shield work did) is the other half of
     * the method; both are described in the development document.
     */
    static String describe() {
        if (FIELDS.isEmpty()) return "none " + invalidations();
        StringBuilder text = new StringBuilder();
        for (var entry : FIELDS.entrySet()) {
            Field field = entry.getValue();
            int lit = 0;
            int max = 0;
            for (byte raw : field.levels) {
                // Masked: the top bit is the solid flag, and counting it as light would report a wall as lit.
                int value = levelOf(raw);
                if (value > 0) lit++;
                if (value > max) max = value;
            }
            text.append(' ').append(entry.getKey())
                    .append("[origin=").append(field.origin.getX()).append(',')
                    .append(field.origin.getY()).append(',').append(field.origin.getZ())
                    .append(" level=").append(field.level)
                    .append(" lit=").append(lit)
                    .append(" max=").append(max)
                    .append(" layer=").append(field.layer)
                    // The published value, one-based: this is the number the shader tests with w > 0. A zero
                    // here is a lamp on the vanilla model that is drawing the ray fallback instead of a field.
                    .append(" w=").append(field.layer < 0 || !field.useful ? 0 : field.layer + 1)
                    .append(" useful=").append(field.useful)
                    // Whether the flood is treating the ground outside the plot as air. A false here means the
                    // plot could not be asked, and the light stops at the hull exactly as it did before this
                    // rule existed -- with no other sign that anything is missing.
                    .append(" pocket=").append(field.plot != null ? 1 : 0)
                    // The levels straight out along +X from the lamp, plus the cost of the first air cell. This is
                    // the flood's own decay rate, and it is the number that says whether "the light stops after a
                    // few blocks" is the lamp's strength, a cost that is too high on a transparent block, or a
                    // neighbour that is charging twice.
                    .append(" axis=").append(axis(field))
                    .append(']');
        }
        return text.append(' ').append(invalidations()).toString();
    }

    /**
     * What the field holds at the pixel the crosshair is on, computed the way the shader computes it.
     *
     * <p>This exists because a screenshot cannot answer the only question that matters when a surface is
     * dark: is the field empty there, or is the field lit there and something else -- the pack's screen-space
     * occlusion, a shadow, its own ambient -- darkening it? Those two have opposite fixes, and every attempt
     * to tell them apart from the picture has cost a round.
     *
     * <p>So the oracle is the CPU, and it is exact rather than approximate: the probe is taken from the
     * crosshair's own ray hit, pushed half a block along the face normal, and converted to a cell with the
     * <em>same formula the shader uses</em> -- {@code floor(fieldSpace + 15.5)} with the field anchored on the
     * lamp's cell centre. If this line reports a level and the pixel is still black, the fault is not in the
     * field and not in the sampling address; it is in what is done with the sample afterwards.
     *
     * <p>One entry per live field: the cell it resolved to, the level stored there, and the block cost, which
     * together say whether the flood reached that cell (level > 0) or the cell is solid (cost 15).
     */
    static String describeLook() {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (!(minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)) return "none";
        var face = hit.getDirection();
        var at = hit.getBlockPos();
        double px = hit.getLocation().x + face.getStepX() * 0.5;
        double py = hit.getLocation().y + face.getStepY() * 0.5;
        double pz = hit.getLocation().z + face.getStepZ() * 0.5;
        StringBuilder text = new StringBuilder("(").append(at.getX()).append(',').append(at.getY()).append(',')
                .append(at.getZ()).append(") face=").append(face.getName())
                .append(String.format(java.util.Locale.ROOT, " probe=(%.2f,%.2f,%.2f)", px, py, pz));
        if (FIELDS.isEmpty()) return text.append(" no field").toString();
        for (var entry : FIELDS.entrySet()) {
            Field field = entry.getValue();
            // The shader's anchor: the lamp's cell centre. Cell i's centre is i - 15 in field space, and the
            // shader resolves a position to a cell with floor(q + 15.5), so this is the same address.
            double ax = field.origin.getX() + MAX_LEVEL + 0.5;
            double ay = field.origin.getY() + MAX_LEVEL + 0.5;
            double az = field.origin.getZ() + MAX_LEVEL + 0.5;
            int cx = (int) Math.floor(px - ax + 15.5);
            int cy = (int) Math.floor(py - ay + 15.5);
            int cz = (int) Math.floor(pz - az + 15.5);
            boolean inside = cx >= 0 && cx < SIZE && cy >= 0 && cy < SIZE && cz >= 0 && cz < SIZE;
            int cell = inside ? index(cx, cy, cz) : -1;
            text.append(' ').append(entry.getKey())
                    .append(" cell=(").append(cx).append(',').append(cy).append(',').append(cz).append(')')
                    // level 0 + cost 0 is unlit air (the flood never got here); cost > 0 is a solid cell, which
                    // means the probe went the wrong way for this face or the surface is inside its own block.
                    .append(" level=").append(inside ? levelOf(field.levels[cell]) : -1)
                    .append(" cost=").append(inside ? field.cost[cell] & 0xFF : -1)
                    .append(" layer=").append(field.layer)
                    .append(" lamp=").append(field.origin.getX() + MAX_LEVEL).append(',')
                    .append(field.origin.getY() + MAX_LEVEL).append(',')
                    .append(field.origin.getZ() + MAX_LEVEL);
            if (inside) text.append(blendBlock(field, face, px, py, pz, cx, cy, cz));
        }
        return text.toString();
    }

    /**
     * The nine cells the shader averages around this pixel, the four vertex values it builds from them, and the
     * bilinear result -- the number that actually gets shaded.
     *
     * <p>This is the half of the shader's arithmetic the single-cell probe could not see, and it is the half that
     * matters when one cell of a lit area comes out darker than its neighbours: the shaded value is an average of
     * four vertices, each an average of four cells, so a cell can read 14 while the value four cells are averaged
     * into reads 9. The row is printed as {@code 3x3} in (t1, t2) order, then the vertex values, then the bilinear
     * result and its brightness.
     *
     * <p>It reproduces the shader exactly: the same tangent axes ({@code t2 = cross(normal, t1)}), the same
     * substitution of an unlit cell by the cell in front of the face, the same weights from the fragment's own
     * position inside the face.
     */
    private static String blendBlock(Field field, net.minecraft.core.Direction face,
            double px, double py, double pz, int cx, int cy, int cz) {
        // The shader's in-plane axes, chosen from the face normal exactly as slo_light.glsl chooses them.
        int nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();
        int t1x, t1y, t1z;
        if (Math.abs(nx) > Math.abs(ny)) {
            t1x = 0; t1y = 1; t1z = 0;
        } else {
            t1x = 1; t1y = 0; t1z = 0;
        }
        int t2x = ny * t1z - nz * t1y;
        int t2y = nz * t1x - nx * t1z;
        int t2z = nx * t1y - ny * t1x;
        // level[du + 1][dv + 1], with du along t1 and dv along t2.
        int[][] level = new int[3][3];
        // solid[du + 1][dv + 1]: the shader's second channel, and the reason the top bit of every byte is spent
        // on it. A wall and an unlit air cell both read level zero, and they must be treated in opposite ways.
        boolean[][] solid = new boolean[3][3];
        for (int du = -1; du <= 1; du++) {
            for (int dv = -1; dv <= 1; dv++) {
                int x = cx + du * t1x + dv * t2x;
                int y = cy + du * t1y + dv * t2y;
                int z = cz + du * t1z + dv * t2z;
                boolean outside = x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE;
                byte raw = outside ? 0 : field.levels[index(x, y, z)];
                solid[du + 1][dv + 1] = !outside && (raw & SOLID_BIT) != 0;
                level[du + 1][dv + 1] = outside ? -1 : levelOf(raw);
            }
        }
        // The fraction of the way across the face, from the probe's position along each axis.
        double pu = px * t1x + py * t1y + pz * t1z;
        double pv = px * t2x + py * t2y + pz * t2z;
        double cu = Math.floor(pu + 0.5);
        double cv = Math.floor(pv + 0.5);
        double fa = pu - cu + 0.5;
        double fb = pv - cv + 0.5;
        int centre = level[1][1];
        // The shader substitutes before it interpolates, and the rule has three parts: an opaque cell and a cell
        // outside the slab are replaced by the face's own level, a cell that is lit but more than two levels
        // below it came from around a corner and is replaced too, and an UNLIT AIR CELL IS LEFT ALONE -- that
        // last one is what keeps a gradient at the edge of the pool instead of a flat patch per block.
        for (int du = 0; du < 3; du++) {
            for (int dv = 0; dv < 3; dv++) {
                int value = level[du][dv];
                boolean replace = solid[du][dv] || value < 0 || (value > 0 && value < centre - 2);
                level[du][dv] = replace ? centre : value;
            }
        }
        // The reconstruction the shader uses now: a quadratic B-spline over the same nine cells, which is C1.
        // A bilinear one is only C0, and the slope jump at every cell boundary is what reads as facets.
        double ua = fa - 0.5;
        double ub = fb - 0.5;
        double wa0 = 0.5 * (0.5 - ua) * (0.5 - ua);
        double wa1 = 0.75 - ua * ua;
        double wa2 = 0.5 * (0.5 + ua) * (0.5 + ua);
        double wb0 = 0.5 * (0.5 - ub) * (0.5 - ub);
        double wb1 = 0.75 - ub * ub;
        double wb2 = 0.5 * (0.5 + ub) * (0.5 + ub);
        double blended = wa0 * (wb0 * level[0][0] + wb1 * level[0][1] + wb2 * level[0][2])
                + wa1 * (wb0 * level[1][0] + wb1 * level[1][1] + wb2 * level[1][2])
                + wa2 * (wb0 * level[2][0] + wb1 * level[2][1] + wb2 * level[2][2]);
        double lv = Math.max(0.0, Math.min(1.0, blended / 15.0));
        double lv2 = lv * lv;
        double shaped = lv2 * lv2 * lv2 * lv2 + 0.18 * lv2 + 0.16 * lv * (2.0 - lv);
        return String.format(java.util.Locale.ROOT,
                " 3x3=[%d %d %d|%d %d %d|%d %d %d] fa=%.2f fb=%.2f shaded=%.2f bright=%.3f",
                level[0][0], level[0][1], level[0][2],
                level[1][0], level[1][1], level[1][2],
                level[2][0], level[2][1], level[2][2],
                fa, fb, blended, shaped);
    }

    /**
     * The four counters of the invalidation chain, as one bracketed group: changes seen, chunks seen, marks
     * made, rebuilds run. Read it left to right -- the first counter that is zero is the link that is broken,
     * and everything after it is zero for that reason alone.
     */
    private static String invalidations() {
        return "inv(change=" + blockChanges + " chunk=" + chunkLoads + " level=" + levelChanges
                + " marks=" + dirtyMarks + " rebuild=" + rebuilds + ')';
    }

    /** Levels at the lamp cell and the four cells out along +X, and the block cost of the first of them. */
    private static String axis(Field field) {
        StringBuilder text = new StringBuilder();
        for (int step = 0; step <= 4; step++) {
            if (step > 0) text.append(',');
            text.append(levelOf(field.levels[index(MAX_LEVEL + step, MAX_LEVEL, MAX_LEVEL)]));
        }
        text.append(" cost+1=").append(field.cost[index(MAX_LEVEL + 1, MAX_LEVEL, MAX_LEVEL)] & 0xFF);
        return text.toString();
    }

    /**
     * The four floats the shader actually receives for each slot, as {@code (x, y, z, w)} in the pack's space.
     *
     * <p>This is the only way to see the published side of the contract. The field's own state above can be
     * perfect while the uniform carries something else -- the world-to-scene conversion, the slot index and the
     * per-frame timing all sit between them -- and the shader's symptom for all three is the same: a
     * bounds-tested sample that fails everywhere, or a field that belongs to a different lamp.
     */
    static String describePublished() {
        float[] out = IrisMuzzleBridge.packedSlots();
        StringBuilder text = new StringBuilder();
        for (int slot = 0; slot < MAX_FIELDS * 2; slot++) {
            int base = slot * 4;
            if (base + 3 >= out.length) break;
            if (out[base + 3] <= 0.0F) continue;
            text.append(" slot").append(slot).append("=(")
                    .append(Math.round(out[base])).append(',')
                    .append(Math.round(out[base + 1])).append(',')
                    .append(Math.round(out[base + 2])).append(",w=")
                    .append(out[base + 3]).append(')');
        }
        return text.length() == 0 ? " none published" : text.toString();
    }

    /**
     * The raw world-space source for the first slots, and how many of them carry a field.
     *
     * <p>This exists to tell a stale array from a stale probe. The published values come out of a copy, so if
     * that copy shows a field in a slot the source never filled, the fault is in the copy; if the source itself
     * still holds a lamp from an earlier frame, the fault is that nothing clears the slots past the lights that
     * are in the list now.
     */
    static String describeRaw() {
        float[] source = IrisMuzzleBridge.fieldSlots();
        int carrying = 0;
        for (int slot = 0; slot < source.length / 4; slot++) {
            if (source[slot * 4 + 3] > 0.0F) carrying++;
        }
        StringBuilder text = new StringBuilder("sourceW=").append(carrying).append(" (");
        for (int slot = 0; slot < 12; slot++) {
            int base = slot * 4;
            text.append(slot).append(":w=").append(source[base + 3])
                .append("/x=").append(Math.round(source[base])).append(' ');
        }
        return text.append(')').toString();
    }
}
