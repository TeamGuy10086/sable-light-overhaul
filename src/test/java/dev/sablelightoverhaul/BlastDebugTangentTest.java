package dev.sablelightoverhaul;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlastDebugTangentTest {
    @Test void motionStaysInTheViewPlaneEvenWhenLookingVertically() {
        for (var look : new Vec3[] { new Vec3(0, 1, 0), new Vec3(1, .2, -.5).normalize() }) {
            for (double turn : new double[] { 0, .2, .7, .95 }) {
                Vec3 slow = BlastDebugItem.tangentMotion(look, turn, 0);
                Vec3 fast = BlastDebugItem.tangentMotion(look, turn, 1);
                assertEquals(0, look.dot(slow), 1e-9);
                assertEquals(0, look.dot(fast), 1e-9);
                assertEquals(.65, slow.length(), 1e-9);
                assertEquals(2.4, fast.length(), 1e-9);
                assertTrue(slow.dot(fast) > 0);
            }
        }
    }
}
