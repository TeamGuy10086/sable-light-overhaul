package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpotShadowDepthTest {

    private static final double RANGE = 16.0;

    @Test
    void zeroSentinelRoundTripsToInfinity() {
        assertEquals(0, SpotShadowDepth.encode(0.0, RANGE));
        assertEquals(0, SpotShadowDepth.encode(-3.5, RANGE));
        assertEquals(Double.POSITIVE_INFINITY, SpotShadowDepth.decode(0, RANGE));
    }

    @Test
    void encodeIsNonDecreasingAndDecodeIsMonotone() {
        int previous = SpotShadowDepth.encode(0.0, RANGE);
        for (int i = 1; i <= 1000; i++) {
            int code = SpotShadowDepth.encode(RANGE * i / 1000.0, RANGE);
            assertTrue(code >= previous, "code decreased at step " + i);
            assertTrue(code >= 1 && code <= SpotShadowDepth.MAX_CODE);
            previous = code;
        }
        double previousDistance = 0.0;
        for (int code = 1; code <= SpotShadowDepth.MAX_CODE; code++) {
            double distance = SpotShadowDepth.decode(code, RANGE);
            assertTrue(distance >= previousDistance, "decode decreased at code " + code);
            previousDistance = distance;
        }
    }

    @Test
    void roundTripErrorIsBoundedByQuantisationStep() {
        double step = RANGE / (2.0 * (SpotShadowDepth.MAX_CODE - 1));
        for (int i = 1; i <= 500; i++) {
            double distance = RANGE * i / 500.0;
            int code = SpotShadowDepth.encode(distance, RANGE);
            assertTrue(code >= 1, "expected hit code for distance " + distance);
            double decoded = SpotShadowDepth.decode(code, RANGE);
            assertTrue(Math.abs(decoded - distance) <= step, "error too large for " + distance);
        }
    }

    @Test
    void maxCodeSurvivesSignedShortRoundTripAsUnsigned() {
        assertEquals(SpotShadowDepth.MAX_CODE, SpotShadowDepth.encode(RANGE * 10.0, RANGE));
        short stored = (short) SpotShadowDepth.MAX_CODE;
        assertTrue(stored < 0, "0xFFFF must occupy the sign bit as a stored short");
        int unsigned = stored & 0xFFFF;
        assertEquals(SpotShadowDepth.MAX_CODE, unsigned);
        assertEquals(RANGE, SpotShadowDepth.decode(unsigned, RANGE), 1e-9);
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SpotShadowDepth.encode(Double.NaN, RANGE));
        assertThrows(IllegalArgumentException.class, () -> SpotShadowDepth.encode(Double.POSITIVE_INFINITY, RANGE));
        assertThrows(IllegalArgumentException.class,
                () -> SpotShadowDepth.encode(Double.NEGATIVE_INFINITY, RANGE));
        assertThrows(IllegalArgumentException.class, () -> SpotShadowDepth.encode(1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> SpotShadowDepth.encode(1.0, -2.0));
        assertThrows(IllegalArgumentException.class, () -> SpotShadowDepth.encode(1.0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> SpotShadowDepth.encode(1.0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> SpotShadowDepth.decode(-1, RANGE));
        assertThrows(IllegalArgumentException.class, () -> SpotShadowDepth.decode(65536, RANGE));
        assertThrows(IllegalArgumentException.class, () -> SpotShadowDepth.decode(1 << 16, RANGE));
        assertThrows(IllegalArgumentException.class, () -> SpotShadowDepth.decode(5, 0.0));
        assertThrows(IllegalArgumentException.class, () -> SpotShadowDepth.decode(5, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> SpotShadowDepth.decode(5, Double.NEGATIVE_INFINITY));
    }
}
