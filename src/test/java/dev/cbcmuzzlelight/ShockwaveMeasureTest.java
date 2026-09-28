package dev.cbcmuzzlelight;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two numbers the shockwave is built from: how far the blast got, and how enclosed it was.
 *
 * <p>These are the replacement for the block count the KubeJS generator used, so what matters is that
 * they separate the charge from the terrain: the same blast in the open and buried must come out with the
 * same yield and different reaches, and two different charges must not be confused just because they moved
 * a similar amount of material.
 */
class ShockwaveMeasureTest {
    /** Blocks spread evenly in every octant, out to {@code distance}. */
    private static List<BlockPos> shell(Vec3 center, double distance) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    blocks.add(BlockPos.containing(center.x + sx * distance, center.y + sy * distance, center.z + sz * distance));
                }
            }
        }
        return blocks;
    }

    @Test void anOpenBlastReachesItsChargeInEveryDirection() {
        Vec3 center = new Vec3(0.5, 64.5, 0.5);
        float yield = 12.0F;
        float[] reach = ShockwavePayload.measure(center, yield, shell(center, 11.5));
        for (float r : reach) assertTrue(r > 10.0F, "reached out in every octant: " + r);
        assertTrue(ShockwavePayload.solidity(yield, reach) < 0.25F,
            "an unobstructed blast is not enclosed");
    }

    @Test void aBuriedBlastDoesNotReach() {
        Vec3 center = new Vec3(0.5, 64.5, 0.5);
        float yield = 12.0F;
        // Only a couple of blocks, all close in: the blast was stopped almost immediately.
        List<BlockPos> near = List.of(
            BlockPos.containing(center.x + 1.0, center.y, center.z),
            BlockPos.containing(center.x, center.y + 1.0, center.z),
            BlockPos.containing(center.x, center.y, center.z + 1.0));
        float[] reach = ShockwavePayload.measure(center, yield, near);
        assertTrue(ShockwavePayload.solidity(yield, reach) > 0.6F,
            "a blast that is stopped at once is enclosed: " + ShockwavePayload.solidity(yield, reach));
    }

    @Test void theOctantsAreFilledEvenWhereNothingWasHit() {
        // An octant with no blocks must not come out as zero, or the wave would look broken there rather
        // than blocked.
        float[] reach = ShockwavePayload.measure(new Vec3(0, 0, 0), 12.0F, List.of());
        assertEquals(ShockwavePayload.OCTANTS, reach.length);
        for (float r : reach) assertTrue(r > 0.0F, "no octant is empty: " + r);
    }

    @Test void theShapeFollowsTheTerrain() {
        // Blocks spread out in the +x+y+z octant: a blast that opened into a corner. That octant must be
        // the long one, and the octants on the other side must keep the floor.
        Vec3 center = new Vec3(0.5, 0.5, 0.5);
        List<BlockPos> corner = new ArrayList<>();
        for (int i = 1; i <= 20; i++) corner.add(BlockPos.containing(center.x + i, center.y + i, center.z + i));
        float[] reach = ShockwavePayload.measure(center, 12.0F, corner);
        int open = ShockwavePayload.octant(1, 1, 1);
        int closed = ShockwavePayload.octant(-1, -1, -1);
        assertTrue(reach[open] > reach[closed], "the open octant reaches further");
        assertEquals(34.0F, reach[open], 2.0F, "and reaches as far as the blocks went");
    }

    @Test void theSameChargeIsTheSameYieldWhereverItLands() {
        // This is the whole point: yield comes from the charge, and the octants come from the terrain, so
        // an airburst and a hillside hit by the same shell share a tier and differ in shape.
        float airYield = 24.0F;
        float rockYield = 24.0F;
        Vec3 center = new Vec3(0.5, 64.5, 0.5);
        float[] air = ShockwavePayload.measure(center, airYield, shell(center, 23.0));
        float[] rock = ShockwavePayload.measure(center, rockYield,
            List.of(BlockPos.containing(center.x + 2, center.y + 2, center.z + 2)));
        assertEquals(airYield, rockYield, 0.0F, "same charge");
        assertTrue(ShockwavePayload.reachMean(air) > ShockwavePayload.reachMean(rock) * 3.0,
            "but very different reaches");
        assertTrue(ShockwavePayload.solidity(airYield, air) < ShockwavePayload.solidity(rockYield, rock),
            "and so very different solidities");
    }

    @Test void aBlockOnAnAxisBelongsToNoOctant() {
        // Otherwise the whole of an axis would land in one octant and leave its opposite empty.
        assertEquals(-1, ShockwavePayload.octant(5, 0, 0));
        assertEquals(-1, ShockwavePayload.octant(0, -3, 0));
        assertEquals(-1, ShockwavePayload.octant(0, 0, 7));
        assertEquals(0, ShockwavePayload.octant(-1, -1, -1));
        assertEquals(7, ShockwavePayload.octant(1, 1, 1));
        assertEquals(1, ShockwavePayload.octant(1, -1, -1));
    }
}
