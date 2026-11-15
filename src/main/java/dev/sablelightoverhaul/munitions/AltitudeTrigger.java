package dev.sablelightoverhaul.munitions;

/** Pure height-fuze rule: descend through a clamped world Y threshold. No arming delay, no upward trigger. */
public final class AltitudeTrigger {
    public static final int MIN = -2048;
    public static final int MAX = 4096;
    public static final int DEFAULT = 100;

    private AltitudeTrigger() {}

    public static int clamp(int threshold) {
        return Math.max(MIN, Math.min(MAX, threshold));
    }

    public static boolean fires(double worldY, double velocityY, int threshold) {
        if (!Double.isFinite(worldY) || !Double.isFinite(velocityY)) return false;
        return worldY < clamp(threshold) && velocityY < 0.0;
    }
}
