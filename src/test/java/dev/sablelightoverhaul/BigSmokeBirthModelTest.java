package dev.sablelightoverhaul;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavioural invariants for cold-slot classification, cold growth and curl displacement. */
final class BigSmokeBirthModelTest {
    private static final double EPS = 1e-9;

    /** Cold slots at a 20% rate: exactly one per block of five and nearest integer over every prefix. */
    @Test
    void coldFractionOneFifthPlacesExactlyOneColdSlotPerBlock() {
        int[] cold = coldSlots(0.2, 256);

        for (int block = 0; block < 256 / 5; block++) {
            int inBlock = 0;
            for (int i = block * 5; i < block * 5 + 5; i++) inBlock += cold[i];
            assertEquals(1, inBlock, "block " + block + " must hold exactly one cold slot");
        }

        int prefix = 0;
        for (int i = 0; i < 256; i++) {
            prefix += cold[i];
            // Nearest-integer target (half up) for the expected 20% rate.
            assertEquals((int) Math.floor((i + 1) * 0.2 + 0.5), prefix, "cold count over prefix 0.." + i);
        }
    }

    @Test
    void coldFractionExtremesAndNegativeIndicesAreInert() {
        for (int i = 0; i < 128; i++) {
            assertFalse(BigSmokeBirthModel.isCold(i, 0.0), "fraction 0 must never be cold at " + i);
            assertTrue(BigSmokeBirthModel.isCold(i, 1.0), "fraction 1 must always be cold at " + i);
        }

        for (int index = -1; index >= -64; index--) {
            for (double fraction : new double[] {0.0, 0.2, 0.5, 1.0}) {
                assertFalse(BigSmokeBirthModel.isCold(index, fraction), "negative index " + index);
            }
        }
    }

    @Test
    void classificationDependsOnlyOnAbsoluteIndexNotBatchLayout() {
        double fraction = 0.35;
        int total = 300;

        int[] reference = coldSlots(fraction, total);
        int[] globalCursor = new int[1];
        int[] batched = new int[total];
        int absolute = 0;
        for (int batch : new int[] {1, 7, 0, 13, 40, 2, 57, total}) {
            for (int k = 0; k < batch && absolute < total; k++) {
                // Each particle is classified by its absolute index; the batch only decides when it is asked.
                batched[absolute] = BigSmokeBirthModel.isCold(absolute, fraction) ? 1 : 0;
                absolute++;
            }
        }
        globalCursor[0] = absolute;
        assertEquals(total, globalCursor[0]);

        assertTrue(java.util.Arrays.equals(reference, batched), "batching must not change any classification");
    }

    @Test
    void coldGrowthIsLargerAtBirthAndStaysSoWhileFiniteAndMonotonic() {
        double defaultScale = 1.5;
        double expansion = 0.0;
        double[] ages = {0.0, 0.05, 0.25, 1.0, 2.0, 4.0, 6.0, 8.0};

        for (double amount : new double[] {0.0, 0.4, 0.9}) {
            double previousHot = Double.NEGATIVE_INFINITY;
            double previousCold = Double.NEGATIVE_INFINITY;
            for (double age : ages) {
                double hot = BigSmokeModel.growth(age, amount);
                double cold = BigSmokeBirthModel.growth(age, amount, true, defaultScale);

                assertTrue(Double.isFinite(cold), "cold growth finite at age " + age);
                assertTrue(cold > hot, "cold growth exceeds hot at age " + age + " (expansion " + amount + ")");
                assertTrue(cold >= previousCold, "cold growth is monotonic at age " + age);
                assertTrue(hot >= previousHot, "hot growth is monotonic at age " + age);
                previousCold = cold;
                previousHot = hot;
            }
            assertTrue(previousCold >= 0.25, "cold growth stays positive");
        }

        for (double age : ages) {
            assertEquals(BigSmokeModel.growth(age, expansion), BigSmokeBirthModel.growth(age, expansion, false, defaultScale),
                    "non-cold growth must be the untouched hot envelope");
        }

        assertTrue(BigSmokeBirthModel.growth(0, expansion, true, 1.0) > BigSmokeModel.growth(0, expansion),
                "cold cloud is larger at birth even at unit scale");
    }

    @Test
    void curlStartsFlatBoundedAndConvergesInsteadOfRotating() {
        double[] phases = {0.0, 0.3, 1.1, Math.PI / 2, 2.5, Math.PI, 4.9, 6.0};
        double[] strengths = {0.0, 0.25, 1.0, 3.0};
        double[] ages = {0.0, 0.1, 0.5, 1.0, 2.0, 4.0, 8.0, 30.0};

        for (double phase : phases) {
            for (double strength : strengths) {
                assertEquals(0.0, BigSmokeBirthModel.curlSide(0.0, phase, strength), 1e-12, "curl starts at zero (side)");
                assertEquals(0.0, BigSmokeBirthModel.curlUp(0.0, phase, strength), 1e-12, "curl starts at zero (up)");
            }
        }

        for (double phase : phases) {
            assertEquals(0.0, BigSmokeBirthModel.curlSide(3.0, phase, 0.0), 1e-12);
            assertEquals(0.0, BigSmokeBirthModel.curlUp(3.0, phase, 0.0), 1e-12);
        }

        for (double strength : strengths) {
            for (double phase : phases) {
                for (double age : ages) {
                    double side = BigSmokeBirthModel.curlSide(age, phase, strength);
                    double up = BigSmokeBirthModel.curlUp(age, phase, strength);
                    assertTrue(Double.isFinite(side) && Double.isFinite(up), "curl finite at age " + age);
                    assertTrue(Math.abs(side) <= 2.0 * strength + EPS, "side displacement bounded at age " + age);
                    assertTrue(Math.abs(up) <= 2.0 * strength + EPS, "up displacement bounded at age " + age);
                }
            }
        }

        if (strengths[strengths.length - 1] > 0.0) {
            for (double phase : phases) {
                double early = Math.hypot(BigSmokeBirthModel.curlSide(0.5, phase, 1.0),
                        BigSmokeBirthModel.curlUp(0.5, phase, 1.0));
                double late = Math.hypot(BigSmokeBirthModel.curlSide(8.0, phase, 1.0),
                        BigSmokeBirthModel.curlUp(8.0, phase, 1.0));
                double settled = Math.hypot(BigSmokeBirthModel.curlSide(30.0, phase, 1.0),
                        BigSmokeBirthModel.curlUp(30.0, phase, 1.0));
                assertTrue(late <= 2.0 * 1.0 + EPS, "late curl within bound");
                if (phase != 0.0) {
                    assertEquals(late, settled, .003, "curl converges to an endpoint rather than continuing full rotations at phase " + phase);
                }
                assertTrue(early <= 2.0 * 1.0 + EPS);
            }
        }
    }

    private static int[] coldSlots(double fraction, int count) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            slots.add(BigSmokeBirthModel.isCold(i, fraction) ? 1 : 0);
        }
        int[] out = new int[slots.size()];
        for (int i = 0; i < out.length; i++) out[i] = slots.get(i);
        return out;
    }
}
