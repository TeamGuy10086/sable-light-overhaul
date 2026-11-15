package dev.sablelightoverhaul;

import net.minecraft.world.phys.Vec3;

/** Fixed screen-axis footprint inside the projected oblate support; never rotates the smoke lobes. */
public final class HullDustProjection {
    private HullDustProjection(){}
    public record Shape(float halfWidth,float halfHeight,float roll,double lift){}
    public static Shape project(Vec3 normal,Vec3 right,Vec3 up,double width,double thickness,double freeHeight,double blend){
        blend=Math.clamp(blend,0,1);width=Math.max(.01,width);
        double depth=Math.min(width,Math.max(.01,width*thickness));
        double nx=normal.dot(right),ny=normal.dot(up),delta=depth*depth-width*width;
        double a=Math.max(1e-10,width*width+delta*nx*nx),b=Math.max(1e-10,width*width+delta*ny*ny),c=delta*nx*ny;
        // D <= C in the positive-semidefinite ordering: an axis-aligned inscribed ellipse,
        // not the bounding box of a rotated ellipse (which would spill past surface edges).
        double k=Math.max(0,1-Math.abs(c)/Math.sqrt(a*b));
        double fixedWidth=Math.sqrt(a*k),fixedHeight=Math.sqrt(b*k);
        return new Shape((float)(fixedWidth*(1-blend)+width*blend),
            (float)(fixedHeight*(1-blend)+freeHeight*blend),0,supportLift(width,thickness));
    }
    /** Kept at release, rather than retracting the smoke centre into the hull while it expands. */
    public static double supportLift(double width,double thickness){return Math.min(Math.max(.01,width),Math.max(.01,width*thickness))*.75;}
}
