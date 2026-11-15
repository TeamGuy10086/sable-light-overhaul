package dev.sablelightoverhaul;

/**
 * Deterministic, allocation-free motion and brightness model for a light flare
 * dropped from a starting height. The flare descends at exactly one block per
 * second, survives for its fixed lifetime, and emits a mild smooth flicker that
 * fades out over the last portion of its lifetime.
 */
public final class FlareMotion {

    /** Fade window cap, in seconds. */
    private static final double MAX_FADE_SECONDS = 0.7D;
    /** Fraction of lifetime used for the fade when shorter than the cap. */
    private static final double FADE_LIFETIME_FRACTION = 0.2D;
    /** Terminal fade window is kept at most half the lifetime. */
    private static final double MAX_FADE_FRACTION_OF_LIFETIME = 0.5D;

    /** Flicker amplitude (plus/minus) around the base brightness of 1. */
    private static final double FLICKER_AMPLITUDE = 0.15D;
    /** Fixed angular base for the slow component, radians. */
    private static final double SLOW_PHASE = 1.3D;
    /** Fixed angular base for the fast component, radians. */
    private static final double FAST_PHASE = 2.7D;

    private FlareMotion() {
    }

    /**
     * Height of the flare at the given age.
     *
     * @param startY      finite starting height
     * @param ageSeconds  finite, non-negative age in seconds
     * @return {@code startY - max(0, ageSeconds)}
     */
    public static double height(double startY, double ageSeconds) {
        requireFinite(startY, "startY");
        requireFinite(ageSeconds, "ageSeconds");
        double fall = Math.max(0.0D, ageSeconds);
        return startY - fall;
    }

    /**
     * Whether the flare exists: {@code ageSeconds >= 0 && ageSeconds < lifetimeSeconds}.
     */
    public static boolean alive(double ageSeconds, double lifetimeSeconds) {
        requireFinite(ageSeconds, "ageSeconds");
        requireFinite(lifetimeSeconds, "lifetimeSeconds");
        requirePositive(lifetimeSeconds, "lifetimeSeconds");
        return ageSeconds >= 0.0D && ageSeconds < lifetimeSeconds;
    }

    /**
     * Brightness multiplier for the flare.
     * <p>
     * Base is 1.0 with a mild smooth flicker bounded to [0.82, 1.15] before the
     * terminal fade. During the last {@code min(0.7, lifetime * 0.2)} seconds the
     * value smoothly fades to 0. Outside {@code [0, lifetime)} the result is 0.
     * Deterministic for a given seed; no allocation.
     *
     * @param seed           any long
     * @param ageSeconds     finite age in seconds
     * @param lifetimeSeconds finite, positive lifetime in seconds
     */
    public static double brightness(long seed, double ageSeconds, double lifetimeSeconds) {
        requireFinite(ageSeconds, "ageSeconds");
        requireFinite(lifetimeSeconds, "lifetimeSeconds");
        requirePositive(lifetimeSeconds, "lifetimeSeconds");

        if (ageSeconds < 0.0D || ageSeconds >= lifetimeSeconds) {
            return 0.0D;
        }

        double slowHz = 1.0D + 3.0D * unitFromSeed(seed, 0x9E3779B97F4A7C15L);
        double fastHz = 4.5D + 3.5D * unitFromSeed(seed, 0xC2B2AE3D27D4EB4FL);

        double flicker = FLICKER_AMPLITUDE
                * (0.6D * Math.sin(2.0D * Math.PI * slowHz * ageSeconds + SLOW_PHASE)
                 + 0.4D * Math.sin(2.0D * Math.PI * fastHz * ageSeconds + FAST_PHASE));

        double raw = clamp(1.0D + flicker, 0.82D, 1.15D);

        double fade = Math.min(MAX_FADE_SECONDS, lifetimeSeconds * FADE_LIFETIME_FRACTION);
        fade = Math.min(fade, lifetimeSeconds * MAX_FADE_FRACTION_OF_LIFETIME);
        double fadeStart = lifetimeSeconds - fade;

        if (fade <= 0.0D || ageSeconds <= fadeStart) {
            return raw;
        }

        double u = clamp((ageSeconds - fadeStart) / fade, 0.0D, 1.0D);
        double smooth = u * u * (3.0D - 2.0D * u); // smoothstep
        return raw * (1.0D - smooth);
    }

    private static double unitFromSeed(long seed, long salt) {
        long z = seed + salt;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (double) (z >>> 11) / (double) (1L << 53);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static void requireFinite(double v, String name) {
        if (!Double.isFinite(v)) {
            throw new IllegalArgumentException(name + " must be finite: " + v);
        }
    }

    private static void requirePositive(double v, String name) {
        if (!(v > 0.0D)) {
            throw new IllegalArgumentException(name + " must be > 0: " + v);
        }
    }
}
