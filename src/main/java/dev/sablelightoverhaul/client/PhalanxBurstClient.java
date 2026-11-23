package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.PhalanxBurstPayload;
import java.util.ArrayDeque;
import java.util.function.LongConsumer;
import java.util.function.BiConsumer;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** A local straight-line approximation. Only the existing streak renderer owns drawable geometry. */
public final class PhalanxBurstClient {
    private PhalanxBurstClient() {}
    private static final int SAMPLE_LIMIT = 1024;
    private static final ArrayDeque<Round> samples = new ArrayDeque<>();
    private static final ArrayDeque<Round> rounds = new ArrayDeque<>();
    private static ClientLevel owner;
    private static long nextId = -(1L << 48);
    record Round(long id, Vec3 start, Vec3 direction, long firstTick, int phase, double speed, double range) {
        double distance(double tick) {
            return Math.clamp((tick - firstTick + 1 - phase * .2) * speed, 0, range);
        }
    }
    static void receive(PhalanxBurstPayload burst) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(burst.dimension()) || !burst.valid()) return;
        resetLevel(level);
        int budget = ExtraConfig.STREAK_MAX.get();
        var random = new Random(burst.seed());
        Vec3 axis = burst.axis();
        Vec3 right = axis.cross(Math.abs(axis.y) > .9 ? new Vec3(1,0,0) : new Vec3(0,1,0)).normalize();
        Vec3 up = axis.cross(right);
        for (int i = 0; i < burst.count(); i++) {
            Vec3 dir = axis.add(right.scale((random.nextDouble() - .5) * .008))
                .add(up.scale((random.nextDouble() - .5) * .008)).normalize();
            var round = new Round(nextId--, burst.muzzle(), dir, ProjectileStreaks.visualTick() + 1,
                burst.firstPhaseFifths() + 2 * i, burst.speed(), burst.range());
            if (samples.size() == SAMPLE_LIMIT) samples.removeFirst();
            samples.addLast(round);
            if (ExtraConfig.STREAK_ENABLED.get()) {
                trim(rounds, budget - 1, ProjectileStreaks::retire);
                rounds.addLast(round);
            }
        }
    }

    // Oldest local samples are farthest into their flight. Only retire IDs issued by this queue.
    static void trim(ArrayDeque<Round> queue, int keep, LongConsumer retire) {
        while (queue.size() > keep) retire.accept(queue.removeFirst().id);
    }

    private static void resetLevel(ClientLevel level) {
        if (owner != level) { rounds.clear(); samples.clear(); owner = level; }
    }

    public static void forEachSegment(ClientLevel level, float partialTick, BiConsumer<Vec3, Vec3> sink) {
        if (level == null || level != owner || level != Minecraft.getInstance().level || !Float.isFinite(partialTick)) return;
        double time = ProjectileStreaks.interpolatedTick(ProjectileStreaks.visualTick(), partialTick);
        for (Round sample : samples) emitSegment(sample, time, sink);
    }

    static void emitSegment(Round sample, double time, BiConsumer<Vec3, Vec3> sink) {
        if (time >= sample.firstTick + PhalanxBurstPayload.MAX_TICKS - 1) return;
        double head = sample.distance(time);
        double tail = Math.max(sample.distance(time - 1), head - 18);
        if (head > tail) sink.accept(sample.start.add(sample.direction.scale(tail)),
                                    sample.start.add(sample.direction.scale(head)));
    }

    static void tick(ClientLevel level, long tick) {
        resetLevel(level);
        samples.removeIf(sample -> tick - sample.firstTick >= PhalanxBurstPayload.MAX_TICKS
            || sample.distance(tick - 2) >= sample.range);
        if (rounds.isEmpty()) return;
        // The shared renderer can already be occupied by other weapons or terminal tails.
        int available = ProjectileStreaks.freeSlots();
        for (Round round : rounds) if (ProjectileStreaks.active(round.id)) available++;
        trim(rounds, Math.max(0, available), ProjectileStreaks::retire);
        for (var it = rounds.iterator(); it.hasNext();) {
            Round round = it.next();
            long age = tick - round.firstTick;
            if (age < 0) continue;
            double from = round.distance(tick - 1);
            if (age >= PhalanxBurstPayload.MAX_TICKS || from >= round.range || !ExtraConfig.STREAK_ENABLED.get()) {
                ProjectileStreaks.finish(round.id);
                it.remove();
                continue;
            }
            ProjectileStreaks.acceptMath(level, round.id, round.start.add(round.direction.scale(from)),
                round.start.add(round.direction.scale(round.distance(tick))), level.getGameTime(), false, true, false,
                age == 0 ? round.phase : 0, true);
        }
    }
}
