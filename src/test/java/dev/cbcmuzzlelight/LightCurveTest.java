package dev.cbcmuzzlelight;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LightCurveTest {
    @Test void decaySubtractsInitialFractionAndHoldsFloorUntilExpiry() {
        var curve=new LightCurve(20,0.25,0.05);
        assertEquals(1,curve.brightness(0));
        assertEquals(0.5,curve.brightness(10),1e-9);
        assertEquals(0.25,curve.brightness(15),1e-9);
        assertEquals(0.25,curve.brightness(19),1e-9);
        assertEquals(0,curve.brightness(20));
        assertEquals(0,curve.brightness(-1));
    }
    @Test void zeroDecayAndFullMinimumAreValidAndNeverMakeAnImmortalLight() {
        var hold=new LightCurve(60,0,0);
        var fullFloor=new LightCurve(60,1,1);
        assertEquals(1,hold.brightness(59)); assertEquals(0,hold.brightness(60));
        assertEquals(1,fullFloor.brightness(59)); assertEquals(0,fullFloor.brightness(60));
    }
    @Test void maximumDecayAndZeroFloorHaveExplicitBehavior() {
        assertEquals(0,new LightCurve(60,0,1).brightness(1));
        assertEquals(0.25,new LightCurve(60,0.25,1).brightness(1));
        assertEquals(0,new LightCurve(1,1,0).brightness(1));
    }
    @Test void quantizationPreservesAPositiveFloorAndRemovesLightAtExpiry() {
        var curve=new LightCurve(20,0.25,0.05);
        assertEquals(15,curve.blockLevel(15,0));
        assertEquals(8,curve.blockLevel(15,10));
        assertEquals(4,curve.blockLevel(15,19));
        assertEquals(0,curve.blockLevel(15,20));
        assertEquals(1,new LightCurve(20,0.001,1).blockLevel(15,10));
    }
    @Test void invalidValuesCannotReachLightEngine() {
        assertThrows(IllegalArgumentException.class,()->new LightCurve(0,0,0));
        assertThrows(IllegalArgumentException.class,()->new LightCurve(1201,0,0));
        for(double value:new double[]{-0.01,1.01,Double.NaN,Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->new LightCurve(20,value,0));
            assertThrows(IllegalArgumentException.class,()->new LightCurve(20,0,value));
        }
    }
}
