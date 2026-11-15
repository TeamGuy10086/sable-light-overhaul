package dev.sablelightoverhaul;

/** Compact flame density, sampled by a view-aligned volume mesh rather than a transparent cone shell. */
public final class FlameVolume {
    private FlameVolume() {}
    public static float textureU(double along, double side, double phase) {
        return (float) Math.clamp(along + 0.035*Math.sin(along*10-phase*3+side*2),0.002,0.998);
    }
    public static float textureV(double along, double side, double phase) {
        return (float) Math.clamp(0.5+side*0.5+0.025*Math.sin(along*14-phase*2),0.002,0.998);
    }
    public static double density(double along, double radial, double phase) {
        if (along <= 0 || along >= 1 || radial < 0) return 0;
        double radius = 0.18 + 0.82 * Math.pow(Math.sin(Math.PI * along), 0.65);
        double r = radial / radius;
        if (r >= 1) return 0;
        double ends = Math.min(1, along * 12) * Math.min(1, (1-along) * 7);
        return ends * (1-r*r) * (1-r*r) * (0.85 + 0.15*Math.sin(along*23 + phase + radial*9));
    }
    public static float opacity(double density, double step, double width, float peak) {
        if (density <= 0 || step <= 0 || width <= 0) return 0;
        double tau = -Math.log1p(-Math.clamp(peak, 0, 0.995));
        return (float) -Math.expm1(-tau * density * step / (2*width));
    }
}
