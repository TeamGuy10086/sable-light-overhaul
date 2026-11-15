package dev.sablelightoverhaul.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectileStreakContinuityTest {
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
