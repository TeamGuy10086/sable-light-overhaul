package dev.sablelightoverhaul.munitions;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AltitudeTriggerTest {
    @Test void firesOnlyStrictlyBelowThresholdWhileFalling(){
        int t=100;
        assertTrue(AltitudeTrigger.fires(99.999, -0.001, t));
        assertTrue(AltitudeTrigger.fires(0, -1, t));
        assertTrue(AltitudeTrigger.fires(-5000, -Double.MAX_VALUE, t));
        // Equal is not below.
        assertFalse(AltitudeTrigger.fires(100, -1, t));
        // Above is not below, even while falling.
        assertFalse(AltitudeTrigger.fires(100.001, -1, t));
        // Below but rising (or stationary) must not fire.
        assertFalse(AltitudeTrigger.fires(50, 0, t));
        assertFalse(AltitudeTrigger.fires(50, 0.001, t));
    }

    @Test void negativeThresholdsAndTheirBoundariesBehave(){
        assertTrue(AltitudeTrigger.fires(-64.5, -0.1, -64));
        assertFalse(AltitudeTrigger.fires(-64, -0.1, -64));
        assertFalse(AltitudeTrigger.fires(-63.5, -0.1, -64));
        assertTrue(AltitudeTrigger.fires(AltitudeTrigger.MIN - 1.0, -1, AltitudeTrigger.MIN));
        assertFalse(AltitudeTrigger.fires(AltitudeTrigger.MIN, -1, AltitudeTrigger.MIN));
        assertTrue(AltitudeTrigger.fires(AltitudeTrigger.MAX - 1.0, -1, AltitudeTrigger.MAX));
        assertFalse(AltitudeTrigger.fires(AltitudeTrigger.MAX, -1, AltitudeTrigger.MAX));
        // Out-of-range thresholds are clamped before comparison.
        assertTrue(AltitudeTrigger.fires(-3000, -1, -99999));
        assertFalse(AltitudeTrigger.fires(5000, -1, 99999));
        assertFalse(AltitudeTrigger.fires(5000, -1, -99999));
    }

    @Test void clampCoversBoundsAndOrdinaryValues(){
        assertEquals(AltitudeTrigger.MIN, AltitudeTrigger.clamp(Integer.MIN_VALUE));
        assertEquals(AltitudeTrigger.MIN, AltitudeTrigger.clamp(-2049));
        assertEquals(AltitudeTrigger.MIN, AltitudeTrigger.clamp(AltitudeTrigger.MIN));
        assertEquals(AltitudeTrigger.MAX, AltitudeTrigger.clamp(AltitudeTrigger.MAX));
        assertEquals(AltitudeTrigger.MAX, AltitudeTrigger.clamp(4097));
        assertEquals(AltitudeTrigger.MAX, AltitudeTrigger.clamp(Integer.MAX_VALUE));
        assertEquals(0, AltitudeTrigger.clamp(0));
        assertEquals(-64, AltitudeTrigger.clamp(-64));
        assertEquals(AltitudeTrigger.DEFAULT, AltitudeTrigger.clamp(AltitudeTrigger.DEFAULT));
    }

    @Test void nonFiniteAltitudeOrVelocityNeverFires(){
        for(double y:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertFalse(AltitudeTrigger.fires(y, -1, 100), "y "+y);
        for(double v:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertFalse(AltitudeTrigger.fires(-1, v, 100), "vy "+v);
    }
}
