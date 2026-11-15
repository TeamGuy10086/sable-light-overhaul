package dev.sablelightoverhaul.profiles;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * Finding the cannon mount a player is looking at.
 *
 * <p>The command's question is "which cannon does the player mean", and the answer is the nearest mount to
 * where their line of sight lands, found by flood fill. Two rules come from the owner and both are load
 * bearing:
 *
 * <ul>
 *   <li><b>At most ten steps.</b> A mount is something you are pointing at, not something you are near: a
 *       search that wandered further would start configuring cannons on the other side of a wall.</li>
 *   <li><b>Never across bodies.</b> If the aim lands in the world, the search stays in the world; if it lands
 *       on a ship, the search stays on that ship. Crossing would let a cannon on a deck answer for a spot in
 *       the sea below it, and the two are different frames with different coordinates -- there is no
 *       meaningful distance between them to be "nearest" in.</li>
 * </ul>
 *
 * <p>Both rules are the caller's to enforce through {@link Frame}, because only the caller knows what a body
 * is and where its coordinates live. This class does the walking: a breadth-first fill outward from the aim
 * point, so the first mount it reaches is the nearest one in steps.
 *
 * <p>Pure, so the interesting cases -- the mount is the aim itself, the mount is around a corner, the frame
 * refuses to be entered -- are testable without a world.
 */
public final class ProfileTargeting {
    /** How far the fill may spread. Ten steps, as specified. */
    public static final int MAX_STEPS = 10;

    /**
     * What the fill may ask, and nothing else.
     *
     * <p>{@code canEnter} is also where "never across bodies" lives: a frame that includes the whole level
     * would let the search cross, which is the caller's mistake to avoid, not this class's to guess at.
     */
    public interface Frame {
        /** True when this position is a cannon mount, which is what the search is looking for. */
        boolean isCannonMount(BlockPos pos);

        /** True when the fill may step into this position at all. */
        boolean canEnter(BlockPos pos);
    }

    private ProfileTargeting() {}

    /**
     * Which cannon an item click means.
     *
     * <p>Two clicks, two questions, and the difference matters. A plain right-click asks about the block that
     * was clicked and nothing else: if that block is not a mount then the answer is no, because a player
     * pointing at a wall is not asking about the cannon behind it. A shift right-click asks for the nearest
     * mount, which is the forgiving version of the same question and the only one that needs the fill.
     */
    public static Optional<BlockPos> locate(Frame frame, BlockPos clicked, boolean search) {
        if (frame == null || clicked == null) return Optional.empty();
        try {
            if (frame.isCannonMount(clicked)) return Optional.of(clicked.immutable());
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
        return search ? nearest(clicked, frame) : Optional.empty();
    }

    /**
     * The nearest mount to {@code aim}, or empty when there is none within {@link #MAX_STEPS}.
     *
     * <p>The aim point itself is tested first, so looking straight at a mount always finds that one. Any
     * failure inside the frame -- an unloaded chunk, a body that has gone -- ends that branch of the fill
     * rather than the search, because one unanswerable position says nothing about its neighbours.
     */
    public static Optional<BlockPos> nearest(BlockPos aim, Frame frame) {
        if (aim == null || frame == null) return Optional.empty();
        var start = aim.immutable();
        try {
            if (frame.isCannonMount(start)) return Optional.of(start);
            if (!frame.canEnter(start)) return Optional.empty();
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
        var seen = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        var steps = new ArrayDeque<Integer>();
        seen.add(start);
        queue.add(start);
        steps.add(0);
        while (!queue.isEmpty()) {
            var here = queue.poll();
            var distance = steps.poll();
            if (distance >= MAX_STEPS) continue;
            for (var next : new BlockPos[]{
                    here.offset(1, 0, 0), here.offset(-1, 0, 0),
                    here.offset(0, 1, 0), here.offset(0, -1, 0),
                    here.offset(0, 0, 1), here.offset(0, 0, -1)}) {
                if (!seen.add(next)) continue;
                try {
                    if (frame.isCannonMount(next)) return Optional.of(next);
                    if (!frame.canEnter(next)) continue;
                } catch (RuntimeException failure) {
                    // That branch stops here; the rest of the fill carries on.
                    continue;
                }
                queue.add(next);
                steps.add(distance + 1);
            }
        }
        return Optional.empty();
    }
}
