package dev.sablelightoverhaul;

/**
 * Atlas geometry and age mapping for the smoke flipbook.
 * Six variants of {@value #FRAMES} frames live in one {@value #WIDTH}x{@value #HEIGHT} texture,
 * each frame a {@value #SIZE}px tile padded by {@value #PAD}px on every side.
 * Ages are flipbook units in {@code [0,8]}: 1.0 is the end of burning; 8.0 clamps texture motion, not particle lifetime.
 */
public final class SmokeFlipbookLayout {
    public static final int VARIANTS = 6;
    public static final int FRAMES = 32;
    public static final int SIZE = 128;
    public static final int PAD = 2;
    public static final int TILE = SIZE + 2 * PAD;
    public static final int COLUMNS = 8;
    public static final int ROWS = 24;
    public static final int WIDTH = COLUMNS * TILE;
    public static final int HEIGHT = ROWS * TILE;

    private static final int LAST_FRAME = FRAMES - 1;
    private static final int FRESH_FRAMES = 15;
    private static final double FRESH_AGE = 1.0;
    private static final double LIFE_AGE = 8.0;

    private SmokeFlipbookLayout() {}

    /** One of {@value #VARIANTS} smoke shapes, chosen deterministically from a particle seed. */
    public static int variant(int seed) {
        return Math.floorMod(seed, VARIANTS);
    }

    /** Atlas cell for a variant/frame pair, each variant owns four consecutive rows. */
    public static int tileIndex(int variant, int frame) {
        if (variant < 0 || variant >= VARIANTS) {
            throw new IllegalArgumentException("Invalid smoke variant " + variant);
        }
        if (frame < 0 || frame >= FRAMES) {
            throw new IllegalArgumentException("Invalid smoke frame " + frame);
        }
        return variant * FRAMES + frame;
    }

    /** Flipbook age reached at the start of {@code frame}: linear over the fresh phase, then more sparsely sampled. */
    public static double ageAtFrame(int frame) {
        if (frame < 0 || frame >= FRAMES) {
            throw new IllegalArgumentException("Invalid smoke frame " + frame);
        }
        return frame <= FRESH_FRAMES ? frame / (double) FRESH_FRAMES
                : FRESH_AGE + (frame - FRESH_FRAMES) * (LIFE_AGE - FRESH_AGE) / (LAST_FRAME - FRESH_FRAMES);
    }

    /**
     * Inverse of {@link #ageAtFrame} in continuous age space, clamped to {@code [0,8]}.
     * NaN counts as 0, positive infinity as the end of life.
     */
    public static double frameAtAge(double age) {
        if (Double.isNaN(age)) {
            age = 0;
        } else if (age == Double.POSITIVE_INFINITY) {
            age = LIFE_AGE;
        }
        age = Math.max(0, Math.min(LIFE_AGE, age));
        return age <= FRESH_AGE ? age * FRESH_FRAMES
                : FRESH_FRAMES + (age - FRESH_AGE) * (LAST_FRAME - FRESH_FRAMES) / (LIFE_AGE - FRESH_AGE);
    }
}
