package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure helper tests for the tank cannon trail start sphere; no game objects are involved. */
class TrailStartDistanceTest {
    @Test
    void uncapturedFailsOpen() {
        TrailStartDistance gate = new TrailStartDistance();
        assertFalse(gate.isCaptured());
        assertFalse(gate.suppress(4, 0, 0, 0));
        assertFalse(gate.suppress(4, 1, 1, 1));
    }

    @Test
    void startZeroIsDisabledEvenAtOrigin() {
        TrailStartDistance gate = new TrailStartDistance();
        gate.capture(5, 6, 7);
        assertFalse(gate.suppress(0, 5, 6, 7));
        assertFalse(gate.suppress(-1, 5, 6, 7));
    }

    @Test
    void strictlyBeforeThresholdSuppressed() {
        TrailStartDistance gate = new TrailStartDistance();
        gate.capture(0, 0, 0);
        assertTrue(gate.suppress(3, 2.9, 0, 0));
        assertTrue(gate.suppress(3, 0, 0, 0));
        assertFalse(gate.suppress(3, 3.0, 0, 0));
        assertFalse(gate.suppress(3, 3.1, 0, 0));
    }

    @Test
    void exactlyOnThresholdNotSuppressed() {
        TrailStartDistance gate = new TrailStartDistance();
        gate.capture(10, 20, 30);
        assertFalse(gate.suppress(5, 15, 20, 30));
        assertTrue(gate.suppress(5, 14.9, 20, 30));
    }

    @Test
    void firstOriginIsStable() {
        TrailStartDistance gate = new TrailStartDistance();
        gate.capture(0, 0, 0);
        gate.capture(100, 100, 100);
        assertTrue(gate.suppress(2, 1, 0, 0));
        assertFalse(gate.suppress(2, 101, 100, 100));
    }

    @Test
    void diagonalDistanceIsThreeDimensional() {
        TrailStartDistance gate = new TrailStartDistance();
        gate.capture(0, 0, 0);
        // (1,2,2) has length 3.
        assertFalse(gate.suppress(3, 1, 2, 2));
        assertTrue(gate.suppress(3.01, 1, 2, 2));
        assertTrue(gate.suppress(3, 1, 2, 1.9));
    }

    @Test
    void rapidFirstSegmentOnlySuppressesInsideSphere() {
        TrailStartDistance gate = new TrailStartDistance();
        gate.capture(0, 0, 0);
        // Samples 0..8 along +X while the round ends at 10: only 0 and 2 are inside radius 3.
        double[] samples = {0, 2, 4, 6, 8};
        boolean[] expected = {true, true, false, false, false};
        for (int i = 0; i < samples.length; i++) {
            boolean suppressed = gate.suppress(3, samples[i], 0, 0);
            assertTrue(expected[i] == suppressed, "sample " + samples[i]);
        }
    }

    @Test
    void translationInvariant() {
        double ox = -1000, oy = 64, oz = 2500;
        double shiftX = 1234.5, shiftY = -17, shiftZ = 8000;
        double start = 2.5;
        TrailStartDistance gate = new TrailStartDistance();
        gate.capture(ox, oy, oz);
        TrailStartDistance shifted = new TrailStartDistance();
        shifted.capture(ox + shiftX, oy + shiftY, oz + shiftZ);
        double[][] offsets = {{0, 0, 0}, {1, 0, 0}, {1.5, 0, 0}, {2, 0, 0}, {3, 0, 0}, {1, 1, 1}};
        for (double[] o : offsets) {
            boolean near = gate.suppress(start, ox + o[0], oy + o[1], oz + o[2]);
            boolean far = shifted.suppress(start, ox + shiftX + o[0], oy + shiftY + o[1], oz + shiftZ + o[2]);
            assertTrue(near == far, "offset " + o[0] + "," + o[1] + "," + o[2]);
        }
    }
}
