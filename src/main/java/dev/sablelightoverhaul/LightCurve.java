package dev.sablelightoverhaul;

/** Fractions refer to initial intensity: linear subtraction, never exponential decay. */
public record LightCurve(int durationTicks, double minimumBrightness, double decayPerTick, int peakHoldTicks) {
    public static final LightCurve DEFAULT_BIG = new LightCurve(20,0.25,0.05);
    public static final LightCurve AUTO = new LightCurve(4,0,0.25);
    public LightCurve(int durationTicks, double minimumBrightness, double decayPerTick) {
        this(durationTicks,minimumBrightness,decayPerTick,0);
    }
    public LightCurve {
        if (durationTicks < 1 || durationTicks > 1200 || peakHoldTicks < 0 || peakHoldTicks > 1200
                || !fraction(minimumBrightness) || !fraction(decayPerTick))
            throw new IllegalArgumentException("Invalid light curve");
    }
    private static boolean fraction(double value) { return Double.isFinite(value) && value >= 0 && value <= 1; }
    public double brightness(long age) {
        if (age < 0 || age >= durationTicks) return 0;
        return Math.max(minimumBrightness,1.0-Math.max(0,age-peakHoldTicks)*decayPerTick);
    }

}
