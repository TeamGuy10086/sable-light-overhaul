package dev.sablelightoverhaul.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FarContactFocusTest {
    @Test void focusUsesAnAngularConeAtAnyDistance() {
        double cosine = Math.cos(Math.toRadians(10));
        for (double distance : new double[] {160, 400, 1000}) {
            assertTrue(FarContactPortraits.inFocus(distance * Math.cos(Math.toRadians(9.9)), distance, cosine));
            assertFalse(FarContactPortraits.inFocus(distance * Math.cos(Math.toRadians(10.1)), distance, cosine));
            assertFalse(FarContactPortraits.inFocus(-distance, distance, cosine));
        }
    }

    @Test void healthUsesWholeValuesWherePossibleAndRetainsFractions() {
        assertEquals("12.5 / 20", FarContactPortraits.healthText(new dev.sablelightoverhaul.FarContacts.Health(12.5f, 20)));
        assertNull(FarContactPortraits.healthText(null));
    }

    @Test void focusedSizeProjectsToTheRequestedPixelsAcrossFovAndDepth() {
        for (double focal : new double[] {240, 700}) {
            for (double depth : new double[] {60, 512, 1200}) {
                double size = FarContactPortraits.projectedSize(depth, focal, 24);
                assertEquals(24, size * focal / depth, 1e-9);
            }
        }
    }
}
