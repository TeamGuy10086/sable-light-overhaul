package dev.sablelightoverhaul;

/** Timing and fallback movement of the muzzle light, in ticks and blocks. */
public record LightLayout(int muzzleHoldTicks, double fallbackSpeed) {
    public static final LightLayout BIG = new LightLayout(2, 0.9);
    public static final LightLayout AUTO = new LightLayout(0, 0);

    public LightLayout {
        if (muzzleHoldTicks < 0 || muzzleHoldTicks > 600
                || !Double.isFinite(fallbackSpeed) || fallbackSpeed < 0 || fallbackSpeed > 4) {
            throw new IllegalArgumentException("Invalid muzzle light timing");
        }
    }

    public double fallbackTravel(long age) {
        return fallbackSpeed * (1-Math.pow(0.95,Math.max(0,age-muzzleHoldTicks))) / 0.05;
    }
}
