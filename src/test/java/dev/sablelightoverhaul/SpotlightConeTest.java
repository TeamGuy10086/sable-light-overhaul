package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.SourceColor;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The spotlight's cone opening, the colour list the disco ball's page writes, and the haze push.
 *
 * <p>All three were shipped as "the page's number does nothing" reports, and all three are pure arithmetic
 * with one right answer -- which is exactly what a unit test is for.
 */
class SpotlightConeTest {
    @Test void coneSweepsBetweenTheLampsOwnTwoLimits() {
        // Signal 0 is the lower limit and 15 the upper, with a straight line between them: that is what makes
        // a redstone input sweep the cone's size instead of picking one of sixteen fixed openings.
        assertEquals(10.0, BeamLight.coneDegrees(10, 52, 0), 1e-9);
        assertEquals(52.0, BeamLight.coneDegrees(10, 52, 15), 1e-9);
        assertEquals(24.0, BeamLight.coneDegrees(10, 52, 5), 1e-9);
        // Out-of-range drivers are clamped rather than extrapolated.
        assertEquals(10.0, BeamLight.coneDegrees(10, 52, -4), 1e-9);
        assertEquals(52.0, BeamLight.coneDegrees(10, 52, 99), 1e-9);
    }
    @Test void coneLimitsAreOrderedAndClamped() {
        // A page can write the two limits in either order; the beam is built from whichever is larger.
        assertEquals(10.0, BeamLight.coneDegrees(52, 10, 0), 1e-9);
        assertEquals(52.0, BeamLight.coneDegrees(52, 10, 15), 1e-9);
        // One degree is the floor and a right angle the ceiling: below one is a laser, above ninety is not a
        // spotlight any more, and the shader's beam is a cone with a core and a rim.
        assertEquals(1.0, BeamLight.coneDegrees(0, 0, 15), 1e-9);
        assertEquals(90.0, BeamLight.coneDegrees(200, 200, 15), 1e-9);
    }
    @Test void defaultLimitsReproduceTheOldFixedScale() {
        // The defaults are the 10..52 degrees the width scale used to mean, so a lamp placed before the two
        // limits existed looks exactly the same after the update. Driver 15 used to be angle(15) = 52.
        assertEquals(BeamLight.angle(15), BeamLight.coneDegrees(10, 52, 15), 1e-9);
        assertEquals(BeamLight.angle(1), BeamLight.coneDegrees(10, 52, 0), 1e-9);
    }
    @Test void aBeamBuiltFromDegreesHasTheSameShapeAsOneBuiltFromASignal() {
        Vec3 at = new Vec3(3, 4, 5), along = new Vec3(0, 1, 0);
        var fromSignal = BeamLight.create(at, along, 32, 15, 15, 2);
        var fromDegrees = BeamLight.create(at, along, 32, BeamLight.angle(15), 15, 2);
        assertEquals(fromSignal.lateral(), fromDegrees.lateral(), 1e-12);
        assertEquals(fromSignal.axial(), fromDegrees.axial(), 1e-12);
    }
    @Test void theDisabledFalloffProfileIsNotOffered() {
        // Five profiles on offer, and the physical inverse-square one -- documented as buggy -- is not one of
        // them. A lamp already stored on it still sees its own name, so the page cannot silently move it.
        assertEquals(5, dev.sablelightoverhaul.Falloff.choices(0).size());
        assertEquals(5, dev.sablelightoverhaul.Falloff.choices(4).size());
        var stored = dev.sablelightoverhaul.Falloff.choices(5);
        assertEquals(6, stored.size());
        assertTrue(stored.get(5).startsWith(dev.sablelightoverhaul.Falloff.NAMES[5]));
        assertTrue(stored.get(5).contains("停用"));
    }
    @Test void theFalloffCycleSkipsTheDisabledProfile() {
        assertEquals(1, dev.sablelightoverhaul.Falloff.next(0));
        assertEquals(4, dev.sablelightoverhaul.Falloff.next(3));
        assertEquals(0, dev.sablelightoverhaul.Falloff.next(4));
        // A lamp that is already on the disabled one is moved off it, not stuck there.
        assertEquals(0, dev.sablelightoverhaul.Falloff.next(5));
        assertEquals(0, dev.sablelightoverhaul.Falloff.next(99));
    }
    @Test void colourListsAcceptIndicesAndHexAndSkipGarbage() {
        // Palette indices, which is what every list written before the hex box holds.
        assertArrayEquals(new int[]{1, 4, 11, 14}, SourceColor.parseList("1,4,11,14"));
        // Hand-typed colours, upper or lower case, with or without a hash.
        assertArrayEquals(new int[]{SourceColor.custom(0xFFD9C0)},
                SourceColor.parseList("FFD9C0"));
        assertArrayEquals(new int[]{SourceColor.custom(0xFFD9C0), 3},
                SourceColor.parseList("#ffd9c0, 3"));
        // One bad token costs itself and nothing else, and never leaves a phantom slot behind: the old loop
        // incremented its counter before the parse, so a rejected token was stored as the vanilla slot.
        assertArrayEquals(new int[]{2, 9}, SourceColor.parseList("2,biscuit,9"));
        assertArrayEquals(new int[]{2, 9}, SourceColor.parseList("2,,9"));
        // An empty or hopeless list is the vanilla slot rather than no light at all.
        assertArrayEquals(new int[]{SourceColor.UNTINTED}, SourceColor.parseList(""));
        assertArrayEquals(new int[]{SourceColor.UNTINTED}, SourceColor.parseList("nonsense"));
        // Indices are clamped to the palette, so a stale list cannot index past it.
        assertArrayEquals(new int[]{0, 15}, SourceColor.parseList("-3,42"));
    }
    @Test void vanillaTintIsTheWarmWhiteTheUserPicked() {
        float[] out = new float[3];
        SourceColor.rgb(SourceColor.UNTINTED, 0, out, 1F, .58F, .25F);
        // #FFD9C0, converted the same way every palette colour is: sRGB channels to linear.
        assertEquals(Math.pow(255 / 255.0, 2.2), out[0], 1e-6);
        assertEquals(Math.pow(217 / 255.0, 2.2), out[1], 1e-6);
        assertEquals(Math.pow(192 / 255.0, 2.2), out[2], 1e-6);
    }
    @Test void thePushBehindTheGunIsThirtyPercentOfThePushInFront() {
        Vec3 muzzle = new Vec3(0, 0, 0), axis = new Vec3(0, 0, 1);
        double front = CannonDustMotion.gustHorizontal(new Vec3(0, 0, 0.5), muzzle, axis, 3, 0.4).length();
        double rear = CannonDustMotion.gustHorizontal(new Vec3(0, 0, -0.5), muzzle, axis, 3, 0.4).length();
        assertEquals(0.3, CannonDustMotion.REAR_PUSH, 0.0);
        // Same distance on both sides, so the only difference is which half of the gun it is on.
        assertEquals(0.3, rear / front, 1e-9);
        // A puff straight over the barrel has no side; it is pushed rather than left on the barrel.
        assertTrue(CannonDustMotion.gustHorizontal(new Vec3(0.01, 0, 0), muzzle, axis, 3, 0.4).length() > 0);
        // And the whole push is off when the feature is.
        assertEquals(0.0, CannonDustMotion.gustHorizontal(new Vec3(0, 0, -0.5), muzzle, axis, 3, 0).length(), 1e-12);
    }
}
