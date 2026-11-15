package dev.sablelightoverhaul.client;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The one place we touch Voxy, and the only place that has to be revisited when it updates.
 *
 * <p>It answers one question: "looking from here, is that point hidden by the distant terrain?" The answer
 * is kept as a table of directions, each holding the range of the first thing that blocks it -- a horizon,
 * in the same spirit as the depth pyramid Voxy keeps for itself, only in angle rather than in pixels.
 *
 * <p>Angles, because at LOD range direction is what decides visibility and distance is not. The key comes
 * from a camera position snapped to a grid, so that walking a few blocks does not re-key every direction at
 * once. The cast itself always starts from the true camera position.
 *
 * <p>Rules the design rests on:
 *
 * <ol>
 *   <li><b>"Unknown" and "clear" are different answers.</b> A ray whose way Voxy cannot see -- sections it
 *       has not ingested, which is what happens for a while after shelling rearranges the ground -- is not a
 *       clear line. It keeps the direction's previous answer, and keeps it alive. Treating both as clear is
 *       what let a whole cloud reappear for a few seconds in the field test.</li>
 *   <li><b>Nothing happens unprompted.</b> A direction is cast when something asks for it and the answer is
 *       missing or stale, or when an explosion has queued it in advance.</li>
 *   <li><b>A coarse parent can only stand in for a missing fine cell, and is never stored as one.</b></li>
 *   <li><b>Only beyond the render distance</b>, read from the player's own setting.</li>
 *   <li><b>Fail open.</b> No Voxy, no engine, no section, no budget, and the switch off: visible.</li>
 *   <li><b>Only large occlusion.</b> The blocker has to be {@code occlusionMargin} blocks in front of the
 *       thing being judged, which keeps a puff whose centre sits inside a block from flickering.</li>
 *   <li><b>Hysteresis</b> on the range, so grazing a ridge for one sample cannot change the answer.</li>
 * </ol>
 */
@net.neoforged.fml.common.EventBusSubscriber(modid=dev.sablelightoverhaul.SableLightOverhaul.ID,value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class VoxyAccess {
   
 /**
  * Lets go of everything this class holds in Voxy, and of every cached answer.
  *
  * <p>Called when a world goes away. A held section is a reference Voxy counts, and this class holds one across
  * frames so that a ray does not pay for an acquire per sample -- which is correct while a world lives and wrong the
  * moment it does not: the engine never finishes shutting that world down ("not all worlds shutdown, force closing
  * worlds"), and the next world entered hangs behind it.
  */
 public static void reset() {
  releaseQuietly(rayHeld);
  releaseQuietly(cubeHeld);
  rayHeld=null;cubeHeld=null;rayWorld=null;
  rayHeldKey=Long.MIN_VALUE;cubeHeldKey=Long.MIN_VALUE;
  coarseAcquired=false;diagnosticCubeAcquired=false;
  HORIZON.clear();PARENT.clear();CORE.clear();DISC.clear();
 }

 private static void releaseQuietly(Object section) {
  if(section==null||release==null)return;
  try{release.invoke(section);}catch(Throwable ignored){
   // A world that is already shutting down may refuse the release; the reference is dropped either way.
  }
 }

 @net.neoforged.bus.api.SubscribeEvent
 public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
  if(event.getLevel().isClientSide())reset();
 }

    /** One direction: the range at which something first blocks it, and how that range is being updated. */
    private record Horizon(double range, double candidate, int repeated, long tick, long hold,
                            double hitX, double hitY, double hitZ) {}

    /** The camera is snapped to this grid before it is turned into a direction key. */
    private static final double KEY_GRID = 8.0;
    /** Not a range: the ray could not be answered because part of its way was missing from Voxy. */
    private static final double UNKNOWN = -1.0;

    private static final Map<Long, Horizon> HORIZON = new HashMap<>();
    private static final Map<Long, Horizon> PARENT = new HashMap<>();
    /** Directions a coming explosion asked for: its core first, then the ring around it. */
    private static final Deque<Vec3> CORE = new ArrayDeque<>();
    private static final Deque<Vec3> DISC = new ArrayDeque<>();

    private static int budget;
    private static int casts;
    private static int culled;
    private static int clear;
    private static long lastSample;
    /** The section held across samples of the current ray, so a lookup is not an acquire. */
    private static Object rayWorld;
    private static Object rayHeld;
    private static long[] rayVoxels;
    private static long rayHeldKey = Long.MIN_VALUE;
    private static int rayLevel;
    private static int raySamples;
    /** Diagnostic only: whether the coarse mip level answers at all, and what it reports. */
    private static boolean coarseAcquired;
    private static boolean diagnosticCubeAcquired;
    private static int coarseSamples;
    private static int coarseFirstNonEmpty = -1;
    private static boolean rayIncomplete;
    private static Object cubeHeld;
    private static long[] cubeVoxels;
    private static long cubeHeldKey = Long.MIN_VALUE;
    /** The coarse walk of a ray, refined to one block around the first non-empty voxel. */
    private static final int COARSE_STEP = 8;
    /** The mip level the coarse walk asks: level 3 is an 8 block cube, so a thin wall inside it counts. */
    private static final int COARSE_LEVEL = 3;
    /** Where the last ray was hit, and how thick that hit was along the ray. Measurement only. */
    private static double lastHitAt = -1.0;
    private static int lastHitY;
    private static double lastHitX, lastHitYWorld, lastHitZ;
    /** How long that hit ran along the ray, in blocks. */
    /** How many rays were answered by each mip level this second, level 0 first. */
    private static final int[] LEVELS_USED = new int[8];
    /**
     * The highest LOD level Voxy can hold: its own {@code WorldEngine.MAX_LOD_LAYER}, read from the jar rather
     * than guessed.
     *
     * <p>The lookup loop used to try levels 0 to 7, which cost three {@code acquireIfExists} calls per section
     * change that could never return anything. The engine's own constant is 4, so levels 0 to 4 are the whole
     * space, and this only matters because the loop runs on every section crossed by every ray.
     */
    private static final int MAX_LOD_LAYER = 4;
    private static int passes;
    private static long lastReport;
    private static boolean unavailable;

    private static Method ofEngineNullable, isLive, acquireIfExists, release, rawData, getIndex, isAir;

    private static boolean on() {
        try {
            return ExtraConfig.OCCLUSION_ENABLED.get();
        } catch (Throwable failure) {
            disable(failure);
            return false;
        }
    }

    /** Forget everything: a new world, a logout, or a config change. */
    public static void clear() {
        HORIZON.clear();
        PARENT.clear();
        CORE.clear();
        DISC.clear();
    }

    /** One render pass begins: hand out this pass's allowances. */
    public static void beginFrame() {
        if (unavailable) return;
        try {
            if (!on()) return;
            budget = Math.max(0, ExtraConfig.OCCLUSION_BUDGET.get());
            int allowance = Math.max(0, ExtraConfig.OCCLUSION_PRECAST.get());
            int queued = drain(CORE, allowance);
            queued += drain(DISC, allowance - queued);
            if (++passes % 200 == 0) {
                ClientLevel level = Minecraft.getInstance().level;
                long now = level == null ? 0L : level.getGameTime();
                long ttl = Math.max(1, ExtraConfig.OCCLUSION_HORIZON_TICKS.get());
                HORIZON.entrySet().removeIf(entry -> now - entry.getValue().tick() > ttl * 8L);
                PARENT.entrySet().removeIf(entry -> now - entry.getValue().tick() > ttl * 8L);
            }
            report(queued);
        } catch (Throwable failure) {
            disable(failure);
        }
    }

    /**
     * An explosion is about to happen: have its directions ready before its particles are drawn.
     *
     * <p>Its own core first -- the few cells the blast sits in -- and then the ring it will spread across.
     * A plume that rises afterwards is left to the on-demand path on purpose: it moves, so a horizon cast
     * for it now would be about the wrong direction by the time it matters.
     */
    public static void prepare(Vec3 centre, double radius) {
        if (unavailable || !on()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (centre.distanceTo(camera()) <= vanillaViewDistance()) return;
        double step = Math.max(2.0, radius * 0.35);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                CORE.addLast(new Vec3(centre.x + dx * step, centre.y, centre.z + dz * step));
            }
        }
        int points = 16;
        for (int i = 0; i < points; i++) {
            double angle = i * 2.0 * Math.PI / points;
            DISC.addLast(new Vec3(centre.x + Math.cos(angle) * radius, centre.y,
                    centre.z + Math.sin(angle) * radius));
        }
    }

    /** The vanilla render distance in blocks: the point past which LODs are all we have. */
    public static double vanillaViewDistance() {
        return Math.max(2, Minecraft.getInstance().options.renderDistance().get()) * 16.0;
    }

    /** Is the line from {@code camera} to {@code target} hidden by the distant terrain? */
    public static boolean blocked(Vec3 camera, Vec3 target, int id) {
        if (unavailable || !on()) return false;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        double distance = camera.distanceTo(target);
        if (distance <= vanillaViewDistance()) return false;
        long now = level.getGameTime();
        // Grazing lines of sight are the flicker case: looking along the ground, the ray touches it tens of
        // blocks before the particle, and whether it touches at all flips as the player walks. The error
        // grows with range, so the margin does too.
        double margin = ExtraConfig.OCCLUSION_MARGIN_BLOCKS.get();
        long ttl = Math.max(1, ExtraConfig.OCCLUSION_HORIZON_TICKS.get());
        long key = directionKey(camera, target, distance, now);
        // Where the answer came from, for the log: the cache, a fresh exact cast, or nothing because the
        // per pass budget was already spent.
        String source = "cache";
        Horizon known = fresh(key, now, ttl);
        if (known == null) {
            if (budget > 0) {
                budget--;
                casts++;
                source = "cast";
                known = cast(level, camera, target, key, now, ttl, false);
            } else {
                source = "budget";
            }
        }
        if (known == null) {
            return false;
        }
        // The sign, and only the sign: the blocker has to be in front of the particle.
        //
        // A horizon comparison was tried here and had to be taken out again: the ray is aimed *at the
        // particle, so the first hit lies on that very line of sight and its elevation is the particle's
        // elevation, to within a fraction of a degree. Requiring the particle to be below the line to its own
        // blocker is therefore unsatisfiable, and every particle came back visible. A horizon would have to
        // come from a direction the particle did not choose -- a neighbouring cell, or a coarser level -- which
        // is a different design, not a tweak.
        boolean hidden = known.range() <= distance - margin;
        if (hidden && ExtraConfig.on(ExtraConfig.SHOCKWAVE_DEBUG)) {
        }
        if (ExtraConfig.on(ExtraConfig.SHOCKWAVE_DEBUG)) traceParticle(id, key, hidden, distance, margin, known.range(), source);
        if (hidden) culled++;
        return hidden;
    }

    /**
     * The coarse answer for a direction: the farthest known blocker among the children of its parent.
     *
     * <p>It is cached under the parent's own key, never under the fine one: storing it as the fine cell's
     * answer would replace a real horizon with a deliberately distant one for the whole of its lifetime, and
     * that cell would stop culling anything.
     */

    /**
     * A direction with no answer at all, and data that Voxy cannot show: borrow from its neighbours.
     *
     * <p>This is the case a blast creates. Our own LOD refresh (VoxyLodNudger through LSS) makes Voxy
     * re-ingest the columns around the crater, so for a second or two those sections are not there to read,
     * and a direction that was never cast before has nothing to fall back on. A wall or a ridge spans many
     * cells, so taking the *farthest* known neighbour is both conservative -- it can only hide less than the
     * truth -- and enough to keep a cloud behind it hidden.
     */

    private static long parentKey(long key) {
        long azimuth = (key & 0xFFFFL) >> 1;
        long elevation = ((key >>> 16) & 0xFFFFL) >> 1;
        return (azimuth | (elevation << 15)) | (1L << 62);
    }

    private static Horizon fresh(long key, long now, long ttl) {
        Horizon known = HORIZON.get(key);
        if (known == null || now - known.tick() >= ttl) return null;
        return known;
    }

    /** Cast one direction and record it, with hysteresis on the range. */
    private static Horizon cast(ClientLevel level, Vec3 camera, Vec3 target, long key, long now, long ttl,
                                   boolean preCast) {
        double distance = camera.distanceTo(target);
        // One ray per cell, along the cell's own centre direction instead of along whichever particle
        // happened to ask first. This is what makes a cell's answer a property of the cell: every particle in
        // the cone, at any distance, and the same particle a tick later, read the same measurement. Before
        // this, the stored range was in effect "how far away the first asker was", so every particle at a
        // similar distance to that asker was a coin flip -- which is exactly what the per particle trace showed.
        Vec3 cellDir = cellDirection(key);
        // The ray length is a property of the cell too: the view distance, not the asker's distance.
        double range = Math.max(320.0, vanillaViewDistance() * 2.0);
        // The key is built from a camera snapped to the KEY_GRID lattice, so the ray has to start from that
        // same point. Casting from the true camera aimed the ray up to a degree and a half away at range --
        // wider than a one degree cell -- so a cell's answer was often about a neighbouring direction and
        // mostly came back clear, which is exactly what made the cull look like it had stopped working.
        double found = castRay(level, snappedCamera(camera), cellDir.x, cellDir.y, cellDir.z, range);
        double hitX = lastHitX, hitY = lastHitYWorld, hitZ = lastHitZ;
        Horizon known = HORIZON.get(key);
        if (found == UNKNOWN) {
            // Voxy could not see the whole way -- usually ground that shelling has just rearranged and it has
            // not ingested yet. Not a clear line: keep this direction's previous answer and keep it alive, or
            // every cell would forget its blocker at once and a whole cloud would reappear.
            if (known == null) return null;
            Horizon held = new Horizon(known.range(), known.candidate(), known.repeated(), now, known.hold(),
                    known.hitX(), known.hitY(), known.hitZ());
            HORIZON.put(key, held);
            return held;
        }
        // Hysteresis is deliberately asymmetric: a ray that now finds a blocker closer than before is taken
        // at once (that is the culling we want), while an answer that becomes *clearer* has to repeat, so
        // grazing a ridge cannot pop a cloud back. Symmetric hysteresis had a worse failure: a direction
        // alternating between clear and blocked never repeated twice, so it kept whatever it first
        // answered -- permanently clear, a leak no budget could fix.
        int required = Math.max(1, ExtraConfig.OCCLUSION_HYSTERESIS.get());
        int repeated = known != null && Math.abs(known.candidate() - found) < 1.0E-6 ? known.repeated() + 1 : 1;
        boolean moreBlocking = known == null || found < known.range() - 1.0E-6;
        if (preCast && !moreBlocking) return null;
        // Temporal hold: once a direction has been found blocked, that answer stays for a while even if a
        // later sample says clear. Flying past a smoke layer used to flip such a direction every key grid
        // step, which reads as the whole layer blinking. The hold expires, so a direction can never be
        // stuck hidden -- that was the failure of symmetric hysteresis.
        long holdTicks = Math.max(0, ExtraConfig.OCCLUSION_HOLD_TICKS.get());
        boolean holding = known != null && !moreBlocking && now < known.hold();
        boolean accepted = known == null || moreBlocking || (!holding && repeated >= required);
        long hold = known == null ? (moreBlocking ? now + holdTicks : 0L)
                : (accepted ? (moreBlocking ? now + holdTicks : 0L) : known.hold());
        Horizon answer = new Horizon(accepted ? found : known.range(), found, accepted ? 0 : repeated, now, hold,
                hitX, hitY, hitZ);
        HORIZON.put(key, answer);
        return answer;
    }

    /**
     * Walk one direction and report the range of the first voxel solid enough to count, {@link #UNKNOWN} when
     * part of the way was not available at all, or {@code Double.MAX_VALUE} when the way is genuinely clear.
     */
    /**
     * Walk one direction and return the range of the first solid voxel, {@link #UNKNOWN} when part of the
     * way was unavailable, or {@code Double.MAX_VALUE} when the way is genuinely clear.
     *
     * <p>First hit wins, and that is the whole rule. A visibility ray never needs more than the first thing
     * it meets: if that thing is in front of the particle, the particle is hidden, and if it is behind it,
     * the particle is visible. Earlier versions measured how thick that hit was and threw thin ones away,
     * which cost several times more (the ray had to keep walking) and, because the walk steps further out
     * than it does close by, quietly discarded real distant ground as a "sliver".
     *
     * <p>Two phases, so "first" stays exact to the block: a coarse walk with a step of {@link #COARSE_STEP}
     * blocks finds the neighbourhood of the first non-empty voxel, then a one block walk from just before it
     * pins the exact voxel down. The coarse phase can only overshoot into empty space, never past the answer.
     */
    private static double castRay(ClientLevel clientLevel, Vec3 camera, double dirX, double dirY, double dirZ,
                                  double range) {
        if (!resolve()) return UNKNOWN;
        try {
            Object world = ofEngineNullable.invoke(null, clientLevel);
            if (world == null) return UNKNOWN;
            if (!(Boolean) isLive.invoke(world)) return UNKNOWN;
            rayWorld = world;
            rayIncomplete = false;
            raySamples = 0;
            coarseAcquired = false;
            coarseSamples = 0;
            coarseFirstNonEmpty = -1;
            // Two phases, and the coarse one is only ever a candidate generator.
            //
            // Eight blocks (configurable) at a time, ask the mip cube that contains the sample: a cube is non
            // empty when ANY block inside it is solid, so a one block wall anywhere in it registers. A point
            // sample every eight blocks steps straight over such a wall, which is what once stopped walls
            // blocking at all.
            //
            // "Non empty" only means something is in that box, and that something can be off to the side of the
            // ray, so it is never the answer: every candidate is walked at one block steps and the walk carries on
            // down the ray when the cube holds nothing on the ray itself.
            //
            // Some sections have no coarse level at all -- Voxy allocates mips per section, so a level's presence
            // is discovered at runtime and may differ between two sections of the same world. Where it is absent,
            // solidCubeAt falls back to the exact point, which can only miss what the finest level also misses.
            // This is the rule the whole access layer rests on: no level is ever assumed to exist.
            //
            // The look back is configurable because the right distance depends on the terrain: it covers the cube
            // the sample landed in, plus whatever the previous sample left behind. When the cube before this one
            // held something, the segment between the two samples is walked as well -- a one block wall sitting in
            // that gap is otherwise in no window at all, which showed up as thin rings of leaked particles.
            int coarseStep = Math.max(1, ExtraConfig.OCCLUSION_COARSE_STEP.get());
            int lookBack = Math.max(0, ExtraConfig.OCCLUSION_REFINE_LOOKBACK.get());
            int coarseLast = Math.max(coarseStep, (int) range);
            int previousNonEmpty = -1;
            for (int at = coarseStep; at <= coarseLast; at += coarseStep) {
                boolean cube = solidCubeAt(camera, dirX, dirY, dirZ, at);
                coarseAcquired = coarseAcquired || diagnosticCubeAcquired;
                coarseSamples++;
                int from;
                if (cube) {
                    from = Math.max(1, at - lookBack * coarseStep);
                    previousNonEmpty = at;
                } else if (previousNonEmpty >= 0) {
                    from = previousNonEmpty;
                    previousNonEmpty = -1;
                } else {
                    continue;
                }
                for (int fine = from; fine <= at; fine++) {
                    if (solidAt(camera, dirX, dirY, dirZ, fine)) {
                        int bx = (int) Math.floor(camera.x + dirX * fine);
                        int by = (int) Math.floor(camera.y + dirY * fine);
                        int bz = (int) Math.floor(camera.z + dirZ * fine);
                        lastHitX = bx + 0.5;
                        lastHitYWorld = by + 0.5;
                        lastHitZ = bz + 0.5;
                        lastHitY = by;
                        if (ExtraConfig.on(ExtraConfig.SHOCKWAVE_DEBUG)) logRay(true, fine, bx, by, bz, rayIncomplete);
                        return fine;
                    }
                }
            }
            if (!rayIncomplete) clear++;
            if (ExtraConfig.on(ExtraConfig.SHOCKWAVE_DEBUG)) logRay(false, 0, 0, 0, 0, rayIncomplete);
            return rayIncomplete ? UNKNOWN : Double.MAX_VALUE;
        } catch (Throwable failure) {
            disable(failure);
            return UNKNOWN;
        }
    }

    /** Is the voxel at {@code distance} along the direction solid? Uses the mip level that exists there. */
    private static boolean solidAt(Vec3 camera, double dirX, double dirY, double dirZ, double distance) {
        int bx = (int) Math.floor(camera.x + dirX * distance);
        int by = (int) Math.floor(camera.y + dirY * distance);
        int bz = (int) Math.floor(camera.z + dirZ * distance);
        return solidAt(bx, by, bz);
    }

    /**
     * Does a position that has a fine level ever lack a coarse one? Diagnostic, and the one question that decides
     * whether the coarse phase can be relied on at all.
     *
     * <p>The sibling project's objection is right and worth recording: Voxy builds mips bottom-up --
     * {@code WorldUpdater} derives a parent from a child with a {@code >> 1} shift -- so a position that has level
     * 0 should have every level above it. "Level 3 is missing where level 0 exists" is therefore a statement about
     * <em>residency</em>, not about mip construction: Voxy evicts sections through an LRU and reloads them from
     * storage on demand, and {@code acquireIfExists} answers "can I get it right now", not "was it ever built".
     *
     * <p>This asks every level at the same block position and reports at most once a second, and only when a fine
     * level exists while a coarser one does not. A steady stream of those means the coarse phase falls back far
     * more often than it should and the cause is eviction, not our arithmetic.
     */
    private static void probeLevelHole(int bx, int by, int bz) {
        if (!ExtraConfig.on(ExtraConfig.SHOCKWAVE_DEBUG)) return;
        long now = System.nanoTime();
        if (now - lastLevelProbe < 1_000_000_000L) return;
        lastLevelProbe = now;
        StringBuilder found = new StringBuilder();
        boolean hole = false;
        boolean seen = false;
        for (int lvl = 0; lvl <= MAX_LOD_LAYER; lvl++) {
            Object section = null;
            try {
                int sh = 5 + lvl;
                section = acquireIfExists.invoke(rayWorld, lvl, bx >> sh, by >> sh, bz >> sh);
            } catch (Throwable ignored) {
                // A probe must never take the feature down with it.
            }
            boolean there = section != null;
            if (there) {
                seen = true;
                try {
                    release.invoke(section);
                } catch (Throwable ignored) {
                }
            } else if (seen) {
                hole = true;
            }
            found.append('l').append(lvl).append('=').append(there ? '1' : '0').append(' ');
        }
        if (!hole) return;
        com.mojang.logging.LogUtils.getLogger().info(
                "Sable Light Overhaul voxy hole at ({},{},{}): {} -- a fine level exists and a coarser one does "
                        + "not, which is residency, not mip construction",
                bx, by, bz, found.toString().trim());
    }

    private static long lastLevelProbe;

    /**
     * The one place Voxy is read. A section is held across samples, because acquiring one per sample would
     * dominate everything else. Missing sections fall back to the ordinary world, and if that has no chunk
     * loaded either the answer is recorded as incomplete rather than as air -- air would read as "clear".
     */
    private static boolean solidAt(int bx, int by, int bz) {
        raySamples++;
        try {
            long sectionKey = (((long) (bx >> 5) & 0x3FFFFF)) | (((long) (by >> 5) & 0xFFFFF) << 22)
                    | (((long) (bz >> 5) & 0x3FFFFF) << 42);
            if (sectionKey != rayHeldKey) {
                if (rayHeld != null) release.invoke(rayHeld);
                rayHeld = null;
                rayVoxels = null;
                rayLevel = 0;
                // Finest level that exists at this position, 0 upwards. A level L voxel covers 2^L blocks and a
                // level L section 32 * 2^L, which is why the section coordinate is shifted by 5 + L while the
                // voxel inside it is shifted by L. Voxy allocates mips per section, so what exists here is a
                // property of this position in this world and not of the API: the loop therefore tries every
                // level and takes the first non-null, and the fallback below is the ordinary world.
                for (int lvl = 0; lvl <= MAX_LOD_LAYER && rayHeld == null; lvl++) {
                    int sh = 5 + lvl;
                    rayHeld = acquireIfExists.invoke(rayWorld, lvl, bx >> sh, by >> sh, bz >> sh);
                    if (rayHeld != null) rayLevel = lvl;
                }
                rayHeldKey = sectionKey;
                rayVoxels = rayHeld == null ? null : (long[]) rawData.invoke(rayHeld);
            }
            if (rayVoxels == null) {
                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(bx, by, bz);
                ClientLevel level = Minecraft.getInstance().level;
                if (level == null) {
                    rayIncomplete = true;
                    return false;
                }
                if (!level.getBlockState(pos).isAir()) {
                    LEVELS_USED[0]++;
                    return true;
                }
                rayIncomplete = true;
                return false;
            }
            int index = (Integer) getIndex.invoke(null, (bx >> rayLevel) & 31, (by >> rayLevel) & 31,
                    (bz >> rayLevel) & 31);
            probeLevelHole(bx, by, bz);
            if (Boolean.TRUE.equals(isAir.invoke(null, rayVoxels[index]))) return false;
            LEVELS_USED[rayLevel]++;
            return true;
        } catch (Throwable failure) {
            disable(failure);
            return false;
        }
    }

    /**
     * The same question asked of one mip level instead of the finest one: is the cube of {@code 1 << level}
     * blocks around this point non empty?
     *
     * <p>This is what makes the coarse walk honest. Asking a single point every eight blocks steps straight
     * over a one block wall, and since the fine walk only runs after the coarse walk has found something, the
     * wall simply stopped blocking anything. A mip voxel is non empty when *any* block inside it is solid, so
     * a wall anywhere in the cube registers, and the fine walk then finds it exactly.
     */
    private static boolean solidCubeAt(Vec3 camera, double dirX, double dirY, double dirZ, double distance) {
        diagnosticCubeAcquired = false;
        int bx = (int) Math.floor(camera.x + dirX * distance);
        int by = (int) Math.floor(camera.y + dirY * distance);
        int bz = (int) Math.floor(camera.z + dirZ * distance);
        int sh = 5 + COARSE_LEVEL;
        int cx = bx >> sh, cy = by >> sh, cz = bz >> sh;
        raySamples++;
        try {
            long cubeKey = (((long) cx & 0xFFFFF)) | (((long) cy & 0xFFFFF) << 20) | (((long) cz & 0xFFFFF) << 40);
            if (cubeKey != cubeHeldKey) {
                if (cubeHeld != null) release.invoke(cubeHeld);
                cubeHeld = acquireIfExists.invoke(rayWorld, COARSE_LEVEL, cx, cy, cz);
                diagnosticCubeAcquired = cubeHeld != null;
                cubeHeldKey = cubeKey;
                cubeVoxels = cubeHeld == null ? null : (long[]) rawData.invoke(cubeHeld);
            }
            if (cubeVoxels == null) {
                // No such mip level here: fall back to the exact point, which can only miss what the finest
                // level also misses.
                return solidAt(camera, dirX, dirY, dirZ, distance);
            }
            int idx = (Integer) getIndex.invoke(null, (bx >> COARSE_LEVEL) & 31, (by >> COARSE_LEVEL) & 31,
                    (bz >> COARSE_LEVEL) & 31);
            if (Boolean.TRUE.equals(isAir.invoke(null, cubeVoxels[idx]))) return false;
            LEVELS_USED[COARSE_LEVEL]++;
            if (!coarseAcquired) coarseSamples = 0;
            coarseSamples++;
            if (coarseFirstNonEmpty < 0) coarseFirstNonEmpty = (int) distance;
            return true;
        } catch (Throwable failure) {
            disable(failure);
            return false;
        }
    }

    /** A sample of the rays, rate limited, so a run can be read afterwards. */
    private static void logRay(boolean hit, double at, int bx, int by, int bz, boolean incomplete) {
        long now = System.nanoTime();
        if (now - lastSample < 100_000_000L) return;
        lastSample = now;
        com.mojang.logging.LogUtils.getLogger().info(
                "Sable Light Overhaul ray: hit={} at={} block=({},{},{}) samples={} mip={} incomplete={} | coarseLvl={} acquired={} cubes={} firstNonEmpty={}",
                hit, String.format(java.util.Locale.ROOT, "%.1f", at), bx, by, bz, raySamples, rayLevel,
                incomplete, COARSE_LEVEL, coarseAcquired, coarseSamples, coarseFirstNonEmpty);
    }

    /**
     * World azimuth and elevation of the target, bucketed in degrees -- but never finer than a fixed number
     * of blocks at that distance, or a burst just past the render distance would smear across hundreds of
     * cells while the same burst at range needs a handful.
     */
    /**
     * The centre direction of a cell: the inverse of {@link #directionKey}. Cells are purely angular, so this
     * depends on nothing but the key -- not the camera, not the distance, and not who asked.
     */
    /** The lattice point {@link #directionKey} measured a direction from, so a ray can start in the same place. */
    private static Vec3 snappedCamera(Vec3 camera) {
        return new Vec3(Math.round(camera.x / KEY_GRID) * KEY_GRID,
                Math.round(camera.y / KEY_GRID) * KEY_GRID,
                Math.round(camera.z / KEY_GRID) * KEY_GRID);
    }

    private static Vec3 cellDirection(long key) {
        double cell = Math.toRadians(Math.max(0.1, ExtraConfig.OCCLUSION_CELL_DEGREES.get()));
        double azimuth = ((key & 0xFFFFL) + 0.5) * cell - Math.PI;
        double elevation = (((key >> 16) & 0xFFFFL) + 0.5) * cell - Math.PI / 2.0;
        double cosEl = Math.cos(elevation);
        return new Vec3(cosEl * Math.cos(azimuth), Math.sin(elevation), cosEl * Math.sin(azimuth));
    }

    private static long directionKey(Vec3 camera, Vec3 target, double distance, long now) {
        double baseX = Math.round(camera.x / KEY_GRID) * KEY_GRID;
        double baseY = Math.round(camera.y / KEY_GRID) * KEY_GRID;
        double baseZ = Math.round(camera.z / KEY_GRID) * KEY_GRID;
        double dx = target.x - baseX, dy = target.y - baseY, dz = target.z - baseZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double degrees = ExtraConfig.OCCLUSION_CELL_DEGREES.get();
        // Cells are purely angular: distance is not part of the key, so one cell means one direction
        // for every particle that falls in it, whatever its range.
        double cell = Math.toRadians(Math.max(0.1, degrees));
        long azimuth = ((long) Math.floor((Math.atan2(dz, dx) + Math.PI) / cell)) & 0xFFFFL;
        long elevation = ((long) Math.floor((Math.atan2(dy, horizontal) + Math.PI / 2) / cell)) & 0xFFFFL;
        return azimuth | (elevation << 16);
    }

    private static int drain(Deque<Vec3> queue, int allowance) {
        int done = 0;
        while (done < allowance && !queue.isEmpty()) {
            Vec3 point = queue.pollFirst();
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return done;
            Vec3 camera = camera();
            long now = level.getGameTime();
            long ttl = Math.max(1, ExtraConfig.OCCLUSION_HORIZON_TICKS.get());
            if (point.distanceTo(camera) <= vanillaViewDistance()) continue;
            cast(level, camera, point, directionKey(camera, point, point.distanceTo(camera), now), now, ttl, true);
            done++;
        }
        return done;
    }

    private static Vec3 camera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }

    /**
     * One line a second, and only while the debug switch is on. It used to stay silent no matter what,
     * which left the trend line that is easiest to read -- casts, culled, clear, which mip levels answered --
     * unavailable exactly when it was wanted.
     */
    private static void report(int queued) {
        if (!ExtraConfig.on(ExtraConfig.SHOCKWAVE_DEBUG)) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        if (!GameClock.due(now, lastReport, 20)) return;
        lastReport = now;
        com.mojang.logging.LogUtils.getLogger().info(
                "Sable Light Overhaul occlusion: casts/s={} culled/s={} clear/s={} levels={} table={} budget={} queued={}",
                casts, culled, clear, java.util.Arrays.toString(LEVELS_USED), HORIZON.size(), budget, queued);
        casts = 0;
        culled = 0;
        clear = 0;
        java.util.Arrays.fill(LEVELS_USED, 0);
    }

    private static boolean resolve() {
        if (ofEngineNullable != null) return true;
        if (unavailable) return false;
        try {
            Class<?> identifier = Class.forName("me.cortex.voxy.commonImpl.WorldIdentifier");
            Class<?> engine = Class.forName("me.cortex.voxy.common.world.WorldEngine");
            Class<?> section = Class.forName("me.cortex.voxy.common.world.WorldSection");
            ofEngineNullable = identifier.getMethod("ofEngineNullable", net.minecraft.world.level.Level.class);
            isLive = engine.getMethod("isLive");
            acquireIfExists = engine.getMethod("acquireIfExists", int.class, int.class, int.class, int.class);
            release = section.getMethod("release");
            rawData = section.getMethod("_unsafeGetRawDataArray");
            getIndex = section.getMethod("getIndex", int.class, int.class, int.class);
            isAir = Class.forName("me.cortex.voxy.common.world.other.Mapper").getMethod("isAir", long.class);
            com.mojang.logging.LogUtils.getLogger().info(
                    "Sable Light Overhaul voxy access: {} deg cells, budget {} per pass, beyond {} blocks",
                    ExtraConfig.OCCLUSION_CELL_DEGREES.get(),
                    ExtraConfig.OCCLUSION_BUDGET.get(), (int) vanillaViewDistance());
            // Said out loud, once, because "the occlusion is not running" and "the occlusion has nothing to
            // cull" look identical from the outside. It ran silently for five releases while a null read in
            // this very method threw and was caught below, and the only trace was a log line nobody reads.
            tell("sablelightoverhaul.occlusion.on");
            return true;
        } catch (Throwable failure) {
            disable(failure);
            return false;
        }
    }

    /** At most a handful of these a second, so the log stays readable and the cost stays nil. */
    /**
     * Per particle, not per cell: record the moment a particle's own verdict changes, and which cell it
     * changed in. The per cell trace could not tell "two particles in one cone disagree" (which is correct,
     * a nearer particle really can be visible while a farther one is not) from "one particle oscillates in
     * time" (which is the defect). This one can.
     */
    private static void traceParticle(int id, long key, boolean hidden, double distance, double margin,
                                      double range, String source) {
        Boolean before = LAST_VERDICT.put(id, hidden);
        Long beforeCell = LAST_CELL.put(id, key);
        boolean wasHidden = before != null && before;
        if (before != null && wasHidden == hidden) return;
        long now = System.nanoTime();
        if (now - lastSample < 50_000_000L) return;
        lastSample = now;
        com.mojang.logging.LogUtils.getLogger().info(
                "Sable Light Overhaul particle verdict: id={} {} -> {} sameCell={} cell={} d={} margin={} range={} via={}",
                id, before == null ? "new" : (wasHidden ? "hidden" : "visible"), hidden ? "hidden" : "visible",
                beforeCell != null && beforeCell.longValue() == key, key,
                String.format(java.util.Locale.ROOT, "%.1f", distance),
                String.format(java.util.Locale.ROOT, "%.1f", margin),
                range == Double.MAX_VALUE ? "clear" : String.format(java.util.Locale.ROOT, "%.1f", range), source);
        if (LAST_VERDICT.size() > 8192) { LAST_VERDICT.clear(); LAST_CELL.clear(); }
    }

    /** The last verdict seen for a cell, so a change can be reported with where the new answer came from. */
    private static final Map<Integer, Boolean> LAST_VERDICT = new HashMap<>();
    private static final Map<Integer, Long> LAST_CELL = new HashMap<>();


    private static void disable(Throwable failure) {
        if (unavailable) return;
        unavailable = true;
        // The stack trace, not just the message: when this was a null read in our own code, toString() said
        // nothing a reader could act on, and the whole occlusion system stayed off with no sign of why.
        com.mojang.logging.LogUtils.getLogger().warn(
                "Sable Light Overhaul voxy access disabled ({}): LOD occlusion will not be applied",
                failure.toString(), failure);
        tell("sablelightoverhaul.occlusion.off", failure.toString());
    }
    /** One line in the player's chat, so a state that is otherwise invisible cannot be missed. */
    private static void tell(String key, Object... arguments) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null) return;
        minecraft.player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(key, arguments), false);
    }
}
