package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.HostileShellPayload;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostileMathShellsTest {
    @Test void confirmedPositionInterpolatesOnceThenHoldsWithoutExtrapolating() {
        var shell = new HostileShellPayload.Shell(0, UUID.randomUUID(), HostileShellPayload.Model.AP_127,
            new Vec3(30, 70, 40), new Vec3(34, 68, 48), false, true);
        var sample = new HostileMathShells.Track(shell, Long.MIN_VALUE / 2, 100);
        assertEquals(new Vec3(32, 69, 44), sample.position(100, .5f));
        assertEquals(shell.current(), sample.position(101, .2f));
    }

    @Test void terminalRepeatDoesNotLayAnotherTracerSegment() {
        var model = HostileShellPayload.Model.HE_203;
        var id = UUID.randomUUID();
        var end = new Vec3(0, 0, 3);
        var segment = new HostileShellPayload.Shell(0, id, model, Vec3.ZERO, end, false, true);
        var terminalRepeat = new HostileShellPayload.Shell(0, id, model, Vec3.ZERO, end, true, true);
        var finalStep = new HostileShellPayload.Shell(0, id, model, end, new Vec3(0, 0, 4), true, true);
        assertTrue(HostileMathShells.sameSegment(segment, terminalRepeat));
        assertFalse(HostileMathShells.sameSegment(segment, finalStep));
    }

    @Test void terminalStepKeepsTheCarrierAndEndsAtTheAuthoritativePoint() {
        var id = UUID.randomUUID();
        var start = Vec3.ZERO;
        var confirmed = new Vec3(1, 2, 3);
        var stop = new Vec3(2, 3, 4);
        var live = new HostileShellPayload.Shell(0, id, HostileShellPayload.Model.HE_76, start, confirmed, false, true);
        var terminal = live.terminalAt(stop);
        assertEquals(id, terminal.uuid());
        assertEquals(confirmed, terminal.previous());
        assertEquals(stop, terminal.current());
        assertTrue(terminal.terminal());
        assertTrue(terminal.tracer());
        assertTrue(HostileMathShells.sameSegment(live, live.terminalAt(confirmed)));
    }

    @Test void onlyTheMatchingFinalFrameMayFollowTheApiEnd() {
        var id = UUID.randomUUID();
        var position = new Vec3(0, 0, 5);
        for (var model : HostileShellPayload.Model.values()) {
            var live = new HostileShellPayload.Shell(0, id, model, Vec3.ZERO, position, false, true);
            var pending = new HostileMathShells.PendingEnd(position, null);
            assertTrue(pending.accepts(live.terminalAt(position)));
            assertFalse(pending.accepts(live));
            assertFalse(pending.accepts(live.terminalAt(new Vec3(0, 0, 6))));
            assertFalse(pending.accepts(dev.sablelightoverhaul.HostileMathFlightPayload.handoff(id, model, position)));
        }
    }

    @Test void onlyTheTwoProximityHeModelsRequireAnExplicitHandoff() {
        assertEquals(java.util.List.of(HostileShellPayload.Model.HE_127, HostileShellPayload.Model.HE_76),
            java.util.Arrays.stream(HostileShellPayload.Model.values()).filter(HostileShellPayload.Model::proximityHe).toList());
    }

    @Test void onlyTheSixStableNavalTypesAreRecognized() {
        for (var model : HostileShellPayload.Model.values()) {
            assertSame(model, HostileShellPayload.Model.forType(model.id().toString()));
        }
        assertNull(HostileShellPayload.Model.forType("createbigcannons:he_shell"));
        assertNull(HostileShellPayload.Model.forType("sablehostile:tracer_grapeshot"));
    }
}
