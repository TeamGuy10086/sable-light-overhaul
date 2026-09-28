package dev.cbcmuzzlelight;

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
 * <p>The answer is that they are the same function. A cone's q is {@code max(t, side/(axial*t))} with
 * {@code t = (distance from apex)/length}; a beam's q is {@code max(side/(along*lateral), along/range)}.
 * Substituting the cone's apex-relative distance and {@code lateral = length*tan(half angle)} turns one
 * into the other, so the distance term the beam was missing is exactly what a cone has always had, and a
 * beam reaches the same result with its origin at the apex rather than at a bounds centre.
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

    /** The hand torch: the same beam, plus the distance term. */
    private AreaLight beam() {
        return AreaLight.beam(0, 0, 0, 1, 0, 0, RANGE, HALF_ANGLE_TANGENT, 0, 0.4, 0.05, 15, true);
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
     * A beam and a cone with the same apex are the same shape, everywhere the cone has a real
     * cross-section. The apex itself is excluded: a cone's radius is zero there, so it is degenerate by
     * construction, while a beam simply starts at its origin.
     */
    @Test void aBeamIsTheSameShapeAsAConeWithTheSameApex() {
        var beam = beam();
        var cone = cone();
        double worst = 0;
        String worstAt = "";
        for (double along = 4; along <= RANGE - 1; along += 3.5) {
            for (double side = 0; side <= 60; side += 1.5) {
                double viaBeam = beam.at(along, 0, side);
                double viaCone = cone.at(along, 0, side);
                double diff = Math.abs(viaBeam - viaCone);
                if (diff > worst) {
                    worst = diff;
                    worstAt = "along=" + along + " side=" + side + " beam=" + viaBeam + " cone=" + viaCone;
                }
            }
        }
        assertEquals(0.0, worst, 1.0E-9, "worst disagreement: " + worstAt);
    }

    /** The point of the exercise: a hand torch dims along its own length instead of staying bright. */
    @Test void theHandTorchDimsAlongItsLength() {
        var torch = beam();
        double near = onFloor(torch, 32);
        double far = onFloor(torch, 200);
        assertTrue(far < near * 0.25, "the far end is much dimmer than the near end, was " + near + " then " + far);
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
