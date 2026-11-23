package dev.sablelightoverhaul;

import java.util.function.IntConsumer;

/** Records terrain removal during the engine's existing block destruction loop. */
public interface BlastDamageTracking {
    void slo$afterBlockDestruction(IntConsumer callback);
}
