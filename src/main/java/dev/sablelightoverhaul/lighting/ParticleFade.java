package dev.sablelightoverhaul.lighting;

/**
 * How a puff's opacity changes over its life, in one place.
 *
 * <p>Two consumers: the engine generators' own particles ({@link GroundDustParticleData} and
 * {@link SmokeParticleData}) both carry an opacity at birth and a fade, and both have to fade the same
 * way for the two page fields to mean the same thing. It is also the only part of either particle that
 * can be checked without a game, so it is a pure function and nothing else.
 *
 * <p>The fade is quadratic, which is what makes the tail disappear instead of thinning visibly to
 * nothing; the value of the curve is not exposed, only when it starts and how much of the life it takes.
 */
public final class ParticleFade {
    private ParticleFade() {}

    /**
     * The opacity of a puff partway through its life.
     *
     * @param progress 0 at birth, 1 at the end of its life.
     * @param opacity  the opacity at birth, {@code 0.02..1}.
     * @param fade     the fraction of the life spent fading out: {@code 0.4} means full opacity until
     *                 60% of the life has passed and gone at 100% (what this mod did before the value
     *                 was configurable), and {@code 1} means it starts fading at birth. Anything at or
     *                 below zero holds the birth opacity and still dies at the end of the life.
     * @return the alpha to draw with, {@code 0..opacity}.
     */
    public static float alpha(double progress, float opacity, float fade) {
        double p = Math.min(Math.max(progress, 0.0), 1.0);
        if (p >= 1.0) return 0.0F;
        double f = Math.min(Math.max(fade, 0.0), 1.0);
        if (f <= 1.0E-6) return opacity;
        double held = 1.0 - f;
        if (p <= held) return opacity;
        double across = 1.0 - (p - held) / f;
        return (float) (opacity * across * across);
    }
}
