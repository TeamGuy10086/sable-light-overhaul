package dev.sablelightoverhaul;

/** Pure geometry shared with render validation. Flame local X is the barrel's forward axis. */
public final class FlameShape {
    private FlameShape() {}
    public record Vector(double x, double y, double z) {
        public Vector add(Vector v) { return new Vector(x+v.x,y+v.y,z+v.z); }
        public Vector scale(double s) { return new Vector(x*s,y*s,z*s); }
        public double dot(Vector v) { return x*v.x+y*v.y+z*v.z; }
        public Vector cross(Vector v) { return new Vector(y*v.z-z*v.y,z*v.x-x*v.z,x*v.y-y*v.x); }
        public Vector unit() {
            double n = Math.sqrt(dot(this));
            if (!Double.isFinite(n) || n < 1.0e-8) throw new IllegalArgumentException("Invalid flame direction");
            return scale(1.0/n);
        }
    }
    public record Basis(Vector forward, Vector right, Vector up) {}
    public static Basis basis(double dx, double dy, double dz) {
        var forward = new Vector(dx,dy,dz).unit();
        var reference = Math.abs(forward.y) < 0.9 ? new Vector(0,1,0) : new Vector(1,0,0);
        var right = forward.cross(reference).unit();
        return new Basis(forward,right,forward.cross(right).unit());
    }
    public static float envelope(float progress) {
        float p = Math.clamp(progress,0,1);
        return (1-p)*(1-p);
    }
}
