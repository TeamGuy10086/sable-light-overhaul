package dev.sablelightoverhaul;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DustHeightCalibrationTest {
    @Test
    void referenceUsesTheTwoShellBenchmarksAndInterpolatesBetweenPowers() {
        assertEquals(222.0, DustHeightCalibration.referenceBlocks(6));
        assertEquals(403.0, DustHeightCalibration.referenceBlocks(8));
        assertEquals(356.5, DustHeightCalibration.referenceBlocks(7.5));
        assertEquals(1.0, DustHeightCalibration.heightFactor(6, 222));
        assertEquals(.5, DustHeightCalibration.heightFactor(6, 111));
        assertEquals(1.75, DustHeightCalibration.heightFactor(8, 1000));
    }

    @Test
    void packetDistinguishesUnknownDamageFromZeroAndPreservesTheShallowVentFlag() {
        int dust = BlastPreviewPayload.ventDustColor(0x414347, 2);
        var dimension = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        var unknown = new BlastPreviewPayload(dimension, BlastPreviewPayload.Kind.BURIED, Vec3.ZERO, Vec3.ZERO,
            8, BlastPreviewPayload.withDustHeight(dust, 8, -1));
        var zero = new BlastPreviewPayload(dimension, BlastPreviewPayload.Kind.BURIED, Vec3.ZERO, Vec3.ZERO,
            8, BlastPreviewPayload.withDustHeight(dust, 8, 0));
        assertEquals(1.0, unknown.dustHeightFactor());
        assertEquals(0.0, zero.dustHeightFactor());
        assertTrue(zero.shallowVent());
        assertEquals(0x414347, zero.dustColor() & 0xFFFFFF);
    }
}
