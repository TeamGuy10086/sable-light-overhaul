package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.BlastPreviewPayload.Kind;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ImpactFragmentSizeTest {
    @Test void largestDiametersAreTwoToFourTimesThePreviousMaximumAndMoveSlower() {
        assertFragmentRange(Kind.GROUND, .15 * 1.35);
        assertFragmentRange(Kind.AIR, .14);
        assertFragmentRange(Kind.HULL, .14);
        assertFragmentRange(Kind.INTERCEPTION, .34);
    }

    @Test void craterWallSpreadsTheSkirtIntoTheOpening() {
        var outward = new net.minecraft.world.phys.Vec3(1, 0, 0);
        var flat = net.minecraft.world.phys.Vec3.ZERO;
        for (int i = 0; i < 16; i++) {
            assertTrue(ImpactExplosionSmoke.groundSpread(i * Math.PI / 8, outward).dot(outward) > 0);
        }
        assertTrue(ImpactExplosionSmoke.groundSpread(Math.PI, flat).dot(outward) < 0);
    }

    @Test void missileDebrisKeepsTheIncomingHeadingAtLowAndHighSpeed() {
        var heading = new Vec3(.4, -.5, .7).normalize();
        var perpendicular = heading.cross(new Vec3(0, 1, 0)).normalize();
        for (double speed : new double[] {.65, 2.4, 17, 51, 102}) {
            var incoming = heading.scale(speed);
            for (var scatter : new Vec3[] {heading.reverse(), perpendicular, perpendicular.reverse()}) {
                Vec3 velocity = ImpactExplosionSmoke.interceptionVelocity(incoming, scatter, 1, 1);
                double forward = velocity.dot(heading);
                double lateral = velocity.subtract(heading.scale(forward)).length();
                assertTrue(forward > 0);
                assertTrue(lateral <= Math.min(.56, .19 * forward) + 1e-10);
            }
        }
        var incoming = heading.scale(51);
        assertTrue(ImpactExplosionSmoke.interceptionVelocity(incoming, perpendicular, 1, .55).length()
            < ImpactExplosionSmoke.interceptionVelocity(incoming, perpendicular, 1, 1.45).length());
        assertTrue(ImpactExplosionSmoke.interceptionVelocity(Vec3.ZERO, perpendicular, 1, 1).length() < .2);
    }

    @Test void doublingAndQuadruplingFragmentDiameterScalesItsSmokeWidth() {
        var width = ExplosionTrailMesh.debrisWakeWidth(.25f);
        assertEquals(width * 2, ExplosionTrailMesh.debrisWakeWidth(.5f), 1e-9);
        assertEquals(width * 4, ExplosionTrailMesh.debrisWakeWidth(1), 1e-9);
    }

    @Test void calibratedMissileDiameterIsUsedDirectly() {
        for (float diameter : new float[] {.40f, .23f, .18f, .58f, .52f}) {
            assertEquals(diameter, ImpactExplosionSmoke.missileFragmentMaximum(2.5, diameter), 1e-8);
        }
        assertEquals(.60, ImpactExplosionSmoke.missileFragmentMaximum(8, 0), 1e-8);
    }

    private static void assertFragmentRange(Kind kind, double previousMax) {
        var small = ImpactExplosionSmoke.fragmentShape(kind, 0);
        var large = ImpactExplosionSmoke.fragmentShape(kind, 1);
        assertTrue(large.diameter() >= previousMax * 2 && large.diameter() <= previousMax * 4, kind.name());
        assertTrue(small.diameter() < large.diameter());
        assertTrue(small.speed() > large.speed());
    }
}
