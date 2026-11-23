package dev.sablelightoverhaul.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DustColumnMotionTest {
    @Test void driftStartsAtTheLaunchEndpointWithTheSameVelocity() {
        Vec3 launch = new Vec3(3, 33, -5);
        Vec3 exitVelocity = new Vec3(.04, .45, -.06);
        double handoff = 3.12;
        var motion = new ImpactExplosionSmoke.DustColumnMotion(launch, exitVelocity, handoff);

        assertEquals(launch, motion.displacement(handoff));
        double delta = 1e-5;
        Vec3 before = launch.subtract(motion.displacement(handoff - delta)).scale(1 / delta);
        Vec3 after = motion.displacement(handoff + delta).subtract(launch).scale(1 / delta);
        assertTrue(before.distanceTo(exitVelocity) < 1e-4);
        assertTrue(after.distanceTo(exitVelocity) < 1e-4);
        assertTrue(motion.displacement(4).y > launch.y);
    }
}
