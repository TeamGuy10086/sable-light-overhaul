package dev.sablelightoverhaul.client;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The per-frame table of body rotations the shadow cones are measured in.
 *
 * <p>What matters: one slot per hull however many lamps it carries (which is why eight is plenty), a
 * hull that leaves the view releasing its slot, and overflow falling back to the world frame rather than
 * to somebody else's rotation -- a cone tested against the wrong hull is worse than one that does not
 * turn, because it would point somewhere plausible.
 */
class BodyFramesTest {
    private static final float[] TURNED = {0.0F, 0.7071F, 0.0F, 0.7071F};

    @BeforeEach
    void reset() {
        BodyFrames.begin();
    }

    @Test
    void oneSlotPerHullHoweverManyLampsItCarries() {
        UUID hull = UUID.randomUUID();
        assertEquals(1, BodyFrames.slot(hull, TURNED), "slot 0 is the world, so the first hull takes 1");
        assertEquals(1, BodyFrames.slot(hull, TURNED), "and every lamp on it shares that slot");
        assertEquals(1, BodyFrames.used());
        assertEquals(TURNED[1], BodyFrames.table()[4 + 1], 1e-6, "the quaternion is stored at its slot");
    }

    @Test
    void theWorldIsSlotZeroAndIsNeverAssigned() {
        // Slot 0 is the identity by construction: a hull must not land on it, or every lamp with no hull
        // would silently inherit that hull's rotation.
        UUID hull = UUID.randomUUID();
        assertEquals(1, BodyFrames.slot(hull, TURNED), "the first hull goes to slot 1, not 0");
        assertEquals(0.0F, BodyFrames.table()[0], 1e-6);
        assertEquals(1.0F, BodyFrames.table()[3], 1e-6, "slot 0 stays the identity");
        assertEquals(0, BodyFrames.slot(null, TURNED), "no hull is the world frame");
        assertEquals(0, BodyFrames.slot(hull, null), "no rotation is the world frame");
    }

    @Test
    void aNewFrameReleasesTheHullsThatLeft() {
        UUID first = UUID.randomUUID();
        assertEquals(1, BodyFrames.slot(first, TURNED));
        BodyFrames.begin();
        assertEquals(0, BodyFrames.used());
        UUID second = UUID.randomUUID();
        assertEquals(1, BodyFrames.slot(second, TURNED), "the slot is free again, not leaked");
    }

    @Test
    void hullsBeyondTheTableFallBackToTheWorldAndAreCounted() {
        for (int i = 0; i < BodyFrames.MAX - 1; i++) {
            assertEquals(i + 1, BodyFrames.slot(UUID.randomUUID(), TURNED), "hull " + i);
        }
        assertEquals(BodyFrames.MAX - 1, BodyFrames.used());
        UUID tooMany = UUID.randomUUID();
        assertEquals(0, BodyFrames.slot(tooMany, TURNED), "the world frame, never another hull's");
        assertEquals(1, BodyFrames.spilled(), "and it is counted rather than hidden");
        assertEquals(0, BodyFrames.slot(tooMany, TURNED), "still counted once per hull, not per lamp");
        assertEquals(1, BodyFrames.spilled());
    }
}
