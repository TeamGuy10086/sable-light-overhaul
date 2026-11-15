package dev.sablelightoverhaul.client;

import org.junit.jupiter.api.Test;

import static dev.sablelightoverhaul.client.ParticleWind.Mode.FIXED;
import static dev.sablelightoverhaul.client.ParticleWind.Mode.VARYING;
import static org.junit.jupiter.api.Assertions.*;

class ParticleWindTest {
    @Test void fixedWindUsesWorldHeadingAndBlocksPerSecond() {
        var north = ParticleWind.sample(FIXED, 0, 4, 120, 0);
        var east = ParticleWind.sample(FIXED, 90, 4, 120, 680);
        assertEquals(0, north.x(), 1e-12);
        assertEquals(-.2, north.z(), 1e-12);
        assertEquals(.2, east.x(), 1e-12);
        assertEquals(0, east.z(), 1e-12);
        assertEquals(0, east.y(), 1e-12);
        assertEquals(north, ParticleWind.sample(FIXED, 0, 4, 120, 680));
    }

    @Test void varyingWindChangesSmoothlyAndRepeatsWithoutADiscontinuity() {
        int period = 120;
        var first = ParticleWind.sample(VARYING, 0, 4, period, 0);
        var quarter = ParticleWind.sample(VARYING, 0, 4, period, period * 5L);
        var last = ParticleWind.sample(VARYING, 0, 4, period, period * 20L - 1);
        assertTrue(quarter.x() > .05);
        assertTrue(Math.abs(quarter.length() - first.length()) > .01);
        assertTrue(last.subtract(first).length() < .001);
        assertEquals(first, ParticleWind.sample(VARYING, 0, 4, period, period * 20L));
        assertEquals(0, ParticleWind.sample(VARYING, 270, 0, period, 13).length(), 0);
    }
}
