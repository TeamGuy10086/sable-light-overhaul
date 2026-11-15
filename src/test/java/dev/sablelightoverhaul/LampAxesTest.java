package dev.sablelightoverhaul;

import dev.sablelightoverhaul.client.LampAxes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The four axis lines of a lamp's angle frame.
 *
 * <p>These lines are the only way a player can see what the numbers on a lamp's page mean, so the
 * convention they draw has to be the convention the shader measures in: horizontal 0 on +X growing
 * towards +Z, its opposite on -X, and the vertical pair up and down. A mistake here would not fail to
 * compile; it would send somebody typing angles against the wrong frame, which is worse than no lines.
 */
class LampAxesTest {
    private static void assertVec(Vec3 expected, Vec3 actual, String what) {
        assertEquals(expected.x, actual.x, 1e-6, what + " x");
        assertEquals(expected.y, actual.y, 1e-6, what + " y");
        assertEquals(expected.z, actual.z, 1e-6, what + " z");
    }

    @Test
    void anUprightLampDrawsTheWorldAxes() {
        Vec3[] axes = LampAxes.axes(null);
        assertEquals(4, axes.length);
        assertVec(new Vec3(1, 0, 0), axes[0], "horizontal 0");
        assertVec(new Vec3(-1, 0, 0), axes[1], "horizontal 180");
        assertVec(new Vec3(0, 1, 0), axes[2], "vertical up");
        assertVec(new Vec3(0, -1, 0), axes[3], "vertical down");
        // The identity quaternion is the same frame as "no body at all", so a lamp on the ground behaves
        // the same before and after Sable answers.
        assertVec(axes[0], LampAxes.axes(new float[]{0, 0, 0, 1})[0], "identity");
    }

    @Test
    void aTurnedHullTurnsTheFrameWithIt() {
        // A quarter turn about +Y maps +X onto -Z: the frame a player measured against the deck follows
        // the deck, which is the whole reason the lines take a quaternion at all.
        float half = (float) Math.sin(Math.PI / 4.0);
        float[] yawQuarter = {0.0F, half, 0.0F, half};
        Vec3[] axes = LampAxes.axes(yawQuarter);
        assertVec(new Vec3(0, 0, -1), axes[0], "horizontal 0 after a quarter turn");
        assertVec(new Vec3(0, 0, 1), axes[1], "horizontal 180 stays opposite");
        // The vertical pair must not move for a yaw: it is the same line before and after.
        assertVec(new Vec3(0, 1, 0), axes[2], "vertical up is unmoved by yaw");
        assertVec(new Vec3(0, -1, 0), axes[3], "vertical down is unmoved by yaw");
    }

    @Test
    void thePairsStayOppositeAndUnitLength() {
        // Whatever the hull is doing, the two ends of one axis are the same line pointing both ways: if
        // they ever drift, a range read off the gizmo would be measured against two different frames.
        float[] tilted = {0.2706F, 0.6533F, -0.2706F, 0.6533F};
        Vec3[] axes = LampAxes.axes(tilted);
        for (int i = 0; i < 4; i++) {
            assertEquals(1.0, axes[i].length(), 1e-5, "axis " + i + " is a unit vector");
        }
        assertVec(axes[0].scale(-1), axes[1], "horizontal pair is opposite");
        assertVec(axes[2].scale(-1), axes[3], "vertical pair is opposite");
        assertEquals(0.0, axes[0].dot(axes[2]), 1e-5, "the two axes stay perpendicular");
    }
}
