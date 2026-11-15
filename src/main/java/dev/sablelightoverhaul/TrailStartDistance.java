package dev.sablelightoverhaul;

/**
 * Small pure helper behind the tank cannon trail gate: remembers where a projectile was first seen on
 * the client and decides whether a particle standing at a given point is still inside the start sphere.
 *
 * <p>The origin has to be captured on the projectile's first client tick, before that tick moves it,
 * otherwise the sphere would follow the round and gate nothing. It is therefore a fixed launch point
 * in world coordinates, not the camera and not the round's current position.
 */
public final class TrailStartDistance {
    private boolean captured;
    private double originX;
    private double originY;
    private double originZ;

    /** Stores the first origin only; later calls are ignored so the sphere stays at the launch point. */
    public void capture(double x, double y, double z) {
        if (captured) return;
        captured = true;
        originX = x;
        originY = y;
        originZ = z;
    }

    public boolean isCaptured() {
        return captured;
    }

    /**
     * True when a particle spawned at the given position lies strictly inside the start sphere, i.e. its
     * distance from the captured origin is below {@code start}. A disabled gate ({@code start <= 0}) or
     * a missing origin always returns false so the trail renders exactly as CBC does.
     */
    public boolean suppress(double start, double x, double y, double z) {
        if (start <= 0 || !captured) return false;
        double dx = x - originX;
        double dy = y - originY;
        double dz = z - originZ;
        return dx * dx + dy * dy + dz * dz < start * start;
    }
}
