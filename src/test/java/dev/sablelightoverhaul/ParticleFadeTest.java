package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sablelightoverhaul.lighting.ParticleFade;
import org.junit.jupiter.api.Test;

/**
 * The fade curve both engine particles use, which is the part of "how fast does the smoke thin out" that
 * can be checked without a game.
 *
 * <p>The default has to be exactly what this mod did before the value was configurable -- full opacity
 * until 60% of the life has passed, gone at 100%, quadratic in between -- or making it configurable would
 * silently change every existing dust generator.
 */
class ParticleFadeTest {
    private static final float OPACITY = 0.5F;

    @Test void theDefaultKeepsTheOldBehaviour() {
        assertEquals(OPACITY, ParticleFade.alpha(0.0, OPACITY, 0.4F), 1.0E-6, "born at the birth opacity");
        assertEquals(OPACITY, ParticleFade.alpha(0.6, OPACITY, 0.4F), 1.0E-6, "held for the first 60%");
        // Halfway through the last 40%: the quadratic tail is at 1/4.
        assertEquals(OPACITY * 0.25F, ParticleFade.alpha(0.8, OPACITY, 0.4F), 1.0E-6);
        assertEquals(0.0F, ParticleFade.alpha(1.0, OPACITY, 0.4F), 1.0E-6, "and gone at the end");
    }

    @Test void aLongerFadeStartsEarlierAndAFullLengthOneStartsAtBirth() {
        assertTrue(ParticleFade.alpha(0.5, OPACITY, 1.0F) < OPACITY, "a fade of 1 is thinning from birth");
        assertEquals(OPACITY * 0.25F, ParticleFade.alpha(0.5, OPACITY, 1.0F), 1.0E-6);
        // A fade of 0.1 holds until 90%, so at 50% nothing has changed yet, and at 95% it is exactly
        // halfway through the fade (a quarter of the birth opacity) -- the tail is where it goes.
        assertEquals(OPACITY, ParticleFade.alpha(0.5, OPACITY, 0.1F), 1.0E-6);
        assertEquals(OPACITY * 0.25F, ParticleFade.alpha(0.95, OPACITY, 0.1F), 1.0E-6);
        assertTrue(ParticleFade.alpha(0.98, OPACITY, 0.1F) < OPACITY * 0.25F, "and then it goes quickly");
    }

    @Test void itIsMonotoneAndNeverExceedsTheBirthOpacity() {
        float previous = Float.MAX_VALUE;
        for (int i = 0; i <= 100; i++) {
            float alpha = ParticleFade.alpha(i / 100.0, OPACITY, 0.4F);
            assertTrue(alpha <= OPACITY + 1.0E-6, "never brighter than at birth: " + alpha);
            assertTrue(alpha <= previous + 1.0E-6, "never brighter than the tick before: " + alpha);
            assertTrue(alpha >= 0.0F, "never negative: " + alpha);
            previous = alpha;
        }
        assertEquals(0.0F, previous, 1.0E-6, "and it ends at nothing");
    }

    /** A fade of zero is a legitimate "hold it and then let go", not a division by zero. */
    @Test void aZeroFadeHoldsToTheEndAndStillDies() {
        assertEquals(OPACITY, ParticleFade.alpha(0.99, OPACITY, 0.0F), 1.0E-6);
        assertEquals(0.0F, ParticleFade.alpha(1.0, OPACITY, 0.0F), 1.0E-6);
    }
}
