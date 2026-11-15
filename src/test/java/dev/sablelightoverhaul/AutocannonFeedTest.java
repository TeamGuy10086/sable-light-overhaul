package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AutocannonFeedTest {
    @Test void doesNotPublishSpawnOrPositionCorrectionAsTravel() {
        Vec3 muzzle = new Vec3(0, 82, 0);
        assertFalse(AutocannonFeed.measuredStep(Vec3.ZERO, muzzle, 0, 0));
        assertFalse(AutocannonFeed.measuredStep(Vec3.ZERO, muzzle, 1, 0));
        assertFalse(AutocannonFeed.measuredStep(muzzle, muzzle.add(0, 17, 0), 1, 4));
        assertFalse(AutocannonFeed.measuredStep(muzzle, muzzle.add(4, 0, 0), 2, 4));
    }

    @Test void publishesMeasuredTravelInAnyDirection() {
        Vec3 muzzle = new Vec3(100, 82, -100);
        assertTrue(AutocannonFeed.measuredStep(muzzle, muzzle.add(4, 0, 0), 1, 4));
        assertTrue(AutocannonFeed.measuredStep(muzzle, muzzle.add(0, 4, 0), 1, 4));
        assertTrue(AutocannonFeed.measuredStep(muzzle, muzzle.add(0, -4, 0), 1, 4));
    }
}
