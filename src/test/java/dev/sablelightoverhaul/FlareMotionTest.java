package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlareMotionTest {

    @Test
    void descendsExactlyOneBlockPerSecond() {
        assertEquals(64.0, FlareMotion.height(64.0, 0.0), 0.0);
        assertEquals(62.5, FlareMotion.height(64.0, 1.5), 1e-12);
        assertEquals(63.75, FlareMotion.height(64.0, 0.25), 1e-12);
        assertEquals(60.0, FlareMotion.height(64.0, 4.0), 1e-12);
    }

    @Test
    void lifecycleBoundaries() {
        double life = 5.0;
        assertTrue(FlareMotion.alive(0.0, life));
        assertTrue(FlareMotion.alive(4.999, life));
        assertFalse(FlareMotion.alive(life, life));
        assertFalse(FlareMotion.alive(-1e-9, life));
        assertEquals(0.0, FlareMotion.brightness(7L, -0.001, life), 0.0);
        assertEquals(0.0, FlareMotion.brightness(7L, life, life), 0.0);
        assertTrue(FlareMotion.brightness(7L, 0.0, life) > 0.8);
    }

    @Test
    void sampledBrightnessBoundedBeforeFade() {
        for (long seed = 0; seed < 40; seed++) {
            double life = 10.0;
            double fadeStart = life - 0.7;
            for (double t = 0.0; t <= fadeStart; t += 0.01) {
                double b = FlareMotion.brightness(seed * 131L, t, life);
                assertTrue(b >= 0.82 - 1e-12 && b <= 1.15 + 1e-12, "seed " + seed + " t " + t + " b " + b);
            }
            double fadeMid = life - 0.35;
            assertTrue(FlareMotion.brightness(seed, fadeMid, life) < 1.15);
            assertTrue(FlareMotion.brightness(seed, life - 1e-9, life) < 0.01);
        }
    }

    @Test
    void deterministicAndSeedDependent() {
        double life = 3.0;
        for (double t = 0.0; t < life; t += 0.137) {
            assertEquals(FlareMotion.brightness(42L, t, life), FlareMotion.brightness(42L, t, life), 0.0);
        }
        boolean differs = false;
        for (double t = 0.0; t < life - 0.7; t += 0.05) {
            if (Math.abs(FlareMotion.brightness(1L, t, life) - FlareMotion.brightness(2L, t, life)) > 1e-6) {
                differs = true;
                break;
            }
        }
        assertTrue(differs, "different seeds should produce different flicker");
        double a = FlareMotion.brightness(-99L, 1.25, life);
        double b = FlareMotion.brightness(-98L, 1.25, life);
        assertNotEquals(a, b, 0.0);
    }

    @Test
    void continuousVariationAndValidation() {
        double life = 4.0;
        double h = 1e-4;
        double fadeStart = life - 0.7;
        double[] probes = {0.5, 1.0, 2.0, fadeStart - h, fadeStart, fadeStart + h, life - h * 2.0, life - 0.1};
        for (long seed = 0; seed < 10; seed++) {
            for (double t : probes) {
                double lo = Math.max(0.0, t - h);
                double hi = Math.min(life - 1e-9, t + h);
                double d = (FlareMotion.brightness(seed, hi, life) - FlareMotion.brightness(seed, lo, life));
                assertTrue(Double.isFinite(d) && Math.abs(d) < 0.05, "jump at t=" + t + " d=" + d);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> FlareMotion.height(Double.NaN, 1.0));
        assertThrows(IllegalArgumentException.class, () -> FlareMotion.height(1.0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> FlareMotion.alive(1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> FlareMotion.alive(Double.NaN, 1.0));
        assertThrows(IllegalArgumentException.class, () -> FlareMotion.brightness(0L, 1.0, -1.0));
        assertThrows(IllegalArgumentException.class, () -> FlareMotion.brightness(0L, Double.NEGATIVE_INFINITY, 1.0));
    }
}
