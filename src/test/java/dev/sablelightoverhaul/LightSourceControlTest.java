package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.LightSourceBlockEntity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stand-in's redstone answer, as arithmetic: three modes, one latch, and edges that only count once.
 *
 * <p>These are the two functions the block entity's tick is built from, kept pure so the answer can be
 * checked without a level. The modes are the ones the page offers: 忽略红石 (always on), 信号使能 (the
 * level is the answer) and 上升沿切换 (an edge flips the latch).
 */
class LightSourceControlTest {
    private static final int IGNORE = LightSourceBlockEntity.IGNORE;
    private static final int LEVEL = LightSourceBlockEntity.LEVEL;
    private static final int EDGE = LightSourceBlockEntity.EDGE;

    /** The default has to leave every lamp that existed before this feature exactly as it was. */
    @Test void ignoringRedstoneIsAlwaysOn() {
        assertTrue(LightSourceBlockEntity.lit(IGNORE, false, 0));
        assertTrue(LightSourceBlockEntity.lit(IGNORE, false, 15));
        // Even a latch that says "off" cannot switch an ignore-mode lamp off: the mode is the answer.
        assertTrue(LightSourceBlockEntity.lit(IGNORE, false, 0));
    }

    @Test void signalEnableFollowsTheLevel() {
        assertFalse(LightSourceBlockEntity.lit(LEVEL, true, 0));
        assertTrue(LightSourceBlockEntity.lit(LEVEL, false, 1));
        assertTrue(LightSourceBlockEntity.lit(LEVEL, false, 15));
        // The latch is not consulted at all in this mode.
        assertTrue(LightSourceBlockEntity.lit(LEVEL, false, 7));
        assertFalse(LightSourceBlockEntity.lit(LEVEL, true, 0));
    }

    @Test void anEdgeFlipsTheLatch() {
        // Unpowered to powered is a rising edge: the latch flips, whichever way it was.
        assertFalse(LightSourceBlockEntity.flipped(true, 15, 0));
        assertTrue(LightSourceBlockEntity.flipped(false, 15, 0));
        // Powered to powered is not an edge, so holding a lever down does not flip once per tick.
        assertTrue(LightSourceBlockEntity.flipped(true, 15, 15));
        assertFalse(LightSourceBlockEntity.flipped(false, 7, 3));
        // Releasing is not an edge either: only the rise counts, which is what makes a button a toggle.
        assertTrue(LightSourceBlockEntity.flipped(true, 0, 15));
        assertFalse(LightSourceBlockEntity.flipped(false, 0, 15));
    }

    /** Walking from unpowered to powered to unpowered to powered toggles twice, and only twice. */
    @Test void aButtonPulsesOncePerPress() {
        boolean latched = true;
        latched = LightSourceBlockEntity.flipped(latched, 15, 0);
        assertFalse(latched, "the first press turns it off");
        latched = LightSourceBlockEntity.flipped(latched, 0, 15);
        assertFalse(latched, "releasing does nothing");
        latched = LightSourceBlockEntity.flipped(latched, 15, 0);
        assertTrue(latched, "the second press turns it back on");
    }

    /**
     * The mode list is what the page shows and what a stored number indexes, so its size is a contract.
     *
     * <p>Three modes, and no fourth: AFN is a source of redstone signal rather than a way of using one
     * (see the source tests below), so a bound channel feeds these three answers instead of replacing
     * them.
     */
    @Test void theModesAreTheThreeThePageOffers() {
        assertTrue(LightSourceBlockEntity.CONTROLS.length == 3);
        assertTrue(IGNORE == 0 && LEVEL == 1 && EDGE == 2);
        assertTrue(LightSourceBlockEntity.CONTROLS[IGNORE].contains("忽略"));
        assertTrue(LightSourceBlockEntity.CONTROLS[LEVEL].contains("信号"));
        assertTrue(LightSourceBlockEntity.CONTROLS[EDGE].contains("上升沿"));
    }

    /** A bound board is where the signal comes from, and it wins over the block's own redstone. */
    @Test void aBoundChannelIsTheSignalAndNothingElseChanges() {
        assertEquals(15, LightSourceBlockEntity.source(true, 15, 0), "the channel's level is the signal");
        assertEquals(0, LightSourceBlockEntity.source(true, 0, 15), "a bound channel's silence is silence");
        assertEquals(7, LightSourceBlockEntity.source(false, 15, 7), "with no board the redstone answers");
    }

    /**
     * A channel's level can be edge-detected, which is what the retired model got wrong.
     *
     * <p>Holding a value is a statement about the value's shape, not about its history: a handle moved
     * from off to on is a rising edge, and the lamp already remembers the previous sample.
     */
    @Test void aHeldChannelStillHasEdges() {
        boolean lit = true;
        lit = LightSourceBlockEntity.flipped(lit, 15, 0);
        assertFalse(lit, "the channel coming up flips a lamp in EDGE mode");
        assertFalse(LightSourceBlockEntity.flipped(lit, 15, 15), "and holding it does not flip it again");
        lit = LightSourceBlockEntity.flipped(lit, 0, 15);
        assertFalse(lit, "going down is not an edge");
        assertTrue(LightSourceBlockEntity.flipped(lit, 15, 0), "coming up again is");
    }

    /** A world written under the old model keeps its behaviour, and its dark edge case is named. */
    @Test void theRetiredChannelModeBecomesTheLevelMode() {
        assertEquals(LEVEL, LightSourceBlockEntity.migrateControl(3),
                "old mode 3 meant 'on while the channel is on', which is now the channel feeding LEVEL");
        assertEquals(IGNORE, LightSourceBlockEntity.migrateControl(IGNORE));
        assertEquals(LEVEL, LightSourceBlockEntity.migrateControl(LEVEL));
        assertEquals(EDGE, LightSourceBlockEntity.migrateControl(EDGE));
        assertEquals(EDGE, LightSourceBlockEntity.migrateControl(99), "anything else is clamped, not trusted");
        assertEquals(IGNORE, LightSourceBlockEntity.migrateControl(-1));
    }
}
