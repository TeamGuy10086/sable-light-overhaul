package dev.sablelightoverhaul.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The bodies whose rotation the shadow cones of this frame are measured in.
 *
 * <p>A lamp's cones are angles a player measured against the hull, so a hull that turns must turn them
 * too. Turning a direction into the body's frame is a quaternion rotation in the shader, and the shader
 * can only know what we publish -- so this collects one quaternion per physics body, once per frame, and
 * hands out a small index per body for the per-light style word to carry.
 *
 * <p>Keyed by the body's id rather than by its rotation, for two reasons: every lamp on one hull shares a
 * slot (which is why eight is plenty), and a body that has not turned still costs one lookup instead of a
 * quaternion comparison. Index 0 is the world -- the identity -- which is what a lamp on the ground is
 * measured in and what every lamp falls back to.
 *
 * <p>More bodies than the table holds is not an error, but it is not silent either: the ones that did not
 * fit keep the world frame, which means their cones will not turn with their hull, and {@link #spilled()}
 * is counted for the debug line so that can be seen rather than guessed at.
 */
final class BodyFrames {
    /** The shader's table size: {@code sloBody0..7}. Keep in step with the patch. */
    static final int MAX = 8;
    private static final Map<UUID, Integer> SLOTS = new HashMap<>();
    private static final float[] TABLE = new float[MAX * 4];
    private static int used;
    private static int spilled;

    private BodyFrames() {}

    /** Start a frame: the table is rebuilt from scratch, because a body may have left the view. */
    static void begin() {
        SLOTS.clear();
        // Slot 0 belongs to the world and is never handed out. It has to be written here rather than
        // assumed: an all-zero quaternion rotates a direction into nothing, and a lamp on the ground
        // would then test a cone that is not the one its page was given.
        java.util.Arrays.fill(TABLE, 0.0F);
        TABLE[3] = 1.0F;
        used = 1;
        spilled = 0;
    }

    /**
     * The slot for one body, assigning it on first sight this frame.
     *
     * @returns the slot, or 0 (the world) when the table is full.
     */
    static int slot(UUID body, float[] quaternion) {
        if (body == null || quaternion == null) return 0;
        Integer existing = SLOTS.get(body);
        if (existing != null) return existing;
        if (used >= MAX) {
            // Remembered as the world frame so the count is "how many bodies were left out" rather than
            // "how many lamps asked": every other lamp on that hull then finds the answer already here.
            SLOTS.put(body, 0);
            spilled++;
            return 0;
        }
        int slot = used++;
        SLOTS.put(body, slot);
        System.arraycopy(quaternion, 0, TABLE, slot * 4, 4);
        return slot;
    }

    /** The quaternions, four floats each, for the shader's table. */
    static float[] table() {
        return TABLE;
    }

    /** How many bodies this frame uses; the world is not one of them. */
    static int used() {
        return used - 1;
    }

    /** How many cone-bearing bodies did not fit the table this frame. */
    static int spilled() {
        return spilled;
    }
}
