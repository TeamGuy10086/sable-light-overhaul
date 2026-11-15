package dev.sablelightoverhaul;

import dev.sablelightoverhaul.AreaLight;
import dev.sablelightoverhaul.client.AreaSnapshot;
import dev.sablelightoverhaul.client.IrisMuzzleBridge;
import dev.sablelightoverhaul.lighting.Cones;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The style word: the one place the mod's per-light flags become the bit layout the shader reads.
 *
 * <p>Four different features share these bits -- emit faces, the flood mode, the shadow cones and the body
 * its cones are measured in -- so a mistake here does not fail to compile, it makes one lamp behave like
 * another. Each field is therefore pinned at its own shift, with values chosen so that a swap would be
 * visible.
 */
class LightStyleWordTest {
    private static AreaSnapshot snapshot(boolean flood, int faces, int cones, int bodySlot) {
        AreaLight light = new AreaLight(0, 0, 0, 1, 0, 0, 4, 4, 0, 15, false, 0.5, 0.5);
        int[] packed = Cones.parse(cones == 0 ? "" : cones == 1 ? "{0,90,0,45}" : "{0,90,0,45};{180,270,0,45}");
        return new AreaSnapshot(light, AreaSnapshot.bounds(light), null, 0, false, 0, 0, 0, flood, faces,
                packed[0], packed[1], packed[2], packed[3], bodySlot);
    }

    @Test
    void everyFlagSitsInItsOwnField() {
        int plain = IrisMuzzleBridge.style(snapshot(false, 63, 0, 0));
        assertEquals(63, plain & 63, "faces");
        assertEquals(0, plain & 64, "not flood");
        assertEquals(0, (plain >> IrisMuzzleBridge.STYLE_CONE_SHIFT) & 7, "no cones");
        assertEquals(0, (plain >> IrisMuzzleBridge.STYLE_BODY_SHIFT) & 7, "the world");

        int loaded = IrisMuzzleBridge.style(snapshot(true, 1, 2, 3));
        assertEquals(1, loaded & 63, "faces survive the other fields");
        assertEquals(64, loaded & 64, "flood");
        assertEquals(2, (loaded >> IrisMuzzleBridge.STYLE_CONE_SHIFT) & 7, "two cones");
        assertEquals(3, (loaded >> IrisMuzzleBridge.STYLE_BODY_SHIFT) & 7, "the third body");
        // Nothing may bleed into a neighbour: the flood bit sits outside the faces field, and the body
        // index outside the cone count.
        assertEquals(1, loaded & 63, "the flood bit is not part of the faces field");
        assertNotEquals(0, loaded & 64);
    }

    @Test
    void theConeCountIsWhatTheShaderUnpacks() {
        // The count is the number of leading non-zero words, which is also how the shader is entered: a
        // lamp whose second cone is set carries two, and the words are compacted so the test stops early.
        assertEquals(0, IrisMuzzleBridge.style(snapshot(false, 63, 0, 0)) >> IrisMuzzleBridge.STYLE_CONE_SHIFT & 7);
        assertEquals(1, IrisMuzzleBridge.style(snapshot(false, 63, 1, 0)) >> IrisMuzzleBridge.STYLE_CONE_SHIFT & 7);
        assertEquals(2, IrisMuzzleBridge.style(snapshot(false, 63, 2, 0)) >> IrisMuzzleBridge.STYLE_CONE_SHIFT & 7);
    }
}
