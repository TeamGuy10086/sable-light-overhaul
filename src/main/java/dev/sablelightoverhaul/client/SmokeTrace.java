package dev.sablelightoverhaul.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Per-frame render trace for the thermal smoke flicker investigation (0.1.575).
 *
 * <p>Video analysis of one isolated puff (recorded against a plain wall with a static camera)
 * showed two distinct defects that shader-side reasoning alone could not separate: an every-frame
 * A/B content alternation, and bursts of 1-4 frame spans where the puff is absent from the whole
 * screen. The decisive question is whether {@code render()} ran on the absent frames: if it did,
 * the draw was produced and a downstream stage (composite, TAA) lost it; if it did not, the engine
 * never emitted the quad and the hunt moves to the engine/queue side.
 *
 * <p>The trace also answers a second question for free: Iris calls the particle render twice per
 * frame, so a healthy puff shows two {@code render} rows per frame with identical payload -- if the
 * two rows ever disagree, the passes are drawing different content.
 *
 * <p>Toggle with {@code /slosmoke trace}; turning it off writes {@code logs/slo-smoke-trace.csv}
 * beside the instance. Rows are milliseconds since the trace started, so they line up with a
 * recording's timeline.
 */
public final class SmokeTrace {
    private SmokeTrace() {}

    private static volatile boolean on;
    private static final List<String> ROWS = new ArrayList<>();
    private static long started;

    public static boolean on() { return on; }

    public static synchronized String toggle() {
        if (on) {
            on = false;
            return flush();
        }
        ROWS.clear();
        ROWS.add("ms,kind,age,partial,coverage,encoded,x,y,z,size");
        started = System.nanoTime();
        on = true;
        return "SLO smoke trace: on (toggle again to write logs/slo-smoke-trace.csv)";
    }

    /** One row per call; the string is built only while tracing, so the off cost is one branch. */
    public static void row(String kind, int age, float partial, float coverage, int encoded,
                           double x, double y, double z, float size) {
        if (!on) return;
        String row = ((System.nanoTime() - started) / 1_000_000L) + "," + kind + "," + age + ","
            + partial + "," + coverage + "," + encoded + ","
            + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f", x, y, z) + "," + size;
        synchronized (SmokeTrace.class) {
            if (ROWS.size() < 400_000) ROWS.add(row);
        }
    }

    private static String flush() {
        Path out = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("logs").resolve("slo-smoke-trace.csv");
        try {
            Files.write(out, ROWS);
        } catch (IOException failure) {
            return "SLO smoke trace: write failed: " + failure;
        }
        return "SLO smoke trace: wrote " + ROWS.size() + " rows to " + out.getFileName();
    }
}
