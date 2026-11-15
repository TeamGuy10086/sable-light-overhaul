package dev.sablelightoverhaul;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which shape a hand-held light wants: a bounded cone or a beam with distance falloff.
 *
 * <p>Both are built the way {@code HeldLights} would build them, with the player's eye at the origin
 * looking down +X, and both are sampled at the same world point so the comparison means something.
 *
 * <p>A cone folds its distance term into the same curve as its cross-section, {@code q = max(t, side/(axial*t))},
 * and the beam used to do the same through {@code max(across, along/range)}. The beam is two separate curves
 * now -- the angular profile, and a distance ramp whose knee the light carries -- so the two shapes are no
 * longer the same function. The tests below check the split instead of the equivalence.
 */
class FlashlightShapeTest {
    private static final double RANGE = 232.0;
    /** The default width signal 5 gives a 22-degree full angle. */
    private static final double HALF_ANGLE_TANGENT = Math.tan(Math.toRadians(22.0) / 2.0);
    private static final double EYE_HEIGHT = 1.6;

    /** The searchlight: apex at the eye, angle-only, deliberately as bright at the far end as the near. */
    private AreaLight searchlight() {
        return AreaLight.beam(0, 0, 0, 1, 0, 0, RANGE, HALF_ANGLE_TANGENT, 0, 0.4, 0.05, 15);
    }

    /** The hand torch: the same beam, plus the distance ramp a quarter way to nothing at half the range. */
    private AreaLight beam() {
        return AreaLight.beam(0, 0, 0, 1, 0, 0, RANGE, HALF_ANGLE_TANGENT, 0, 0.4, 0.05, 15, true, 0.5);
    }

    /**
     * The same light as a bounded cone: its x/y/z is the bounds centre, so the apex sits `axial` behind
     * it, and for a cone `lateral` is the radius reached at the far cap rather than a tangent.
     */
    private AreaLight cone() {
        double axial = RANGE / 2.0;
        double lateral = RANGE * HALF_ANGLE_TANGENT;
        return new AreaLight(axial, 0, 0, 1, 0, 0, axial, lateral, 0, 15, true, 0.4, 0.05);
    }

    /** Brightness where the beam lands on a floor `EYE_HEIGHT` below the axis, at one distance. */
    private double onFloor(AreaLight light, double distance) {
        return light.at(distance, -EYE_HEIGHT, 0);
    }

    @Test void bothShapesLightTheFloorAhead() {
        assertTrue(onFloor(beam(), 16) > 0, "beam lights the floor ahead");
        assertTrue(onFloor(cone(), 16) > 0, "cone lights the floor ahead");
    }

    /**
     * The beam is its angular curve times its distance ramp -- the identity that replaced the old
     * "a beam and a cone are the same shape".
     *
     * <p>That equivalence was real: folding distance into the cross-section through {@code max(across, down)}
     * made a beam reproduce a bounded cone exactly. It was also the thing the flashlight's page could not
     * express, because one number then moved both how wide the cone read and how far it reached. Asserting the
     * product here means re-folding the two curves -- deliberately or by accident -- fails in this file.
     *
     * <p>The ramp's argument is the <b>real path</b> from the apex, {@code hypot(along, side)}, not the axial
     * depth {@code along}; see {@link #theDistanceRampFollowsTheRealPathNotTheAxialDepth()}.
     */
    @Test void theBeamIsTheAngularCurveTimesTheDistanceRamp() {
        var torch = beam();
        for (double along : new double[] {4, 32, 116, 200, 228}) {
            for (double side : new double[] {0, 1.6, 8}) {
                double across = side / (along * HALF_ANGLE_TANGENT);
                double expected = 15
                        * AreaLight.falloff(across, 0, 0.4, 0.05)
                        * AreaLight.falloff(Math.hypot(along, side) / RANGE, 0, 0.5, 0.5);
                assertEquals(expected, torch.at(along, 0, side), 1.0E-9,
                        "at along=" + along + " side=" + side);
            }
        }
    }

    /**
     * The distance ramp runs on the light's real path, not on the depth along the axis.
     *
     * <p>This is a difference the player can see. With the ramp reading {@code along}, two points at the same
     * depth -- one on the axis, one out at the cone's rim -- came out equally bright, so the pool had a flat
     * middle and an edge as bright as its centre: "the flashlight's distance falloff is decided by its centre
     * point". A rim point is {@code 1/cos(angle)} further from the apex, and the ramp is what has to know it.
     *
     * <p>Two consequences are pinned here: the rim is dimmer than the axis at the same depth, and a torch's
     * reach becomes a sphere -- a point whose real distance is past the range is dark even though its axial
     * depth is still inside it. A searchlight has no ramp and keeps both its flat length and its hard axial
     * cut, so it is checked alongside.
     */
    @Test void theDistanceRampFollowsTheRealPathNotTheAxialDepth() {
        var torch = beam();
        for (double along : new double[] {64, 128, 200}) {
            double onAxis = torch.at(along, 0, 0);
            double atRim = torch.at(along, 0, along * HALF_ANGLE_TANGENT * 0.9);
            assertTrue(atRim < onAxis,
                    "the rim is further from the torch, so the ramp dims it more: " + atRim + " vs " + onAxis);
        }
        // Past the range on the diagonal, but still inside the cone and inside the axial depth.
        double along = RANGE * 0.99;
        double side = along * HALF_ANGLE_TANGENT * 0.9;
        assertTrue(Math.hypot(along, side) > RANGE, "the sample really is past the range");
        assertTrue(along < RANGE, "and its axial depth is still inside the range");
        assertEquals(0.0, torch.at(along, 0, side), 0.0, "a torch reaches a sphere, not a disc");
        assertTrue(searchlight().at(along, 0, side) > 0,
                "a searchlight has no ramp, so only its angle decides -- and this sample is inside the cone");
    }

    /** The point of the exercise: a hand torch gives up its light with distance, a searchlight does not. */
    @Test void theHandTorchDimsAlongItsLength() {
        var torch = beam();
        var lamp = searchlight();
        // Same angular profile, so the difference between the two is the distance ramp and nothing else.
        double torchFar = onFloor(torch, 200);
        double lampFar = onFloor(lamp, 200);
        assertTrue(torchFar < lampFar * 0.5,
                "the torch gives up its light with distance, the searchlight does not: " + torchFar + " vs " + lampFar);
        // The ramp only bites towards the end of the range: near the lamp the floor keeps most of its light.
        assertTrue(onFloor(torch, 32) > onFloor(torch, 200) * 2.0,
                "the near floor is not dimmed yet: " + onFloor(torch, 32) + " then " + torchFar);
    }

    /**
     * A searchlight is deliberately not dimmed by distance, and that has to stay true.
     *
     * <p>It is a ship's lamp: it should light the far end of its beam as brightly as the near end. Making
     * every beam fade was a change to the way that lamp looks, which is exactly why the distance term is a
     * property of the light rather than of the shape.
     */
    @Test void theSearchlightIsNotDimmedByDistance() {
        var lamp = searchlight();
        double near = onFloor(lamp, 32);
        double far = onFloor(lamp, 200);
        assertTrue(far > near * 0.5, "a searchlight keeps its brightness, was " + near + " then " + far);
    }

    /** The flag travels in the sign of the range, so the shape has to read it back correctly. */
    @Test void theFlagSurvivesTheEncoding() {
        assertFalse(searchlight().distanceFalloff());
        assertTrue(beam().distanceFalloff());
        assertEquals(RANGE, searchlight().beamRange(), 1.0E-9);
        assertEquals(RANGE, beam().beamRange(), 1.0E-9);
        assertEquals(-RANGE, beam().axial(), 1.0E-9, "a fading beam carries a negative range");
    }

    /** Both shapes agree on how the light is spread across the floor, not just along it. */
    @Test void bothShapesLightTheSameWidthOfFloor() {
        var beam = beam();
        var cone = cone();
        for (double distance : new double[] {16, 32, 128}) {
            assertEquals(patchHalfWidth(beam, distance), patchHalfWidth(cone, distance), 0.3,
                    "floor patch width at " + distance);
        }
    }

    /** How far to the side of the aim point the floor is still lit, at one distance. */
    private double patchHalfWidth(AreaLight light, double distance) {
        double limit = distance * HALF_ANGLE_TANGENT * 2.5;
        for (double side = 0; side < limit; side += 0.25) {
            if (light.at(distance, -EYE_HEIGHT, side) <= 0) return side;
        }
        return limit;
    }
}
