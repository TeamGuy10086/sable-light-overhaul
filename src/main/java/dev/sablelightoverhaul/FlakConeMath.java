package dev.sablelightoverhaul;

/**
 * Pure geometry for the flak cone: a closed finite solid cone (a triangle revolved around its axis).
 *
 * <p>Every test is reduced to the axial/radial half-plane {@code (s, r)}, where {@code s} is the depth
 * along the unit axis and {@code r >= 0} the perpendicular distance. The cone's cross-section there is the
 * triangle {@code (0,0), (range,0), (range,range*tangent)}. Because the cross-section is convex and the
 * distance from a point to the revolved solid equals the plane distance to the triangle, the sphere
 * intersects exactly when the centre is inside the triangle or within {@code radius} of its three edges.
 * No angular or axis-aligned approximation is involved.</p>
 */
public final class FlakConeMath {
    /** Axial directions whose normalized length cannot be squared without overflow. */
    public static final double MIN_AXIS_LENGTH = 1.0e-150;
    /** Largest axial direction whose squared length is still finite in every coordinate. */
    public static final double MAX_AXIS_LENGTH = 1.0e150;
    /** Value returned by {@link #falloff(double,double)} at the far edge of the range. */
    public static final double RANGE_FALLOFF = 0.35;

    private FlakConeMath() {}

    /**
     * Tests whether a bounding sphere intersects the closed finite cone.
     *
     * @param x,y,z        sphere centre offset from the cone tip
     * @param ax,ay,az     cone axis (any finite, non-zero, reasonably scaled vector, normalized internally)
     * @param range        axial extent of the cone, {@code > 0}
     * @param tangent      cone radius per axial depth, {@code >= 0}
     * @param radius       sphere radius, {@code >= 0}
     * @return true when the sphere touches or overlaps the solid cone; false for any invalid input
     */
    public static boolean intersects(double x, double y, double z,
                                     double ax, double ay, double az,
                                     double range, double tangent, double radius) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(ax) || !Double.isFinite(ay) || !Double.isFinite(az)
                || !Double.isFinite(range) || !Double.isFinite(tangent) || !Double.isFinite(radius))
            return false;
        if (!(range > 0) || tangent < 0 || radius < 0) return false;
        double length = Math.sqrt(ax * ax + ay * ay + az * az);
        if (!(length >= MIN_AXIS_LENGTH) || !(length <= MAX_AXIS_LENGTH))
            return false;

        double ux = ax / length, uy = ay / length, uz = az / length;
        // Axial coordinate: projection of the offset onto the unit axis.
        double s = x * ux + y * uy + z * uz;
        // Radial coordinate: the remaining perpendicular component.
        double px = x - s * ux, py = y - s * uy, pz = z - s * uz;
        double r = Math.sqrt(px * px + py * py + pz * pz);

        double capRadius = range * tangent; // radius at the cap
        double r2 = radius * radius;
        // Round-off at a closed boundary must not turn a touching sphere into a miss.
        r2 += 1e-12 * Math.max(1.0,range*range+capRadius*capRadius+radius*radius);

        // Sphere centre inside the closed triangle. With tangent == 0 this degenerates to the axial segment.
        if (s >= 0 && s <= range && r <= s * tangent) return true;

        // Distance from (s,r) to each of the three triangle edges, in the axial/radial plane.
        if (segmentDistanceSquared(s, r, 0, 0, range, 0) <= r2) return true;                          // base
        if (segmentDistanceSquared(s, r, range, 0, range, capRadius) <= r2) return true;              // cap face
        if (segmentDistanceSquared(s, r, range, capRadius, 0, 0) <= r2) return true;                  // side
        return false;
    }

    /**
     * Squared distance from {@code (qx,qy)} to the closed segment {@code (x1,y1)-(x2,y2)}, in the same
     * coordinate pair both points are given in.
     */
    private static double segmentDistanceSquared(double qx, double qy,
                                                 double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double wx = qx - x1, wy = qy - y1;
        double len2 = dx * dx + dy * dy;
        double t = len2 > 0 ? (wx * dx + wy * dy) / len2 : 0.0;
        if (t < 0) t = 0;
        else if (t > 1) t = 1;
        double ex = wx - t * dx, ey = wy - t * dy;
        return ex * ex + ey * ey;
    }

    /**
     * Linear intensity factor over the cone's axial depth: {@code 1} at {@code 0} or behind, falling to
     * exactly {@link #RANGE_FALLOFF} at {@code range}, and {@code 0} only when {@code distance > range}
     * (or for an invalid range).
     */
    public static double falloff(double distance, double range) {
        if (!Double.isFinite(distance) || !Double.isFinite(range) || !(range > 0)) return 0;
        if (distance <= 0) return 1;
        if (distance > range) return 0;
        if (distance == range) return RANGE_FALLOFF;
        return 1.0 - (1.0 - RANGE_FALLOFF) * (distance / range);
    }
}
