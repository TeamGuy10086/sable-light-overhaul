package dev.sablelightoverhaul.client;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The scene-space conversion every position this mod publishes goes through.
 *
 * <p>It is tested directly rather than through the uniform supplier, because the supplier cannot be called
 * without a running game -- and the fault this covers was invisible in game. A supplier that consumed its own
 * source still returned an array of plausible numbers, one set per program, and the only visible symptom was
 * that lamps whose slots the light list rewrites every tick kept working while the rest lit nothing.
 */
class BridgeSpaceTest {
    private static float[] rawSlots() throws Exception {
        Field field = IrisMuzzleBridge.class.getDeclaredField("FIELD_SLOTS");
        field.setAccessible(true);
        return (float[]) field.get(null);
    }

    /**
     * Calling the conversion twice with one camera pose must give one answer. The camera offset is subtracted
     * from the source, so a second call would subtract it again if the conversion wrote in place; a frame builds
     * many programs and each one asks for the uniform, so "twice" is the ordinary case, not an edge case.
     */
    @Test void theSceneConversionIsIdempotentWithinAFrame() throws Exception {
        float[] slots = rawSlots();
        slots[0] = 9.0F; slots[1] = 48.0F; slots[2] = -2.0F; slots[3] = 1.0F;   // a lamp with a field
        slots[4] = 0.0F; slots[5] = 0.0F; slots[6] = 0.0F; slots[7] = 0.0F;      // a slot with none
        double[] camera = { 30.4, 74.6, 31.5 };

        float[] first = IrisMuzzleBridge.toSceneSpace(camera, 2);
        float[] copy = first.clone();
        float[] second = IrisMuzzleBridge.toSceneSpace(camera, 2);

        assertArrayEquals(copy, second,
                "the conversion must not consume its own source: this runs once per program, so a second call "
                        + "inside one frame has to produce exactly the same array");
        assertEquals(9.0F - 30.4F, second[0], 1.0E-4, "the origin is the world origin minus the camera");
        assertEquals(48.0F - 74.6F, second[1], 1.0E-4);
        assertEquals(-2.0F - 31.5F, second[2], 1.0E-4);
        assertEquals(1.0F, second[3], 1.0E-4, "the layer travels untouched: it is not a position");
        assertEquals(0.0F, second[7], 1.0E-6, "an empty slot stays empty");
    }

    /**
     * A slot past the end of this frame's light list must read as "no field", even if a lamp held it earlier.
     *
     * <p>Neither array is rebuilt from scratch, so a slot that carried a lamp when more lights were in range
     * keeps its layer number. The shader has no way to tell that from a live field: it published the same origin
     * under two or three indices at once, which looks like one lamp lighting the ground from several places.
     */
    @Test void slotsPastTheLightListAreCleared() throws Exception {
        float[] slots = rawSlots();
        for (int slot = 0; slot < 4; slot++) {
            int base = slot * 4;
            slots[base] = 10.0F + slot; slots[base + 1] = 48.0F; slots[base + 2] = 20.0F; slots[base + 3] = 1.0F;
        }
        // A frame that now holds only two lights, with the camera somewhere close.
        float[] packed = IrisMuzzleBridge.toSceneSpace(new double[] { 5.0, 50.0, 25.0 }, 2).clone();
        assertTrue(packed[3] > 0.0F, "the first two slots still carry their fields");
        assertTrue(packed[7] > 0.0F);
        for (int slot = 2; slot < 4; slot++) {
            int base = slot * 4;
            assertEquals(0.0F, packed[base + 3], 0.0F,
                    "slot " + slot + " is past the light list and must publish no field, not a stale layer");
            assertEquals(0.0F, packed[base], 0.0F, "and its origin must be cleared too");
        }
    }

    /**
     * The single property the shader relies on: {@code field_local = scene_pos - origin} has to be the offset
     * from the field's corner to the surface, and that is a property of the world, so it must not change when
     * the camera does.
     *
     * <p>Both sides carry the camera and it cancels -- but only if both sides carry it. {@code scene_pos} is the
     * pack's own camera-relative position for the fragment, so the test has to build it as
     * {@code worldSurface - camera}, which is exactly the mistake the original bug made on the origin side.
     */
    @Test void theShaderSeesTheSameOffsetFromEveryCameraPosition() throws Exception {
        float[] slots = rawSlots();
        slots[0] = 100.0F; slots[1] = 64.0F; slots[2] = 200.0F; slots[3] = 3.0F;
        double worldSurfaceX = 105.0, worldSurfaceY = 64.0, worldSurfaceZ = 200.0;

        for (double[] camera : new double[][] {
                { 95.0, 64.0, 195.0 }, { 30.4, 74.6, 31.5 }, { -5000.0, 70.0, 9000.0 } }) {
            float[] packed = IrisMuzzleBridge.toSceneSpace(camera, 1).clone();
            // The shader's own arithmetic, in float: scene_pos is the fragment's world position minus the
            // camera, and the published origin is the field's world corner minus the same camera.
            float sceneX = (float) (worldSurfaceX - camera[0]);
            float fieldLocal = sceneX - packed[0];
            assertEquals(5.0F, fieldLocal, 1.0E-2,
                    "scene_pos - origin must be the world offset (5 blocks) at camera " + camera[0]);
            assertEquals(3.0F, packed[3], 0.0F, "the layer is unaffected by the camera");
        }
    }
}
