package dev.sablelightoverhaul.profiles;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two rules the targeting has to hold: a mount within ten steps is found, and the fill never leaves the
 * frame it was given.
 */
class ProfileTargetingTest {
    private static final class Grid implements ProfileTargeting.Frame {
        final Set<BlockPos> mounts = new HashSet<>();
        final Set<BlockPos> forbidden = new HashSet<>();
        final Set<BlockPos> outside = new HashSet<>();

        @Override public boolean isCannonMount(BlockPos pos) {
            return mounts.contains(pos);
        }

        @Override public boolean canEnter(BlockPos pos) {
            if (outside.contains(pos)) throw new IllegalStateException("unloaded");
            return !forbidden.contains(pos);
        }
    }

    @Test void lookingStraightAtTheMountFindsIt() {
        var frame = new Grid();
        frame.mounts.add(BlockPos.ZERO);
        assertEquals(BlockPos.ZERO, ProfileTargeting.nearest(BlockPos.ZERO, frame).orElseThrow());
    }

    @Test void theNearestMountWins() {
        var frame = new Grid();
        frame.mounts.add(new BlockPos(5, 0, 0));
        frame.mounts.add(new BlockPos(2, 0, 0));
        assertEquals(new BlockPos(2, 0, 0), ProfileTargeting.nearest(BlockPos.ZERO, frame).orElseThrow());
    }

    @Test void aMountAroundACornerIsStillFound() {
        var frame = new Grid();
        // A wall in the way at x=2, with a gap at z=-1: the fill has to go around rather than through.
        frame.forbidden.add(new BlockPos(2, 0, 0));
        frame.mounts.add(new BlockPos(3, 0, 0));
        assertEquals(new BlockPos(3, 0, 0), ProfileTargeting.nearest(BlockPos.ZERO, frame).orElseThrow());
    }

    @Test void nothingBeyondTenSteps() {
        var frame = new Grid();
        frame.mounts.add(new BlockPos(11, 0, 0));
        assertTrue(ProfileTargeting.nearest(BlockPos.ZERO, frame).isEmpty(),
                "a mount eleven steps away is not something the player is pointing at");
        frame.mounts.clear();
        frame.mounts.add(new BlockPos(10, 0, 0));
        assertEquals(new BlockPos(10, 0, 0), ProfileTargeting.nearest(BlockPos.ZERO, frame).orElseThrow(),
                "ten steps is inside the limit");
    }

    @Test void anUnreachableMountIsNotFoundEvenWhenItIsClose() {
        var frame = new Grid();
        // A wall across the whole search volume at x=3, so nothing east of it can be reached on foot. A cannon
        // block is solid, so "cannot step into it" can never be the reason a mount is missed -- reachability is.
        for (int y = -12; y <= 12; y++) {
            for (int z = -12; z <= 12; z++) frame.forbidden.add(new BlockPos(3, y, z));
        }
        frame.mounts.add(new BlockPos(4, 0, 0));
        assertTrue(ProfileTargeting.nearest(BlockPos.ZERO, frame).isEmpty(),
                "a mount nothing can reach is not one the player is pointing at");

        // One gap in the wall, and the same mount is found by going through it.
        frame.forbidden.remove(new BlockPos(3, 0, 1));
        assertEquals(new BlockPos(4, 0, 0), ProfileTargeting.nearest(BlockPos.ZERO, frame).orElseThrow());
    }

    @Test void anUnanswerablePositionEndsThatBranchAndNotTheSearch() {
        var frame = new Grid();
        // The straight line east is unloaded; the mount sits one step north instead.
        for (int x = 1; x <= 4; x++) frame.outside.add(new BlockPos(x, 0, 0));
        frame.mounts.add(new BlockPos(0, 0, 1));
        assertEquals(new BlockPos(0, 0, 1), ProfileTargeting.nearest(BlockPos.ZERO, frame).orElseThrow());
    }

    @Test void nothingAtAllIsAnEmptyAnswer() {
        assertTrue(ProfileTargeting.nearest(BlockPos.ZERO, new Grid()).isEmpty());
        assertTrue(ProfileTargeting.nearest(null, new Grid()).isEmpty());
        assertTrue(ProfileTargeting.nearest(BlockPos.ZERO, null).isEmpty());
    }

    @Test void aPlainClickOnlyAnswersForTheBlockClicked() {
        var frame = new Grid();
        frame.mounts.add(new BlockPos(0, 0, 1));
        assertTrue(ProfileTargeting.locate(frame, BlockPos.ZERO, false).isEmpty(),
                "pointing at a wall is not asking about the cannon behind it");
        assertEquals(new BlockPos(0, 0, 1), ProfileTargeting.locate(frame, new BlockPos(0, 0, 1), false).orElseThrow(),
                "pointing at the mount itself is the plain case and needs no search at all");
    }

    @Test void aShiftClickAsksForTheNearestMountInstead() {
        var frame = new Grid();
        frame.mounts.add(new BlockPos(0, 0, 1));
        assertEquals(new BlockPos(0, 0, 1), ProfileTargeting.locate(frame, BlockPos.ZERO, true).orElseThrow());
        assertTrue(ProfileTargeting.locate(new Grid(), BlockPos.ZERO, true).isEmpty());
    }

    @Test void locateWithNothingToAskAboutIsEmpty() {
        assertTrue(ProfileTargeting.locate(null, BlockPos.ZERO, true).isEmpty());
        assertTrue(ProfileTargeting.locate(null, BlockPos.ZERO, false).isEmpty());
        assertTrue(ProfileTargeting.locate(new Grid(), null, true).isEmpty());
    }
}
