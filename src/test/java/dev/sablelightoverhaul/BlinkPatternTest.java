package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.BlinkPattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blink text is a small language the page lets a player type, so the parser is the whole feature: the
 * server stores what this returns and the client counts ticks with it.
 */
class BlinkPatternTest {
    @Test
    void emptyMeansSteady() {
        assertEquals("", BlinkPattern.parse(""));
        assertEquals("", BlinkPattern.parse("   "));
        assertTrue(BlinkPattern.of("").litAt(0));
        assertTrue(BlinkPattern.of("").litAt(9_999));
    }

    @Test
    void normalisesWhitespace() {
        assertEquals("4,4,4,28", BlinkPattern.parse(" 4 , 4,4 ,28 "));
    }

    /** on 4, off 4: on at 0..3, off at 4..7, on again at 8. */
    @Test
    void alternatesStartingWithOn() {
        BlinkPattern p = BlinkPattern.of("4,4");
        assertEquals(8, p.total());
        assertTrue(p.litAt(0));
        assertTrue(p.litAt(3));
        assertFalse(p.litAt(4));
        assertFalse(p.litAt(7));
        assertTrue(p.litAt(8));
        assertFalse(p.litAt(-1)); // floorMod: one tick before zero is the end of the off half
    }

    @Test
    void rejectsWhatThePageShowsAsAnError() {
        assertThrows(IllegalArgumentException.class, () -> BlinkPattern.parse("4,"));
        assertThrows(IllegalArgumentException.class, () -> BlinkPattern.parse("0"));
        assertThrows(IllegalArgumentException.class, () -> BlinkPattern.parse("4,a"));
        assertThrows(IllegalArgumentException.class, () -> BlinkPattern.parse("1201"));
        StringBuilder tooMany = new StringBuilder();
        for (int i = 0; i < BlinkPattern.MAX_SEGMENTS + 1; i++) tooMany.append(i == 0 ? "1" : ",1");
        assertThrows(IllegalArgumentException.class, () -> BlinkPattern.parse(tooMany.toString()));
    }

    /** A malformed pattern read back from an old save must not turn a lamp off: it falls back to steady. */
    @Test
    void malformedTextFallsBackToSteady() {
        assertTrue(BlinkPattern.of("4,a").litAt(0));
        assertTrue(BlinkPattern.of("4,a").litAt(123));
    }
}
