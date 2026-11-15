package dev.sablelightoverhaul.client;

/**
 * Whether a periodic deadline kept as a game-time stamp is due.
 *
 * <p>A world's game time restarts at 0, so a stamp remembered from an earlier world can be larger than the
 * time being asked about. A negative delta does not mean "not yet": it means the stamp belongs to another
 * world (or to this one before its clock was wound back), and the deadline is due now.
 *
 * <p>Reading it as "not yet" is what froze every per-interval decision after a new world was created: the
 * light classes held on to the previous world's winner set and dropped every lamp, spotlight and flashlight
 * in the new one (see {@code AdvancedLights.capClass}), while the muzzle/tracer class -- which no budget
 * ever filters -- kept working. The visible symptom was "only the cannon fire still lights up".
 */
public final class GameClock {
    private GameClock() {}

    /** {@code last} is {@link Long#MIN_VALUE} for "never"; {@code interval} is in game ticks. */
    public static boolean due(long now,long last,long interval) {
        return last==Long.MIN_VALUE || now<last || now-last>=interval;
    }
}
