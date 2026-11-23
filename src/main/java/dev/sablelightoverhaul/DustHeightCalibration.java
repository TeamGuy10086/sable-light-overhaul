package dev.sablelightoverhaul;

/** Reference craters from a surface burst over three dirt layers and then solid stone. */
public final class DustHeightCalibration {
    // Nearest integer to the median of 48 seeded ray simulations at each integer power, 6..32.
    private static final int[] REFERENCE = {
        222, 310, 403, 497, 594, 693, 795, 910, 1029, 1153, 1292, 1427, 1564, 1701,
        1835, 1965, 2098, 2227, 2361, 2490, 2618, 2741, 2856, 2979, 3100, 3209, 3338
    };

    private DustHeightCalibration() {}

    public static double referenceBlocks(double power) {
        double clamped = Math.clamp(power, 6, 32);
        int lower = (int)clamped - 6;
        return REFERENCE[lower] + (clamped - Math.floor(clamped))
            * (REFERENCE[Math.min(lower + 1, REFERENCE.length - 1)] - REFERENCE[lower]);
    }

    public static double heightFactor(double power, int destroyed) {
        if (destroyed < 0) return 1;
        if (destroyed == 0) return 0;
        return Math.clamp(destroyed / referenceBlocks(power), .35, 1.75);
    }
}
