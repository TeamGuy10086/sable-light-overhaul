package dev.sablelightoverhaul;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotlightPoseTest {
    private static final double EPS = 1e-6;

    /** Same rotation order as the model: rotateY(yaw) then rotateX(pitch). */
    private static Matrix4d rotation(double tilt, double azimuth) {
        return new Matrix4d().rotateY(Math.toRadians(SpotlightPose.yaw(azimuth)))
                .rotateX(Math.toRadians(SpotlightPose.pitch(tilt)));
    }

    @Test
    void localForwardMatchesMouthOffsetDirection() {
        Random rng = new Random(20240607L);
        for (int i = 0; i < 512; i++) {
            double tilt = -90 + 180 * rng.nextDouble();
            double azimuth = -180 + 360 * rng.nextDouble();
            Vector3d dir = rotation(tilt, azimuth).transformDirection(new Vector3d(0, 0, -1));
            double tr = Math.toRadians(tilt), ar = Math.toRadians(azimuth);
            double[] offset = SpotlightPose.mouthOffset(tilt, azimuth);
            assertEquals(SpotlightPose.LENS_REACH * Math.sin(tr) * Math.cos(ar), offset[0], EPS);
            assertEquals(SpotlightPose.PIVOT_Y - 0.5 + SpotlightPose.LENS_REACH * Math.cos(tr), offset[1], EPS);
            assertEquals(SpotlightPose.LENS_REACH * Math.sin(tr) * Math.sin(ar), offset[2], EPS);
            assertEquals(SpotlightPose.LENS_REACH * Math.sin(tr) * Math.cos(ar),
                    SpotlightPose.LENS_REACH * dir.x, EPS, "tilt=" + tilt + " az=" + azimuth);
            assertEquals(SpotlightPose.LENS_REACH * Math.cos(tr),
                    SpotlightPose.LENS_REACH * dir.y, EPS, "tilt=" + tilt + " az=" + azimuth);
            assertEquals(SpotlightPose.LENS_REACH * Math.sin(tr) * Math.sin(ar),
                    SpotlightPose.LENS_REACH * dir.z, EPS, "tilt=" + tilt + " az=" + azimuth);
        }
    }

    @Test
    void mouthOffsetIsPivotPlusRotatedReach() {
        Random rng = new Random(991L);
        for (int i = 0; i < 512; i++) {
            double tilt = -90 + 180 * rng.nextDouble();
            double azimuth = -180 + 360 * rng.nextDouble();
            Vector3d lens = rotation(tilt, azimuth).transformDirection(new Vector3d(0, 0, -SpotlightPose.LENS_REACH));
            double[] offset = SpotlightPose.mouthOffset(tilt, azimuth);
            assertEquals(lens.x, offset[0], EPS);
            assertEquals(SpotlightPose.PIVOT_Y - 0.5 + lens.y, offset[1], EPS);
            assertEquals(lens.z, offset[2], EPS);
        }
        Vector3d up = rotation(0, -90).transformDirection(new Vector3d(0, 0, -1));
        assertTrue(up.y > 0.999999, "default pose should point up, got " + up);
        double[] neutral = SpotlightPose.mouthOffset(0, -90);
        assertTrue(neutral[1] > SpotlightPose.PIVOT_Y - 0.5, "neutral lens must sit above the pivot");
    }
}
