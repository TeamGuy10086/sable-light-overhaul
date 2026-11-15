package dev.sablelightoverhaul;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flashlight's shape numbers, and the beam they build.
 *
 * <p>Width replaced a 1..15 width "signal", and decay replaced a single profile that shaped both the
 * cross-section and the length. They are two knobs now: the cross-section is one of the five profiles the
 * spotlights use (see {@link BeamLight#profile}), and decay is the knee of the distance ramp alone. What is
 * checked here is the arithmetic the page and the draw path share: the angle really is the half-angle tangent
 * of the degree value, and the decay speed really does scale how fast the beam dims along its length.
 */
class FlashlightBeamTest {
    private static final int RANGE = 80;

    /** The beam both sides build: the shipped angular profile, and the distance knee "decay" asks for. */
    private AreaLight beam(int widthDegrees, int decayPercent) {
        BeamLight.Profile profile = BeamLight.profile(FlashlightBeam.DEFAULT_PROFILE);
        double knee = FlashlightBeam.knee(decayPercent);
        return AreaLight.beam(0, 0, 0, 1, 0, 0, RANGE, FlashlightBeam.halfAngleTangent(widthDegrees),
                profile.core(), profile.middle(), profile.tail(), 10 * profile.brightness(), true, knee);
    }

    @Test void widthIsAPlainFullAngleInDegrees() {
        assertEquals(Math.tan(Math.toRadians(22.0) / 2.0), FlashlightBeam.halfAngleTangent(22), 1.0E-12);
        assertEquals(Math.tan(Math.toRadians(5.0) / 2.0), FlashlightBeam.halfAngleTangent(5), 1.0E-12);
        // 90 degrees is a full right angle, so its half-angle tangent is exactly one.
        assertEquals(1.0, FlashlightBeam.halfAngleTangent(90), 1.0E-12);
    }

    /** A config file, and the page, can both produce nonsense; neither may reach the light. */
    @Test void widthIsClampedRatherThanThrowing() {
        assertEquals(FlashlightBeam.halfAngleTangent(5), FlashlightBeam.halfAngleTangent(0), 1.0E-12);
        assertEquals(FlashlightBeam.halfAngleTangent(5), FlashlightBeam.halfAngleTangent(-400), 1.0E-12);
        assertEquals(FlashlightBeam.halfAngleTangent(90), FlashlightBeam.halfAngleTangent(91), 1.0E-12);
        assertEquals(FlashlightBeam.halfAngleTangent(90), FlashlightBeam.halfAngleTangent(100000), 1.0E-12);
    }

    /** The reference shape: the shipped default must not have changed the way the torch looks. */
    @Test void oneHundredPercentIsTheHalfWayShape() {
        assertEquals(0.5, FlashlightBeam.knee(100), 1.0E-12);
    }

    @Test void fasterDecayPutsTheKneeNearer() {
        assertEquals(0.5 / 3.0, FlashlightBeam.knee(300), 1.0E-12);
        // 20 percent would want a knee of 2.5, which is not a fraction: it is clamped to the top of the
        // usable range rather than handed to AreaLight, which would reject it.
        assertEquals(0.95, FlashlightBeam.knee(20), 1.0E-12);
    }

    /** Whatever the page or the TOML says, the profile has to stay something AreaLight will accept. */
    @Test void theProfileIsAlwaysUsable() {
        // The knee at the fastest decay the range allows is the smallest share that can ever come out.
        double smallest = 0.5 / (FlashlightBeam.MAX_DECAY_PERCENT / 100.0);
        for (int decay = -1000; decay <= 10000; decay += 7) {
            double share = FlashlightBeam.knee(decay);
            assertTrue(share > 0.0 && share < 1.0, "share out of range at " + decay + ": " + share);
            assertTrue(share >= smallest - 1.0E-12, "share below the floor at " + decay + ": " + share);
            // The one call that would throw if the number were ever bad.
            beam(22, decay);
        }
    }

    @Test void higherDecayIsDimmerAtTheSameDistance() {
        double slow = beam(22, FlashlightBeam.MIN_DECAY_PERCENT).at(60, 0, 0);
        double reference = beam(22, 100).at(60, 0, 0);
        double fast = beam(22, FlashlightBeam.MAX_DECAY_PERCENT).at(60, 0, 0);
        assertTrue(slow > reference, "slow decay keeps more light far away: " + slow + " vs " + reference);
        assertTrue(fast < reference, "fast decay gives up its light sooner: " + fast + " vs " + reference);
    }

    /** The whole point of a decay speed: the beam is brighter near the lamp than far from it. */
    @Test void theBeamDimsAlongItsLength() {
        AreaLight torch = beam(22, 100);
        double near = torch.at(8, 0, 0);
        double far = torch.at(72, 0, 0);
        assertTrue(far < near * 0.25, "near " + near + " then far " + far);
    }

    /** Width has to change the cone, not just a number in the page. */
    @Test void aWiderBeamLightsWhatANarrowOneMisses() {
        double offAxis = 10.0;
        double narrow = beam(10, 100).at(40, 0, offAxis);
        double wide = beam(90, 100).at(40, 0, offAxis);
        assertEquals(0.0, narrow, 1.0E-12, "10 degrees does not reach 10 blocks off axis at 40 blocks");
        assertTrue(wide > 0.0, "90 degrees does: " + wide);
    }

    /** Past the range the beam stops dead, whatever its width or decay. */
    @Test void nothingSurvivesTheRangeCutOff() {
        assertEquals(0.0, beam(90, FlashlightBeam.MIN_DECAY_PERCENT).at(RANGE + 1, 0, 0), 1.0E-12);
        assertEquals(0.0, beam(90, 100).at(0, 0, 0), 1.0E-12, "the apex itself is behind the cut-off");
    }

    /** A non-positive tangent is what AreaLight refuses, which is why the clamp matters. */
    @Test void aDegenerateWidthCannotReachAreaLight() {
        assertThrows(IllegalArgumentException.class,
                () -> AreaLight.beam(0, 0, 0, 1, 0, 0, RANGE, 0, 0, 0.5, 0.5, 10, true));
    }
}
