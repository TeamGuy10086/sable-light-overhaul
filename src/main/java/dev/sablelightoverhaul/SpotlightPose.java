package dev.sablelightoverhaul;

/** Blockbench lens/pivot contract, shared by the articulated model and every light backend. */
public final class SpotlightPose {
    public static final double PIVOT_Y=11.25/16.0;
    public static final double LENS_REACH=(8.0-1.85)/16.0;
    // Inscribed circle of the 9.4 x 7.9 pixel lens. No extra rays or geometry per light.
    public static final double APERTURE=7.9/32.0;
    private SpotlightPose() {}
    public static float yaw(double azimuth){return (float)(-90-azimuth);}
    public static float pitch(double tilt){return (float)(90-tilt);}
    /** Offset from the block centre, before any user offset or physics-body transform. */
    public static double[] mouthOffset(double tilt,double azimuth){
        double t=Math.toRadians(tilt),a=Math.toRadians(azimuth);
        return new double[]{LENS_REACH*Math.sin(t)*Math.cos(a),
            PIVOT_Y-0.5+LENS_REACH*Math.cos(t),LENS_REACH*Math.sin(t)*Math.sin(a)};
    }
}
