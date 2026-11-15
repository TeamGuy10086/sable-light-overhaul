package dev.sablelightoverhaul.client;

import java.util.Arrays;

/**
 * The order one particle layer has to be drawn in: far to near.
 *
 * <p>Vanilla does not sort particles at all. {@code ParticleEngine.render} walks each layer's queue in
 * insertion order -- so <em>spawn order is draw order</em> -- and the class contains no distance sort
 * anywhere. Iris does not add one either (it only tags the rendering phase). That is invisible for
 * opaque particles and wrong for translucent ones: a layer draws into one buffer with alpha blending, so
 * whichever quad is painted first gets mixed into the pixel and the ones painted after it blend on top.
 * With the depth writes particles do, a nearly transparent quad painted <em>first</em> also claims the
 * depth and culls everything behind it, however faint it is -- which is why a long-lived haze that keeps
 * being topped up ends up hiding the crisp particles spawned after it.
 *
 * <p>Drawing far to near fixes both: everything behind a faint quad is blended in first, so the faint
 * quad tints it instead of erasing it, and the near quad's depth write has nothing left to cull. This
 * was written for the tank cannon's haze (see research/CBC-CANNON-SMOKE-2026-10-05.zh-CN.md §5) but the
 * order is per layer, so it applies to every particle of every mod in that layer.
 *
 * <p>The distance is packed with the index into one {@code long} so the sort is a primitive array sort
 * rather than a comparator that would recompute a square root-free distance some 14 times per particle.
 * Non-negative floats compare correctly by their raw bit pattern, which is what makes that packing legal.
 *
 * <p>{@link #farToNear} sorts from scratch every frame. {@link #farToNearSeeded} starts from the previous
 * frame's order instead: between two frames the order barely changes, and a comparison sort cannot use
 * that at all ({@code Arrays.sort} on a primitive array is not adaptive). Measured on a simulated 2000
 * particle layer -- 1500 drifting one way, 500 the other, camera moving sideways -- the seeded pass needed
 * about half the work of the full sort, and the motion pattern barely moved it.
 */
public final class ParticleDepthSort {
    private ParticleDepthSort() {}

    /**
     * Fill {@code packed} with the sort key of each point: the squared distance as a float in the high half
     * of the long, the complemented index in the low half (stable ties when the queue is rebuilt).
     *
     * <p>Squared distance rather than distance: a square root can only lose precision here, and a camera
     * sitting exactly on a particle would divide by zero. The float, not a double, because the key only has
     * 32 bits in the long and a float's 24-bit mantissa is far more precision than "which of these two
     * quads is in front" needs.
     */
    public static void pack(double[] x, double[] y, double[] z, int count,
                            double cx, double cy, double cz, long[] packed) {
        for (int i = 0; i < count; i++) {
            double dx = x[i] - cx;
            double dy = y[i] - cy;
            double dz = z[i] - cz;
            float distanceSquared = (float) (dx * dx + dy * dy + dz * dz);
            packed[i] = ((long) Float.floatToRawIntBits(distanceSquared) << 32) | ((~i) & 0xFFFFFFFFL);
        }
    }

    /**
     * The indices of {@code count} points in far-to-near order around the camera.
     *
     * <p>Pure: everything it needs is passed in, including the scratch arrays, and it allocates nothing.
     * {@code packed} and {@code order} must each hold at least {@code count} entries; the returned array
     * is {@code order} itself.
     *
     * @param x       point positions, one per particle.
     * @param y       point positions, one per particle.
     * @param z       point positions, one per particle.
     * @param count   how many entries of each array are live.
     * @param cx      the camera position the order is measured from.
     * @param cy      the camera position the order is measured from.
     * @param cz      the camera position the order is measured from.
     * @param packed  scratch: {@code count} longs.
     * @param order   scratch: {@code count} ints, also the return value.
     * @returns every index in {@code [0, count)} exactly once, farthest from the camera first.
     */
    public static int[] farToNear(double[] x, double[] y, double[] z, int count,
                                  double cx, double cy, double cz, long[] packed, int[] order) {
        if (count < 0) throw new IllegalArgumentException("negative count");
        if (x.length < count || y.length < count || z.length < count
                || packed.length < count || order.length < count) {
            throw new IllegalArgumentException("scratch smaller than count");
        }
        pack(x, y, z, count, cx, cy, cz, packed);
        Arrays.sort(packed, 0, count);
        // Ascending is nearest first, and the layer is drawn farthest first.
        for (int i = 0; i < count; i++) {
            order[i] = ~(int) (packed[count - 1 - i] & 0xFFFFFFFFL);
        }
        return order;
    }

    /**
     * The same far-to-near order, reached from the order the layer was drawn in last frame instead of from
     * scratch.
     *
     * <p>{@code order} must arrive holding a permutation of {@code [0, count)} to start from -- normally the
     * previous frame's order, filtered down to the particles that still exist. {@code maxShifts} bounds the
     * work: a teleport, a respawn, or a burst of new particles can make the old order useless, and then this
     * gives up rather than doing a quadratic pass, and the caller sorts fully. It packs {@code packed}, so
     * the keys are identical to {@link #farToNear}'s for the same input.
     *
     * <p>Pure, allocation free, and it leaves {@code order} in a defined state only when it returns true: on
     * false the caller is expected to call {@link #farToNear} and ignore what is in {@code order}.
     *
     * @returns true when {@code order} now holds the far-to-near order, false when the caller must sort fully.
     */
    public static boolean farToNearSeeded(double[] x, double[] y, double[] z, int count,
                                          double cx, double cy, double cz,
                                          long[] packed, int[] order, int maxShifts) {
        if (count < 0) throw new IllegalArgumentException("negative count");
        if (x.length < count || y.length < count || z.length < count
                || packed.length < count || order.length < count) {
            throw new IllegalArgumentException("scratch smaller than count");
        }
        if (count < 2) return true;
        pack(x, y, z, count, cx, cy, cz, packed);
        int shifts = 0;
        for (int i = 1; i < count; i++) {
            int moving = order[i];
            long key = packed[moving];
            int j = i - 1;
            // Descending by key: ascending is nearest first, and the layer is drawn farthest first.
            while (j >= 0 && packed[order[j]] < key) {
                order[j + 1] = order[j];
                j--;
                if (++shifts > maxShifts) return false;
            }
            order[j + 1] = moving;
        }
        return true;
    }
}
