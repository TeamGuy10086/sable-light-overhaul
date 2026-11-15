package dev.sablelightoverhaul;
/** A finite support bound, not a new light-source grid. */
public final class LightSizing {
    private LightSizing() {}
    public static double radius(double base,double scale,double cap) {
        if(!Double.isFinite(base+scale+cap) || base<=0 || scale<0 || cap<=0) throw new IllegalArgumentException();
        return Math.clamp(base*scale,0.1,cap);
    }
}
