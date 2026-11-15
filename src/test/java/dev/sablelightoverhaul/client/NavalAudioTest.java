package dev.sablelightoverhaul.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NavalAudioTest {
    @Test void nearBoundaryAndPropagationMatchTheOriginalReport() {
        assertFalse(NavalAudio.far(800, 800));
        assertTrue(NavalAudio.far(800.01, 800));
        assertEquals(20, NavalAudio.delay(343, 343));
        assertEquals(0, NavalAudio.delay(2400, 0));
        assertEquals(1200, NavalAudio.delay(2400, .01));
        assertEquals(1, NavalAudio.attenuation(0, 2400, 160));
        assertEquals(0, NavalAudio.attenuation(2400, 2400, 160));
        assertTrue(NavalAudio.attenuation(100, 2400, 160) > NavalAudio.attenuation(800, 2400, 160));
    }
}
