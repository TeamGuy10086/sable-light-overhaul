package dev.sablelightoverhaul.client;

/**
 * The partial tick Sable last drew a hull with.
 *
 * <p>This mod has to place a lamp where its hull was drawn, and until now it re-derived that from
 * {@code getTimer().getGameTimeDeltaPartialTick(false)} -- the same call Sable's level renderer makes, but not
 * the same value: that call is only stable within one frame, and anything that makes the two read it at
 * different moments (a different point in the frame, a re-entrant layer, a mod that advances the timer) puts
 * the lamp a fraction of a tick ahead of the deck it stands on. The symptom of that fraction is a ray lamp's
 * pool sliding and flickering, because a ray consumes the lamp's absolute position directly.
 *
 * <p>So it is recorded instead of recomputed: a mixin on {@code ClientSubLevel.renderPose(float)} stores the
 * argument, and this is the value the hull's own sections were drawn with. Nothing to be in phase with.
 */
public final class SableRenderTick {
    private static volatile float recorded = Float.NaN;

    private SableRenderTick() {}

    /** Called from the mixin, once per hull section per frame, with the tick fraction that section used. */
    public static void record(float partialTick) {
        if (Float.isFinite(partialTick)) recorded = partialTick;
    }

    /** The recorded fraction, or NaN while Sable has not drawn a hull since the game started. */
    public static float last() {
        return recorded;
    }
}
