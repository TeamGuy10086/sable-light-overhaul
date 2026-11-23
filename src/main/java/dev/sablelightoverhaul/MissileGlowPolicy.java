package dev.sablelightoverhaul;

/** Shared render budget and the missile nose's 120-degree exclusion cone. */
public final class MissileGlowPolicy {
    public static final int PACK_SLOTS = 128;
    private MissileGlowPolicy() {}

    public static boolean visible(double fx, double fy, double fz, double vx, double vy, double vz) {
        double f2 = fx*fx + fy*fy + fz*fz;
        double v2 = vx*vx + vy*vy + vz*vz;
        if (!Double.isFinite(f2) || !Double.isFinite(v2) || f2 < 1e-12 || v2 < 1e-12) return false;
        double dot = fx*vx + fy*vy + fz*vz;
        // cos(60 degrees) = 0.5. Rear hemisphere is always outside the nose cone.
        return dot <= 0 || dot*dot < 0.25*f2*v2;
    }
}
