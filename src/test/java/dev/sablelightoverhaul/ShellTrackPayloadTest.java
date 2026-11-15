package dev.sablelightoverhaul;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The trail feed carries one tick of flight: the shell's two endpoints and its velocity.
 *
 * <p>What this guards against is the family of guesses that came before it: coordinates quantised into
 * 16 bits (clamped to +-8 blocks, so every remote shell was drawn near the world origin), then relative to
 * a reference point both sides had to agree on, and alongside them a lookahead and an extrapolated step
 * that were each a second estimate of where the shell would be. None of that is needed: CBC's own trail
 * interpolates between the shell's previous position and its current one, and the frame drift is the
 * shell's own velocity.
 */
class ShellTrackPayloadTest {
    @Test void theEndpointsAndMotionArriveExactly() {
        var shell = new ShellTrackPayload.Shell(7,
            2002.875, 70.25, -1497.5,
            2000.125, 70.5, -1500.0625,
            2.75, -0.25, 2.5625);
        assertEquals(2002.875, shell.x(), 0.0);
        assertEquals(70.25, shell.y(), 0.0);
        assertEquals(-1497.5, shell.z(), 0.0);
        assertEquals(2000.125, shell.px(), 0.0);
        assertEquals(70.5, shell.py(), 0.0);
        assertEquals(-1500.0625, shell.pz(), 0.0);
        assertEquals(2.75, shell.mx(), 0.0);
        assertEquals(-0.25, shell.my(), 0.0);
        assertEquals(2.5625, shell.mz(), 0.0);
    }

    @Test void thereIsNoRangeLimit() {
        // The old encoding reached 7.999 blocks and then clamped; this one has no ceiling to hit.
        var shell = new ShellTrackPayload.Shell(1, 3.0E7, 320, -3.0E7, 3.0E7, 320, -3.0E7, 0, 0, 0);
        assertEquals(3.0E7, shell.x(), 0.0);
        assertEquals(-3.0E7, shell.z(), 0.0);
    }

    @Test void aStepIsMeasuredFromTheTwoEndsAlone() {
        // The client decides how many frames a tick needs from the distance between the endpoints, so the
        // step must be recoverable without any extra field on the wire.
        var shell = new ShellTrackPayload.Shell(1, 3, 4, 0, 0, 0, 0, 0, 0, 0);
        double dx = shell.x() - shell.px();
        double dy = shell.y() - shell.py();
        assertEquals(5.0, Math.hypot(dx, dy), 1.0E-9);
    }
}