package dev.sablelightoverhaul;

/** Dimensionless opacity envelope; independent of firing frequency and light intensity. */
public final class HazeEnvelope {
    /**
     * How long a puff takes to reach full opacity, in ticks -- a fixed time, not a share of its life.
     * The original 12% of the lifetime meant 12 ticks on a 100 tick puff, so the smoke only became
     * visible about 0.6 s after the shot: in game that read as the haze arriving once the whole firing
     * animation had already played, even though the packet is sent on the same tick as the flash.
     */
    private static final float RISE_TICKS = 1.5F;
    private HazeEnvelope() {}
    /** @param ageFraction how far through its life the puff is, 0..1
     *  @param lifetimeTicks the life that fraction is of, so the rise can be a fixed time */
    public static float opacity(float ageFraction, float lifetimeTicks) {
        if (!Float.isFinite(ageFraction) || ageFraction <= 0 || ageFraction >= 1) return 0;
        float rise = lifetimeTicks > 0 ? Math.min(1, ageFraction * lifetimeTicks / RISE_TICKS) : 1;
        float fall = Math.min(1, (1 - ageFraction) / .55F);
        return smooth(rise) * smooth(fall);
    }
    private static float smooth(float x) { return x * x * (3 - 2 * x); }
    public static float layerAlpha(float alpha, int layers) {
        return (float)(1 - Math.pow(1 - Math.clamp(alpha, 0, 1), 1.0 / Math.max(1, layers)));
    }
}
