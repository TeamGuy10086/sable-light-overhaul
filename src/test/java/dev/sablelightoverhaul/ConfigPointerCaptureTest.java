package dev.sablelightoverhaul;

import dev.sablelightoverhaul.client.ui.ConfigPointerCapture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigPointerCaptureTest {
    @Test void popupWinsOverTheSliderAndTitle() {
        assertFalse(ConfigPointerCapture.manual(0,true));
        assertTrue(ConfigPointerCapture.manual(0,false));
        assertFalse(ConfigPointerCapture.manual(1,false));
        assertFalse(ConfigPointerCapture.manual(2,false));
    }
    @Test void partiallyVisibleSliderRemainsUsableInsideItsClip() {
        assertTrue(ConfigPointerCapture.hit(15,22,10,15,40,12,0,20,80,100));
        assertFalse(ConfigPointerCapture.hit(15,18,10,15,40,12,0,20,80,100));
    }
    @Test void hiddenAndZeroAreaBarsNeverCapture() {
        assertFalse(ConfigPointerCapture.hit(15,130,10,125,40,12,0,20,80,100));
        assertFalse(ConfigPointerCapture.hit(15,22,15,20,0,12,0,20,80,100));
    }
}
