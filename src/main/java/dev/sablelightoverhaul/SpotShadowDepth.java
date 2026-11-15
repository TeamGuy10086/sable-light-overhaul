package dev.sablelightoverhaul;

/**
 * R16 UNORM encoding of a spot-light shadow distance relative to a range.
 *
 * <p>Code 0 is the sentinel for "no hit". A real hit at distance {@code d} in
 * {@code [0, range]} is encoded as {@code 1 + round(clamp(d / range, 0, 1) * 65534)},
 * so code {@link #MAX_CODE} represents a hit at or beyond the range.</p>
 *
 * <p>Uploaded to GL as a {@code short}, but decoding always treats the value as
 * unsigned in {@code 0..65535}; the sign bit is never reinterpreted.</p>
 */
public final class SpotShadowDepth {

    /** Largest value of the 16-bit unsigned normalised format. */
    public static final int MAX_CODE = 65535;

    private static final int HIT_CODE_MAX = MAX_CODE - 1;
    private static final double UPPER = HIT_CODE_MAX;

    private SpotShadowDepth() {
    }

    /**
     * Encodes a shadow distance for the given range.
     *
     * @param distance ray distance in world units; values {@code <= 0} mean no hit
     * @param range    maximum representable distance, strictly positive
     * @return {@code 0} for no hit, otherwise {@code 1..65535}
     * @throws IllegalArgumentException if distance or range is non-finite, or range is non-positive
     */
    public static int encode(double distance, double range) {
        checkFinite(distance, "distance");
        checkRange(range);
        if (distance <= 0.0) {
            return 0;
        }
        double t = distance / range;
        if (t > 1.0) {
            t = 1.0;
        }
        return 1 + (int) Math.round(t * UPPER);
    }

    /**
     * Decodes an unsigned R16 UNORM code back to a shadow distance.
     *
     * @param unsignedCode value in {@code 0..65535}
     * @param range        maximum representable distance, strictly positive
     * @return {@link Double#POSITIVE_INFINITY} for code {@code 0}, otherwise the decoded distance
     * @throws IllegalArgumentException if the code is outside {@code 0..65535} or the range is invalid
     */
    public static double decode(int unsignedCode, double range) {
        checkRange(range);
        if (unsignedCode < 0 || unsignedCode > MAX_CODE) {
            throw new IllegalArgumentException("unsignedCode out of range: " + unsignedCode);
        }
        if (unsignedCode == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return (unsignedCode - 1) / UPPER * range;
    }

    private static void checkFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }

    private static void checkRange(double range) {
        checkFinite(range, "range");
        if (range <= 0.0) {
            throw new IllegalArgumentException("range must be positive: " + range);
        }
    }
}
