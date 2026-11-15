package dev.sablelightoverhaul.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Alternates the smoke material on the same frozen puffs while measuring frame-to-frame wall time. */
final class SmokeBenchmark {
    private static final long SECOND = 1_000_000_000L;
    private static final long WARMUP = 2 * SECOND;
    private static final long SAMPLE_TIME = 5 * SECOND;
    private static final boolean[] BAKED = {false, true, true, false};
    private static Run active;

    private SmokeBenchmark() {}

    private static final class Run {
        final ClientLevel level;
        final int count;
        final int visualEpoch;
        final long requestedAt = System.nanoTime();
        final long[][] samples = new long[BAKED.length][8192];
        final int[] lengths = new int[BAKED.length];
        final String settings;
        List<BigSmokeParticle> particles;
        Vec3 eye;
        float pitch;
        float yaw;
        int phase;
        long phaseStart;
        long lastFrame;
        int draws;
        final int[] drawnSamples = new int[BAKED.length];

        Run(Minecraft mc, int count) {
            this.level = mc.level;
            this.count = count;
            visualEpoch = ClientFlashes.visualEpoch();
            settings = String.format(Locale.ROOT, "puffs=%d, backend=%s, render=%dx%d, vsync=%s, fpsLimit=%s",
                count, ClientFlashes.effectiveMode(), mc.getWindow().getWidth(), mc.getWindow().getHeight(),
                mc.options.enableVsync().get(), mc.options.framerateLimit().get());
        }
    }

    static boolean performanceEnabled() {
        Run run = active;
        return run != null && run.particles != null ? BAKED[run.phase] : BigSmokeConfig.PERFORMANCE.get();
    }

    static int start(int count) {
        Minecraft mc = Minecraft.getInstance();
        if (active != null) {
            message(mc, "active");
            return 0;
        }
        if (mc.level == null || mc.player == null || !BigSmokeParticle.available()
                || !MuzzleParticleLayer.proceduralSmoke() || MuzzleParticleLayer.mode() != MuzzleParticleLayer.Mode.FIXED) {
            message(mc, "unavailable");
            return 0;
        }
        if (SmokeTrace.on()) {
            message(mc, "trace");
            return 0;
        }
        active = new Run(mc, count);
        message(mc, "start");
        return 1;
    }

    static int stop() {
        if (active == null) return 0;
        cancel(Minecraft.getInstance(), "stopped");
        return 1;
    }

    static void noteDraw() {
        if (active != null) active.draws++;
    }

    static void onFrame(RenderFrameEvent.Pre event) {
        Run run = active;
        if (run == null) return;
        long now = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != run.level || mc.player == null || run.visualEpoch != ClientFlashes.visualEpoch()) {
            cancel(mc, "world");
            return;
        }
        var camera = mc.gameRenderer.getMainCamera();
        if (run.particles == null) {
            if (mc.screen != null || !mc.isWindowActive()) {
                if (now - run.requestedAt > 10 * SECOND) cancel(mc, "screen");
                return;
            }
            run.eye = camera.getPosition();
            run.pitch = camera.getXRot();
            run.yaw = camera.getYRot();
            run.particles = BigSmokeParticle.benchmarkPuffs(run.level, run.eye, Vec3.directionFromRotation(run.pitch, run.yaw), run.count);
            run.phaseStart = now;
            return;
        }
        if (mc.screen != null || !mc.isWindowActive() || camera.getPosition().distanceToSqr(run.eye) > 0.04
                || Math.abs(Mth.wrapDegrees(camera.getXRot() - run.pitch)) > 0.5
                || Math.abs(Mth.wrapDegrees(camera.getYRot() - run.yaw)) > 0.5) {
            cancel(mc, "view");
            return;
        }
        if (run.lastFrame >= run.phaseStart + WARMUP) {
            int index = run.lengths[run.phase];
            if (index >= run.samples[run.phase].length) {
                cancel(mc, "full");
                return;
            }
            run.samples[run.phase][index] = now - run.lastFrame;
            run.lengths[run.phase]++;
            if (run.draws > 0) run.drawnSamples[run.phase]++;
        }
        run.draws = 0;
        if (now - run.phaseStart >= WARMUP + SAMPLE_TIME) {
            if (run.phase + 1 == BAKED.length) {
                finish(mc, run);
                return;
            }
            run.phase++;
            run.phaseStart = now;
        }
        run.lastFrame = now;
    }

    private static void release(Run run) {
        active = null;
        if (run.particles != null) run.particles.forEach(BigSmokeParticle::remove);
    }

    private static void cancel(Minecraft mc, String reason) {
        release(active);
        message(mc, "cancel", Component.translatable("sablelightoverhaul.smokeBench." + reason));
    }

    private static void finish(Minecraft mc, Run run) {
        release(run);
        for (int round = 0; round < BAKED.length; round++) {
            if (run.lengths[round] < 10 || run.drawnSamples[round] < run.lengths[round] * 0.95) {
                message(mc, "invalid");
                return;
            }
        }

        var csv = new ArrayList<String>();
        csv.add("round,mode,frame,frame_ms");
        for (int round = 0; round < BAKED.length; round++) {
            for (int frame = 0; frame < run.lengths[round]; frame++) {
                csv.add(String.format(Locale.ROOT, "%d,%s,%d,%.4f", round + 1,
                    BAKED[round] ? "baked" : "realtime", frame + 1, run.samples[round][frame] / 1e6));
            }
        }
        String realtime = summarize(run, false);
        String baked = summarize(run, true);
        message(mc, "result", realtime, baked);
        var logger = com.mojang.logging.LogUtils.getLogger();
        logger.info("SLO smoke bench {} | realtime {} | baked {}", run.settings, realtime, baked);
        Path dir = mc.gameDirectory.toPath().resolve("logs");
        try {
            Files.createDirectories(dir);
            String stamp = Instant.now().toString().replace(':', '-');
            Path output = Files.createTempFile(dir, "slo-smoke-bench-" + stamp + "-", ".csv");
            Files.write(output, csv);
            message(mc, "saved", output.getFileName().toString());
        } catch (IOException error) {
            logger.warn("SLO smoke bench could not write CSV", error);
            message(mc, "writeFailed");
        }
    }

    private static String summarize(Run run, boolean baked) {
        int length = 0;
        for (int round = 0; round < BAKED.length; round++) {
            if (BAKED[round] == baked) length += run.lengths[round];
        }
        long[] combined = new long[length];
        int offset = 0;
        for (int round = 0; round < BAKED.length; round++) {
            if (BAKED[round] != baked) continue;
            System.arraycopy(run.samples[round], 0, combined, offset, run.lengths[round]);
            offset += run.lengths[round];
        }
        Arrays.sort(combined);
        return String.format(Locale.ROOT, "P50 %.2f ms / P95 %.2f ms (%d frames)",
            percentile(combined, .50), percentile(combined, .95), length);
    }

    private static double percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(sorted.length * percentile) - 1;
        return sorted[index] / 1e6;
    }

    private static void message(Minecraft mc, String key, Object... args) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("sablelightoverhaul.smokeBench." + key, args), false);
        }
    }
}
