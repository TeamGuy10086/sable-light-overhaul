package dev.sablelightoverhaul.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectileStreakContinuityTest {
    @Test void largeServerTickPreservesEveryFrameAndTickTransition() {
        long start = 308_673_963L;
        for (long tick = start; tick < start + 64; tick++) {
            double previous = ProjectileStreaks.interpolatedTick(tick, 0);
            assertEquals((double) (tick - 1), previous);
            for (int quarter = 1; quarter <= 4; quarter++) {
                double current = ProjectileStreaks.interpolatedTick(tick, quarter / 4f);
                assertEquals(0.25, current - previous);
                previous = current;
            }
            assertEquals(previous, ProjectileStreaks.interpolatedTick(tick + 1, 0));
        }
    }

    @Test void firstSegmentStartsAtItsPhaseAndClampsAtTheConfirmedEndpoint() {
        for (int phase = 0; phase <= 4; phase++) {
            var start = Vec3.ZERO;
            var contact = new Vec3(2, 0, 0);
            double duration = dev.sablelightoverhaul.FlightVisualClock.firstSegmentDuration(phase);
            var first = new ProjectileStreaks.Step(start, contact, 100, duration, phase);
            double begins = 99 + phase / 5.0;
            assertEquals(0, first.fraction(begins - .01));
            assertEquals(.5, first.fraction(begins + duration * .5), 1e-10);
            assertEquals(1, first.fraction(100), 1e-10);
            assertEquals(1, first.fraction(101));
            var later = new ProjectileStreaks.Step(contact, new Vec3(8, 0, 0), 101, 1);
            assertEquals(.5, later.fraction(100.5));
        }
    }

    @Test void mathematicalSamplesUseTheirOwnTicksEvenWhenReceivedTogether() {
        var steps = new java.util.ArrayDeque<ProjectileStreaks.Step>();
        var muzzle = Vec3.ZERO;
        var firstEndpoint = new Vec3(10, 0, 0);
        var secondEndpoint = new Vec3(28, 1, 0);
        ProjectileStreaks.appendMathStep(steps, muzzle, firstEndpoint, 120, 2);
        ProjectileStreaks.appendMathStep(steps, firstEndpoint, secondEndpoint, 121, 0);

        var first = steps.getLast();
        var second = steps.getFirst();
        assertEquals(0, first.fraction(dev.sablelightoverhaul.FlightVisualClock.time(120, false, .30)));
        assertEquals(.5, first.fraction(dev.sablelightoverhaul.FlightVisualClock.time(120, false, .70)), 1e-9);
        assertEquals(1, first.fraction(dev.sablelightoverhaul.FlightVisualClock.time(120, true, .20)));
        assertEquals(first.to(), second.from());
        assertEquals(0, second.fraction(dev.sablelightoverhaul.FlightVisualClock.time(121, false, 0)));
        assertEquals(.5, second.fraction(dev.sablelightoverhaul.FlightVisualClock.time(121, false, .5)), 1e-9);

        ProjectileStreaks.appendMathStep(steps, new Vec3(80, 0, 0), new Vec3(98, 0, 0), 125, 0);
        assertEquals(1, steps.size(), "a jump in supplied simulation ticks cannot draw the missing space");
    }

    @Test void terminalContactUsesTheServerFractionAndCutsOffLaterPrediction() {
        var steps = new java.util.ArrayDeque<ProjectileStreaks.Step>();
        var muzzle = Vec3.ZERO;
        var firstEndpoint = new Vec3(10, 0, 0);
        ProjectileStreaks.appendMathStep(steps, muzzle, firstEndpoint, 120, 2);
        ProjectileStreaks.appendMathStep(steps, firstEndpoint, new Vec3(28, 0, 0), 121, 0);
        ProjectileStreaks.clipMathContact(steps, 120, new Vec3(5, 0, 0), .5, 2);

        assertEquals(1, steps.size());
        var stop = steps.getFirst();
        assertEquals(new Vec3(5, 0, 0), stop.to());
        assertEquals(119.7, stop.time(), 1e-9);
        assertEquals(.5, stop.fraction(119.55), 1e-9);
        assertEquals(1, stop.fraction(121));

        ProjectileStreaks.appendMathStep(steps, muzzle, firstEndpoint, 120, 2);
        ProjectileStreaks.clipMathContact(steps, 121, new Vec3(14.5, 0, 0), .25, 0);
        assertEquals(firstEndpoint, steps.getFirst().from(), "an on-time contact joins the preceding sample");
        assertEquals(120.25, steps.getFirst().time(), 1e-9);
    }

    @Test void lowDisplayRateSweepsOnlyKnownFlightWithinTwoTicks() {
        double cutoff = ProjectileStreaks.frameCutoff(98, 103.3, 104, false);
        assertEquals(101.3, cutoff, 1e-9);
        assertEquals(102.3, ProjectileStreaks.frameCutoff(103, 103.3, 104, false), 1e-9);
        assertEquals(102.3, ProjectileStreaks.frameCutoff(104, 103.3, 104, false), 1e-9);
        assertEquals(102.3, ProjectileStreaks.frameCutoff(98, 103.3, 104, true), 1e-9);

        var steps = new java.util.ArrayDeque<ProjectileStreaks.Step>();
        ProjectileStreaks.appendMathStep(steps, Vec3.ZERO, new Vec3(10, 0, 0), 103, 4);
        var onlyLaunch = steps.getFirst();
        assertEquals(0, onlyLaunch.fraction(cutoff), "exposure cannot extend a phased shot before launch");
        ProjectileStreaks.appendMathStep(steps, new Vec3(20, 0, 0), new Vec3(30, 0, 0), 105, 0);
        assertEquals(1, steps.size(), "exposure has no geometry to bridge a missing sample");
    }

    @Test void aShotEndingBetweenFramesIsExposedOnceAndThenExpires() {
        var steps = new java.util.ArrayDeque<ProjectileStreaks.Step>();
        ProjectileStreaks.appendMathStep(steps, Vec3.ZERO, new Vec3(18, 0, 0), 102, 0);
        ProjectileStreaks.clipMathContact(steps, 102, new Vec3(9, 0, 0), .5, 0);
        var hit = steps.getFirst();
        double cutoff = ProjectileStreaks.frameCutoff(100.5, 102.7, hit.time(), false);
        assertTrue(hit.fraction(cutoff) < hit.fraction(102.7));
        assertEquals(new Vec3(9, 0, 0), hit.to());
        double next = ProjectileStreaks.frameCutoff(102.7, 103.2, hit.time(), false);
        assertEquals(hit.fraction(next), hit.fraction(103.2), "no repeated exposure after the line expires");
    }

    @Test void packetCoalescingKeepsTheEarliestLaunchPhase() {
        var steps = new java.util.ArrayDeque<ProjectileStreaks.Step>();
        var contact = new Vec3(2, 0, 0);
        steps.add(new ProjectileStreaks.Step(Vec3.ZERO, contact, 100, .2, 4));
        ProjectileStreaks.appendArrivalBurst(steps, contact, new Vec3(4, 0, 0), 100);
        assertEquals(0, steps.getLast().fraction(99.79));
        assertEquals(.5, steps.getLast().fraction(99.85), 1e-9);
        assertEquals(1, steps.getFirst().fraction(100), 1e-9);
    }

    @Test void burstKeepsEachConfirmedBendInOrderWithinOneDisplayTick() {
        var steps = new java.util.ArrayDeque<ProjectileStreaks.Step>();
        var a = Vec3.ZERO;
        var b = new Vec3(3, 0, 0);
        var c = new Vec3(6, 1, 0);
        var d = new Vec3(9, 1, 0);
        var older = new ProjectileStreaks.Step(new Vec3(-3, 0, 0), a, 99, 1);
        steps.add(new ProjectileStreaks.Step(a, b, 100, 1));
        steps.add(older);
        ProjectileStreaks.appendArrivalBurst(steps, b, c, 100);
        ProjectileStreaks.appendArrivalBurst(steps, c, d, 100);
        var ordered = steps.reversed().stream().toList();
        org.junit.jupiter.api.Assertions.assertEquals(older, ordered.get(0));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(b, c, d),
            ordered.subList(1, 4).stream().map(ProjectileStreaks.Step::to).toList());
        org.junit.jupiter.api.Assertions.assertEquals(.5, ordered.get(2).fraction(99.5), 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(1, ordered.get(3).fraction(100), 1e-9);
    }

    @Test void jitterHoldStopsAtTheConfirmedHeadAndIsBypassedForFinishedTrajectories() {
        org.junit.jupiter.api.Assertions.assertEquals(99.5, ProjectileStreaks.jitterClock(99.5, 100, true));
        org.junit.jupiter.api.Assertions.assertEquals(100, ProjectileStreaks.jitterClock(101.5, 100, true));
        org.junit.jupiter.api.Assertions.assertEquals(101, ProjectileStreaks.jitterClock(103, 100, true));
        org.junit.jupiter.api.Assertions.assertEquals(101.5, ProjectileStreaks.jitterClock(101.5, 100, false));
    }
    @Test void reconnectsShortForwardGapsOnly() {
        var visible = new Vec3(20, 0, 0);
        var from = new Vec3(19, 1, 0);
        var to = new Vec3(29, 1, 0);
        var motion = to.subtract(from);
        assertTrue(ProjectileStreaks.canReconnect(2, visible, from, to, motion));
        assertTrue(ProjectileStreaks.canReconnect(3, visible, from, to, motion));
        assertFalse(ProjectileStreaks.canReconnect(4, visible, from, to, motion));
        assertFalse(ProjectileStreaks.canReconnect(2, visible, from.add(0, 40, 0), to, motion));
        assertFalse(ProjectileStreaks.canReconnect(2, visible, from, new Vec3(18, 1, 0), motion));
    }

    @Test void autocannonCorrectionCannotBridgeANearVerticalJump() {
        var from = new Vec3(0, 0, 0);
        var motion = new Vec3(1, 0, 0);
        var to = from.add(motion);
        var misplacedHead = new Vec3(0, 2, 0);
        assertTrue(ProjectileStreaks.canReconnect(2, misplacedHead, from, to, motion));
        assertFalse(ProjectileStreaks.followsAutocannonHeading(misplacedHead, to, motion));
        assertTrue(ProjectileStreaks.followsAutocannonHeading(new Vec3(-1, .1, 0), to, motion));
        assertTrue(ProjectileStreaks.followsAutocannonHeading(from, new Vec3(0, 3, 0), new Vec3(0, 1, 0)));
    }
}
