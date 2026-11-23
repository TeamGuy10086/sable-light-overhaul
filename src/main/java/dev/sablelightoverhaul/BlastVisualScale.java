package dev.sablelightoverhaul;

/** Extra size factors applied after each effect's existing power curve. */
public final class BlastVisualScale {
    private BlastVisualScale() {}

    public static double dimensions(double power) {
        return power > 0 && power <= 15 ? .67 : 1;
    }

    public static double fireball(double power) {
        return power > 0 && power <= 15 ? .50 : 1;
    }
}
