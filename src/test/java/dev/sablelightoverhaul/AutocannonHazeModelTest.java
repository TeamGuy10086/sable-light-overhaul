package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AutocannonHazeModelTest {
    @Test void continuedFireAddsMassWithoutMakingEachCarrierOpaque() {
        var mass = new HazeMass(100, .1f);
        for (int i = 0; i < 80; i++) mass.add(i / 2);
        assertTrue(mass.alpha(40, 0) > .95f);
        assertEquals(.32f, AutocannonHazeModel.opacity(mass.alpha(40, 0), .1f), .005f);
        assertTrue(AutocannonHazeModel.opacity(mass.alpha(125, 0), .1f) < .32f);
        assertEquals(0, AutocannonHazeModel.opacity(mass.alpha(139, 0), .1f));
        assertEquals(.8f, AutocannonHazeModel.opacity(1, .8f));
    }

    @Test void cameraFadeAndExpansionStayContinuousAndBounded() {
        assertEquals(0, AutocannonHazeModel.cameraFade(0, 4));
        assertEquals(1, AutocannonHazeModel.cameraFade(3, 4));
        assertTrue(AutocannonHazeModel.cameraFade(1, 4) < AutocannonHazeModel.cameraFade(2, 4));
        assertTrue(AutocannonHazeModel.growth(4, 100) > AutocannonHazeModel.growth(0, 100));
        assertEquals(AutocannonHazeModel.growth(14, 100), AutocannonHazeModel.growth(400, 100));
    }
}
