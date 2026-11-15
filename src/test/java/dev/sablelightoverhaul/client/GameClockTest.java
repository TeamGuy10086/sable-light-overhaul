package dev.sablelightoverhaul.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameClockTest {
    @Test void neverIsDue() { assertTrue(GameClock.due(0,Long.MIN_VALUE,20)); }

    @Test void intervalHasToElapse() {
        assertFalse(GameClock.due(100,100,5));
        assertFalse(GameClock.due(104,100,5));
        assertTrue(GameClock.due(105,100,5));
        assertTrue(GameClock.due(106,100,5));
    }

    /**
     * The case that broke a new world: its game time starts at 0 while the remembered stamp is the old
     * world's, so the delta is negative. That has to read as "due", not as "not for another 200000 ticks".
     */
    @Test void aClockThatWentBackwardsIsDue() {
        assertTrue(GameClock.due(0,240_000,5));
        assertTrue(GameClock.due(199_999,200_000,5));
        assertFalse(GameClock.due(200_000,200_000,5));
        assertTrue(GameClock.due(200_005,200_000,5));
    }
}
