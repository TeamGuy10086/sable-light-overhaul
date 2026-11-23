package dev.sablelightoverhaul;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MissileGlowPolicyTest {
    @Test void frontConeHiddenButSideAndRearVisible() {
        assertFalse(MissileGlowPolicy.visible(1,0,0,100,0,0));
        assertFalse(MissileGlowPolicy.visible(1,0,0,1,1,0));
        assertTrue(MissileGlowPolicy.visible(1,0,0,1,2,0));
        assertTrue(MissileGlowPolicy.visible(1,0,0,0,100,0));
        assertTrue(MissileGlowPolicy.visible(1,0,0,-100,0,0));
    }
    @Test void scaleAndWorldOrientationDoNotChangeCone() {
        assertFalse(MissileGlowPolicy.visible(0,-4,0,0,-100,0));
        assertTrue(MissileGlowPolicy.visible(0,-4,0,0,100,0));
        for(double degrees:new double[]{59.9,60.1,90,180}) {
            double a=Math.toRadians(degrees);
            assertEquals(degrees>60,MissileGlowPolicy.visible(0,0,7,20*Math.sin(a),0,20*Math.cos(a)));
        }
    }
    @Test void invalidOrientationDoesNotProduceUnboundedGlow() {
        assertFalse(MissileGlowPolicy.visible(0,0,0,1,0,0));
        assertFalse(MissileGlowPolicy.visible(Double.NaN,0,0,1,0,0));
        assertFalse(MissileGlowPolicy.visible(1,0,0,0,0,0));
    }
}
