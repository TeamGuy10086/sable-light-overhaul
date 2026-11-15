package dev.sablelightoverhaul;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that decides which lights get the shadow slots.
 *
 * <p>The pack takes a bit per light, so the mod's whole lever is this weight. Distance alone is the wrong
 * answer in both directions, and "where does the light's centre land on screen" is worse, because the
 * light this exists for is behind a wall and therefore nowhere on screen at all. What the tests below pin
 * down is that the weight finds that light anyway, and still refuses to spend a slot on a light that
 * cannot reach the view.
 *
 * <p>The frustum used throughout is the 90-degree cone {@code |y| <= x, |z| <= x, x >= 0}, built from a
 * near plane of four corners at x = 1.
 */
class LightScreenWeightTest {
    /**
     * The pool is handed out by class priority before visibility, and the rank of a class is the ordinal of
     * {@code EffectApi.Priority} — weakest first. That dependency is what this pins: reordering the enum
     * declaration would invert every choice made from it, silently, in a client renderer that no other test
     * touches.
     */
    @Test void thePriorityEnumIsDeclaredWeakestFirst() {
        assertTrue(dev.sablelightoverhaul.api.EffectApi.Priority.LOW.ordinal()
                < dev.sablelightoverhaul.api.EffectApi.Priority.NORMAL.ordinal());
        assertTrue(dev.sablelightoverhaul.api.EffectApi.Priority.NORMAL.ordinal()
                < dev.sablelightoverhaul.api.EffectApi.Priority.HIGH.ordinal());
    }
    /** Camera at the origin looking down +X, with a 90-degree view. */
    private static double[] frustum() {
        return LightScreenWeight.sidePlanes(0, 0, 0, new double[] {
                1, 1, -1,      // top left
                1, 1, 1,       // top right
                1, -1, 1,      // bottom right
                1, -1, -1});   // bottom left
    }

    /** A radial light of the given reach and brightness; its extent is that radius in every direction. */
    private static AreaLight sphere(double x, double y, double z, double radius, double brightness) {
        return new AreaLight(x, y, z, 1, 0, 0, radius, radius, 0.15, brightness, false, 0.575, 0.5);
    }

    private double weight(AreaLight light) {
        return LightScreenWeight.of(light, 0, 0, 0, frustum());
    }

    /** The planes have to be inward-facing, or every light would be judged to be outside. */
    @Test void thePlanesFaceInward() {
        double[] planes = frustum();
        for (int i = 0; i < LightScreenWeight.SIDES; i++) {
            int b = i * 4;
            double ahead = planes[b] * 10 + planes[b + 1] * 0 + planes[b + 2] * 0 + planes[b + 3];
            assertTrue(ahead > 0, "plane " + i + " does not contain a point straight ahead");
        }
        assertTrue(weight(sphere(10, 0, 0, 1, 15)) > 0, "a light straight ahead is worth something");
    }

    /** A light that cannot reach the view is worth nothing, however bright it is. */
    @Test void aLightOutsideTheViewIsWorthNothing() {
        assertEquals(0.0, weight(sphere(-10, 0, 0, 2, 15)), 1.0E-12, "straight behind");
        assertEquals(0.0, weight(sphere(10, 0, 20, 2, 15)), 1.0E-12, "far off to the side");
        assertEquals(0.0, weight(sphere(10, 30, 0, 2, 15)), 1.0E-12, "far above");
    }

    /**
     * The case a test on the light's screen position cannot handle.
     *
     * <p>This lamp is the one leaking through a wall: it sits a couple of blocks beyond it, so it is
     * nowhere on screen. It is squarely in front of the camera, though, and it pours light onto the very
     * wall the player is looking at, so it has to outrank a flash behind them by a wide margin.
     */
    @Test void aLampInFrontOfThePlayerOutranksAFlashBehindThem() {
        AreaLight leakingLamp = sphere(3, 0, 0, 15, 15);
        AreaLight flashBehind = sphere(-2, 0, 0, 6, 15);
        assertTrue(weight(leakingLamp) > weight(flashBehind) * 2.0,
                "lamp " + weight(leakingLamp) + " vs flash " + weight(flashBehind));
    }

    /** Reach counts for something even from outside the view: light spills in from the edges. */
    @Test void aBigLightOutsideStillPokesIntoTheView() {
        assertEquals(0.0, weight(sphere(10, 0, 20, 2, 15)), 1.0E-12);
        assertTrue(weight(sphere(10, 0, 20, 30, 15)) > 0, "the same light with reach spills in");
    }

    /** The solid angle: nearer and bigger both cover more of the view. */
    @Test void nearerAndBiggerBothCoverMore() {
        assertTrue(weight(sphere(5, 0, 0, 2, 15)) > weight(sphere(20, 0, 0, 2, 15)));
        assertTrue(weight(sphere(10, 0, 0, 4, 15)) > weight(sphere(10, 0, 0, 2, 15)));
    }

    @Test void aBrighterLightOutranksADimmerOne() {
        assertEquals(3.0, weight(sphere(10, 0, 0, 2, 15)) / weight(sphere(10, 0, 0, 2, 5)), 1.0E-9);
    }

    /** A light the camera is standing in covers the whole view, and that is where coverage saturates. */
    @Test void coverageSaturatesWhenTheCameraIsInside() {
        double inside = weight(sphere(0.0, 0, 0, 8, 15));
        assertEquals(1.0, inside, 1.0E-3, "a light around the camera is the whole view, not more than it");
    }

    /**
     * A beam's extent is its length, and a length is not a width.
     *
     * <p>A 512-block searchlight reports a 512-block radius. Believing that would let it outrank a lamp
     * the player is standing next to, from any distance at all, on size alone.
     */
    @Test void aLongBeamsLengthIsNotTreatedAsWidth() {
        AreaLight searchlight = AreaLight.beam(40, 0, -20, 1, 0, 0, 512, 0.1, 0, 0.5, 0.5, 15, true);
        assertTrue(searchlight.extentX() > 400, "the beam really does report its length");
        assertTrue(weight(sphere(3, 0, 0, 15, 15)) > weight(searchlight),
                "the nearby lamp still wins");
    }

    /** Degenerate planes are skipped rather than silently halving every weight. */
    @Test void aDegenerateFrustumIsIgnored() {
        double[] flat = LightScreenWeight.sidePlanes(0, 0, 0, new double[] {
                1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0});
        assertEquals(1.0, LightScreenWeight.of(sphere(0, 0, 0, 8, 15), 0, 0, 0, flat), 1.0E-3);
    }
}
