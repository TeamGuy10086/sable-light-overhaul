package dev.sablelightoverhaul;

/** Presentation curves for the shared muzzle haze carriers. */
public final class AutocannonHazeModel {
    private AutocannonHazeModel() {}

    public static float growth(float age, int lifetime) {
        return .72f + .43f * smooth(age / Math.min(14f, lifetime * .5f));
    }

    public static float cameraFade(double distance, float halfHeight) {
        float start = Math.max(.22f, halfHeight * .17f);
        float end = Math.max(.55f, halfHeight * .62f);
        return smooth((float) ((distance - start) / (end - start)));
    }

    public static float opacity(float accumulated, float singleShot) {
        // Keep the initial slope at one, then compress repeated contributions toward a thin ceiling.
        float ceiling = Math.max(.32f, singleShot);
        return accumulated * ceiling / (ceiling + (1 - ceiling) * accumulated);
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
