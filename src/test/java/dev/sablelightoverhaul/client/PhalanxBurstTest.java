package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.PhalanxBurstPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhalanxBurstTest {
    private static PhalanxBurstPayload burst(int count, int first) {
        return new PhalanxBurstPayload(ResourceLocation.parse("minecraft:overworld"), new Vec3(20, 70, -13),
            new Vec3(0, 0, 1), -1234, count, first, 18, 600);
    }

    @Test void optionalWireRoundTripAndPartialSalvos() {
        for (int count = 1; count <= 3; count++) {
            var p = burst(count, 0);
            assertTrue(p.valid());
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                PhalanxBurstPayload.CODEC.encode(buf,p);
                assertEquals(p, PhalanxBurstPayload.CODEC.decode(buf));
                assertFalse(buf.isReadable());
            } finally { buf.release(); }
        }
        assertTrue(burst(2, 1).valid());
        assertTrue(burst(1, 1).valid());
        assertFalse(burst(3, 1).valid());
        assertFalse(burst(0, 0).valid());
        assertFalse(burst(3, Integer.MAX_VALUE).valid());
    }

    @Test void launchSpacingIsTwentyMillisecondsAndStopsAtRange() {
        for (int first : new int[]{0, 1}) {
            for (int phase = first; phase <= 4; phase += 2) {
                var r = new PhalanxBurstClient.Round(-5, Vec3.ZERO, new Vec3(1,0,0), 110, phase, 18, 600);
                assertEquals(0, r.distance(109));
                assertEquals(18 * (1 - phase / 5.0), r.distance(110), 1e-9);
                assertEquals(18, r.distance(111) - r.distance(110), 1e-9);
                assertEquals(600, r.distance(156));
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {128, 512, 1024, 1536})
    void eightGunsKeepMuzzleSamplesDuringSustainedOverload(int budget) {
        var queue = new java.util.ArrayDeque<PhalanxBurstClient.Round>();
        var visible = new java.util.HashSet<Long>();
        long foreignId = 42;
        visible.add(foreignId);
        long id = -1000;
        for (int tick = 0; tick < 160; tick++) {
            var launches = new java.util.ArrayList<Long>();
            for (int gun = 0; gun < 8; gun++) {
                int first = (tick + gun) % 2;
                for (int phase = first; phase < 5; phase += 2) {
                    PhalanxBurstClient.trim(queue, budget - 1, visible::remove);
                    launches.add(--id);
                    queue.addLast(new PhalanxBurstClient.Round(id, new Vec3(gun,0,0), new Vec3(0,0,1),
                        tick, phase, 18, 600));
                }
            }
            int available = budget - visible.size();
            for (var r : queue) if (visible.contains(r.id())) available++;
            PhalanxBurstClient.trim(queue, available, visible::remove);
            for (var r : queue) {
                visible.add(r.id());
                if (r.firstTick() == tick) assertTrue(r.distance(tick) > 0 && r.distance(tick) <= 18);
            }
            assertTrue(visible.containsAll(launches));
            assertTrue(visible.contains(foreignId));
            assertTrue(visible.size() <= budget);
        }
        assertEquals(budget, visible.size());
    }

    @Test void frameExposureIncludesLatePhaseSkippedBetweenFrames() {
        var steps = new java.util.ArrayDeque<ProjectileStreaks.Step>();
        ProjectileStreaks.appendMathStep(steps, Vec3.ZERO, new Vec3(3.6,0,0), 401, 4);
        var first = steps.getFirst();
        assertEquals(0, first.fraction(400.79));
        assertTrue(first.fraction(400.9) > 0);
        double cutoff = ProjectileStreaks.frameCutoff(400.75, 402.1, 402, false);
        assertEquals(0, first.fraction(cutoff));
        assertEquals(1, first.fraction(402.1));
    }

    @Test void hologramReadsPhasedWorldSegmentsWithoutAWorldRenderer() {
        var origin = new Vec3(30, 40, 50);
        var r = new PhalanxBurstClient.Round(-7, origin, new Vec3(0,0,1), 101, 4, 30, 36);
        var ends = new java.util.ArrayList<Vec3>();
        java.util.function.BiConsumer<Vec3,Vec3> sink = (a,b) -> { ends.add(a); ends.add(b); };
        PhalanxBurstClient.emitSegment(r, 100.75, sink);
        assertTrue(ends.isEmpty());
        PhalanxBurstClient.emitSegment(r, 100.9, sink);
        assertEquals(origin,ends.getFirst());
        assertEquals(53,ends.getLast().z,1e-8);
        ends.clear();
        PhalanxBurstClient.emitSegment(r, 102, sink);
        assertEquals(86,ends.getLast().z,1e-8);
        assertEquals(18,ends.getFirst().distanceTo(ends.getLast()),1e-8);
        ends.clear();
        PhalanxBurstClient.emitSegment(r, 104, sink);
        PhalanxBurstClient.emitSegment(r, 150, sink);
        assertTrue(ends.isEmpty());
    }
}
