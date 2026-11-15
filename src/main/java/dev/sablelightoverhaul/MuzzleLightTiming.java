package dev.sablelightoverhaul;

/** Keeps the stored shot alive through both configured light stages. No particle or client dependencies. */
public final class MuzzleLightTiming {
    private MuzzleLightTiming() {}
    public record Plan(LightCurve lifetime,LightLayout layout) {}
    public static Plan big(LightCurve lifetime,LightLayout layout,LightCurve blast,LightCurve smoke) {
        int duration=Math.max(lifetime.durationTicks(),blast.durationTicks()+smoke.durationTicks());
        return new Plan(new LightCurve(duration,lifetime.minimumBrightness(),lifetime.decayPerTick(),lifetime.peakHoldTicks()),
            new LightLayout(blast.durationTicks(),layout.fallbackSpeed()));
    }
}
