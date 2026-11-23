package dev.sablelightoverhaul.effects;

import dev.sablelightoverhaul.ShockwavePayload;
import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.lighting.LightingCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Turns one explosion into the one {@link EffectApi.ImpactEvent} everybody reads.
 *
 * <p>Before this, "which explosions count" was decided across five places inside a single event handler, in
 * one order, where any check returning early silently took the rest with it -- which is why some explosions
 * lit up and threw a wave while others did nothing at all, with no way to see which of the five had stopped
 * them. The judgement is now here, once, and the answers to the two questions that used to be tangled
 * together are kept apart:
 *
 * <ul>
 *   <li><b>Is this an explosion we can describe?</b> -- the type guard and the measurement. Only this can
 *       produce no event at all, and only for an explosion that did not happen on a server level.</li>
 *   <li><b>Is it big enough for this mod to draw?</b> -- {@link #worthDrawing}. That is a policy about
 *       <em>our</em> effects, so it belongs to our listeners, not to the event: a third party that wants to
 *       draw something for a grenade now can, and our own anti-strobe threshold still holds for ours.</li>
 * </ul>
 *
 * <p>The measurement itself is not re-run: vanilla has already cast the blast's rays, so the farthest block
 * per octant is read out of the block list the event carries. See {@link ShockwavePayload#measure}.
 */
public final class ImpactClassifier {
    private ImpactClassifier() {}

    /**
     * Measures one explosion, or returns null when there is nothing to describe.
     *
     * <p>The null case is narrow on purpose. Everything below the "is it big enough" question is answered on
     * the event, so the event exists for every explosion and the size policy is the listener's business.
     */
    public static @Nullable EffectApi.ImpactEvent of(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return null;
        var explosion = event.getExplosion();
        var blocks = event.getAffectedBlocks();
        Vec3 center = explosion.center();
        // The charge itself, when the explosion has one. CBC's CustomExplosion passes its
        // block_damaging_explosive_power straight into the vanilla constructor, so this is the shell's own
        // number and does not change with what it hit; a plain vanilla blast has none and takes the floor.
        float yield = explosion.radius();
        if (!(yield > ShockwavePayload.FALLBACK_BELOW)) yield = ShockwavePayload.DEFAULT_YIELD;
        // How far the blast actually got, per octant: measured from the blocks vanilla's own rays walked
        // through, so it already carries the resistance of everything that was in the way.
        float[] reach = ShockwavePayload.measure(center, yield, blocks);
        float solidity = ShockwavePayload.solidity(yield, reach);
        int tier = tierOf(yield);
        ShockwavePayload.BlastClass blastClass = classify(level, center, reach, solidity);
        var source = explosion.getDirectSourceEntity();
        var impact = new EffectApi.ImpactEvent(level, center, null, yield, solidity, reach, blocks.size(),
                ShockwavePayload.toHitClass(blastClass), tier, source, null, DefaultSpecs.forImpact(),
                EffectApi.Cancellation.none());
        report(level, event, impact, blastClass);
        return impact;
    }

    /**
     * Whether this explosion is big enough for this mod's own effects.
     *
     * <p>Two independent signals of "how big was this", because not every source has CBC's charge data: a
     * vanilla TNT or another mod's blast only has a radius, and it may well break fewer than 200 blocks.
     * Either signal being big enough draws the event. A grenade or a creeper satisfies neither, so a
     * firefight still does not strobe.
     *
     * <p>The gate is on "was this a real explosion", not on "did it destroy much": a shell that hits
     * something indestructible reports no affected blocks at all, and it still flashed and threw a wave.
     *
     * <p>This is a policy about <em>our</em> defaults and says nothing about the event; a third party is free
     * to ignore it. It is one function so that the two places that apply it -- the light and the shockwave --
     * cannot drift apart.
     */
    public static boolean worthDrawing(EffectApi.ImpactEvent impact) {
        return impact.yield() >= LightingCommon.SHOCKWAVE_MIN_POWER.get().floatValue()
                || impact.affectedBlocks() >= LightingCommon.EXPL_MIN_BLOCKS.get();
    }

    /** Where the charge went off, from the world around it and from what the blast measured. */
    static ShockwavePayload.BlastClass classify(ServerLevel level, Vec3 center, float[] reach, float solidity) {
        int depth = LightingCommon.SHOCKWAVE_BURIED_DEPTH.get();
        boolean materialAbove = false;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= depth && !materialAbove; dy++) {
            probe.set((int) Math.floor(center.x), (int) Math.floor(center.y) + dy, (int) Math.floor(center.z));
            if (!level.hasChunkAt(probe)) break;
            materialAbove = !level.getBlockState(probe).getCollisionShape(level, probe).isEmpty();
        }
        double surfaceDelta = center.y - level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                (int) Math.floor(center.x), (int) Math.floor(center.z));
        return classify(materialAbove, surfaceDelta, downUpRatio(reach), solidity,
                LightingCommon.SHOCKWAVE_GROUND_BAND.get(), LightingCommon.SHOCKWAVE_BURIED_SOLIDITY.get(),
                LightingCommon.SHOCKWAVE_DOWN_UP.get(), LightingCommon.SHOCKWAVE_AIR_BAND.get());
    }

    /**
     * The pure decision, so the thresholds and the order they are applied in can be tested.
     *
     * <p>Two reads of the world decide the hard case. Material directly above the centre means the charge was
     * under something, so it is {@link ShockwavePayload.BlastClass#BURIED}. Otherwise its height over the
     * local surface tells a ground burst from an air burst -- and a <b>crater</b> reads as a ground burst for
     * free, because by the time the event fires the blast has already dug the floor down, so the heightmap is
     * the crater floor and the sky above it is empty.
     *
     * <p>{@code downUp} cross-checks it: a ground burst is stopped downwards while an air burst is not, so the
     * ratio of what the four downward octants reached to what the four upward ones did is small on the ground
     * and near one in the air.
     */
    public static ShockwavePayload.BlastClass classify(boolean materialAbove, double surfaceDelta, double downUp,
                                                      double solidity, double groundBand, double buriedSolidity,
                                                      double downUpLimit, double airBand) {
        if (materialAbove || solidity >= buriedSolidity) return ShockwavePayload.BlastClass.BURIED;
        if (surfaceDelta > airBand) return ShockwavePayload.BlastClass.AIR;
        if (surfaceDelta <= groundBand || downUp <= downUpLimit) return ShockwavePayload.BlastClass.GROUND;
        return ShockwavePayload.BlastClass.AIR;
    }

    /** What the downward octants reached over what the upward ones did; 1 when the blast was unobstructed. */
    public static double downUpRatio(float[] reach) {
        double down = 0.0, up = 0.0;
        int downCount = 0, upCount = 0;
        for (int i = 0; i < reach.length && i < ShockwavePayload.OCTANTS; i++) {
            if ((i & 2) == 0) { down += reach[i]; downCount++; } else { up += reach[i]; upCount++; }
        }
        double downMean = downCount == 0 ? 0.0 : down / downCount;
        double upMean = upCount == 0 ? 0.0 : up / upCount;
        return upMean <= 1.0E-4 ? 1.0 : downMean / upMean;
    }

    /** The shell's own charge decides the tier, whatever it happened to hit. */
    static int tierOf(float yield) {
        return tierOf(yield, LightingCommon.EXPL_YIELD_TIERS.get());
    }

    public static int tierOf(float yield, java.util.List<? extends Integer> thresholds) {
        int tier = 1;
        for (int i = 0; i < thresholds.size(); i++) if (yield >= thresholds.get(i)) tier = i + 2;
        return Math.min(tier, 6);
    }

    // ------------------------------------------------------------------------------------------------
    // Diagnostics. Gated on the same switch and written for the same explosions as before, so the log a
    // test matrix reads does not change shape when behaviour does not either.
    // ------------------------------------------------------------------------------------------------

    private static void report(ServerLevel level, ExplosionEvent.Detonate event, EffectApi.ImpactEvent impact,
                               ShockwavePayload.BlastClass blastClass) {
        if (!LightingCommon.on(LightingCommon.GEN_DEBUG) || !worthDrawing(impact)) return;
        var logger = com.mojang.logging.LogUtils.getLogger();
        int blocks = impact.affectedBlocks();
        logger.info("Sable Light Overhaul blast: class={} blocks={} surfaceDelta={} downUp={} solidity={}",
                blastClass, blocks,
                fmt(impact.center().y - level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        (int) Math.floor(impact.center().x), (int) Math.floor(impact.center().z))),
                fmt(downUpRatio(impact.reach())), fmt(impact.solidity()));
        // One line per explosion: what kind of explosion it was, what the game let it do, what the block
        // list actually contains, and how many viewers were told. This is the line a test matrix reads.
        var blocksList = event.getAffectedBlocks();
        double minDistance = Double.MAX_VALUE, maxDistance = 0.0, maxAbove = 0.0, maxBelow = 0.0;
        int[] perOctant = new int[ShockwavePayload.OCTANTS];
        int solid = 0;
        for (var pos : blocksList) {
            double dx = pos.getX() + 0.5 - impact.center().x, dy = pos.getY() + 0.5 - impact.center().y,
                    dz = pos.getZ() + 0.5 - impact.center().z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            minDistance = Math.min(minDistance, distance);
            maxDistance = Math.max(maxDistance, distance);
            maxAbove = Math.max(maxAbove, dy);
            maxBelow = Math.max(maxBelow, -dy);
            int octant = ShockwavePayload.octant(dx, dy, dz);
            if (octant >= 0) perOctant[octant]++;
            if (!level.getBlockState(pos).isAir()) solid++;
        }
        if (blocksList.isEmpty()) minDistance = 0.0;
        var probeSource = event.getExplosion().getDirectSourceEntity();
        double range = LightingCommon.SHOCKWAVE_RANGE.get();
        logger.info(
                "Sable Light Overhaul probe: explosion={} power={} blockInteraction={} sourceEntity={} | blocks={} distance[min/max]={}/{} above/below={}/{} stillSolid={} perOctant={} | viewers={}",
                event.getExplosion().getClass().getSimpleName(), fmt(event.getExplosion().radius()),
                event.getExplosion().getBlockInteraction(),
                probeSource == null ? "none" : probeSource.getType().toString(),
                blocksList.size(), fmt(minDistance), fmt(maxDistance), fmt(maxAbove), fmt(maxBelow), solid,
                java.util.Arrays.toString(perOctant),
                level.players().stream().filter(p -> p.distanceToSqr(impact.center()) <= range * range).count());
        logger.info("Sable Light Overhaul explosion: blocks={} yield={} solidity={} tier={} class={} reach[min/mean/max]={}/{}/{} at {}",
                blocks, fmt(impact.yield()), fmt(impact.solidity()), impact.tier(), blastClass,
                fmt(minOf(impact.reach())), fmt(ShockwavePayload.reachMean(impact.reach())),
                fmt(maxOf(impact.reach())), impact.center());
    }

    private static float minOf(float[] values) {
        float min = Float.MAX_VALUE;
        for (float v : values) min = Math.min(min, v);
        return min;
    }

    private static float maxOf(float[] values) {
        float max = 0.0F;
        for (float v : values) max = Math.max(max, v);
        return max;
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
