package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sablelightoverhaul.ShockwavePayload.BlastClass;
import dev.sablelightoverhaul.client.ShockwaveRing;
import dev.sablelightoverhaul.effects.ImpactClassifier;
import org.junit.jupiter.api.Test;

/**
 * The two pieces of the KubeJS ring that can be checked without a game: which tier a block count falls in
 * and what that tier buys, and the three-way decision about where the charge went off.
 *
 * <p>The tier numbers are the script's own, so these tests are also the record of what "ported unchanged"
 * means -- if a number is ever retuned, this is where the intent is written down.
 */
class ShockwaveRingTest {
    @Test void theTiersAreTheScriptsOwnWithOurFloor() {
        assertNull(ShockwaveRing.tierFor(199), "below the mod's own explosion gate");
        assertNotNull(ShockwaveRing.tierFor(200), "the lowest tier now starts where the mod's gate does");
        assertEquals(1, tierIndex(550));
        assertEquals(1, tierIndex(1999));
        assertEquals(2, tierIndex(2000));
        assertEquals(2, tierIndex(17999));
        assertEquals(3, tierIndex(18000));
        assertEquals(3, tierIndex(1000000));
    }

    private static int tierIndex(int blocks) {
        ShockwaveRing.Tier tier = ShockwaveRing.tierFor(blocks);
        return tier == ShockwaveRing.TIERS[0] ? 1 : tier == ShockwaveRing.TIERS[1] ? 2 : 3;
    }

    @Test void theCountAndSpeedAreTheScriptsFormulas() {
        ShockwaveRing.Tier tier1 = ShockwaveRing.TIERS[0];
        // count = min(blocks * factor, max) * multiplier, with the script's floor of ten.
        assertEquals(100, ShockwaveRing.countFor(tier1, 400), "400 * 0.25, below the clamp");
        assertEquals(200, ShockwaveRing.countFor(tier1, 1000), "1000 * 0.25 = 250, clamped to maxCount 200");
        assertEquals(200, ShockwaveRing.countFor(tier1, 10000), "and stays there");
        assertEquals(10, ShockwaveRing.countFor(tier1, 30), "and never below ten");
        ShockwaveRing.Tier tier3 = ShockwaveRing.TIERS[2];
        assertEquals(2400, ShockwaveRing.countFor(tier3, 18000), "the biggest tier: 400 * 6");
        // speed = (base + blocks * factor) * scale.
        assertEquals(1.65, ShockwaveRing.speedFor(tier1, 1000, 1.5), 1.0E-9);
        assertEquals(1.10, ShockwaveRing.speedFor(tier1, 1000, 1.0), 1.0E-9);
        assertTrue(ShockwaveRing.speedFor(tier3, 18000, 1.5) > ShockwaveRing.speedFor(tier1, 1000, 1.5),
                "a bigger charge throws its ring faster");
    }

    /** The decision the script could not make: where the charge went off. */
    @Test void theChargesSurroundingsDecideWhatMayBeDrawn() {
        double band = 4.0, buried = 0.85, downUp = 0.75, air = 12.0;
        // Something solid directly above: buried, whatever the height says.
        assertEquals(BlastClass.BURIED, ImpactClassifier.classify(true, 30.0, 1.0, 0.1, band, buried, downUp, air));
        // Or deep enough in rock to measure as enclosed.
        assertEquals(BlastClass.BURIED, ImpactClassifier.classify(false, 30.0, 1.0, 0.9, band, buried, downUp, air));
        // On the ground: either by height above the surface or by the blast being stopped downwards.
        assertEquals(BlastClass.GROUND, ImpactClassifier.classify(false, 1.5, 1.0, 0.2, band, buried, downUp, air));
        assertEquals(BlastClass.GROUND, ImpactClassifier.classify(false, 8.0, 0.3, 0.2, band, buried, downUp, air));
        // High up with nothing in the way: an air burst draws no ring at all.
        assertEquals(BlastClass.AIR, ImpactClassifier.classify(false, 30.0, 1.0, 0.2, band, buried, downUp, air));
    }

    /** A crater reads as a ground burst for free: sky above it, and the floor right under the charge. */
    @Test void aCraterIsAGroundBurst() {
        assertEquals(BlastClass.GROUND,
                ImpactClassifier.classify(false, 0.5, 0.6, 0.4, 4.0, 0.85, 0.75, 12.0));
    }

    @Test void theDownUpRatioSeparatesGroundFromAir() {
        float[] ground = {4, 4, 20, 20, 4, 4, 20, 20};   // octants 0,1,4,5 look down: stopped by the ground
        float[] air = {18, 18, 20, 20, 18, 18, 20, 20};
        assertTrue(ImpactClassifier.downUpRatio(ground) < 0.5, "a ground burst barely reaches down");
        assertTrue(ImpactClassifier.downUpRatio(air) > 0.85, "an air burst reaches almost as far down as up");
        assertEquals(1.0, ImpactClassifier.downUpRatio(new float[] {0, 0, 0, 0, 0, 0, 0, 0}), 1.0E-9,
                "a blast with nothing measured is not called a ground burst");
    }
}
