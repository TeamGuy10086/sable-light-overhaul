package dev.sablelightoverhaul;

/** Immutable shape and three-stage spatial falloff; no world reads on render workers. */
public record AreaLight(double x,double y,double z,double dx,double dy,double dz,
                        double axial,double lateral,double core,double brightness,
                        boolean cone,double middle,double tail,
                        boolean directional,boolean diamond,double rear,double vertical,double rearCutoff,
                        double ux,double uy,double uz,boolean beam) {
    /**
     * The range a beam runs to, and whether it dims along its own length.
     *
     * <p>A beam stores this in {@code axial} as a <b>signed</b> value: the magnitude is the range and a
     * negative sign means "fade out with distance as well as with angle". That is the whole protocol, and
     * it is the same on every side — {@link #at} reads it directly, the screen-space pass packs the same
     * signed number into the light buffer, and the pack reads its sign. It replaced an earlier attempt to
     * make every beam dim, which quietly changed the way a ship's searchlight looks; making it a property
     * of the light instead means a searchlight and a hand torch can differ without sharing a code path.
     */
    public double beamRange() { return Math.abs(axial); }
    /** Whether this beam fades along its length; false for every non-beam shape. */
    public boolean distanceFalloff() { return beam && axial < 0; }
    /**
     * Distance ramp knee; 0 uses the ordinary profile, -1 selects the flare's axial end fade.
     *
     * <p>0.5 means half brightness at half the range. It rides in {@code ux}, which no beam reads --
     * {@code ux/uy/uz} are the directional shape's own axes and are zero for every beam -- so the extra
     * number costs no new record component and no new argument at the call sites that do not need one.
     */
    public double distanceKnee() { return beam ? (ux < 0 ? -1.0 : Math.max(0.0, Math.min(0.95, ux))) : 0.0; }
    /** Negative knee selects an axial end fade for very wide downward flare cones.
     * Unlike ordinary spotlights, their footprint must not be clipped by a fixed sphere. */
    public boolean axialEndFade() { return beam && ux < 0; }
    public static AreaLight axialFadeBeam(double x,double y,double z,double range,double brightness) {
        return new AreaLight(x,y,z,0,-1,0,range,Math.tan(Math.toRadians(75)),0,brightness,false,
                .5,.5,false,false,range,Math.tan(Math.toRadians(75)),0,-1,0,0,true);
    }
    /** A signal keeps its spherical range; only the downward emission opening is narrowed. */
    public static AreaLight signalPoint(double x,double y,double z,double brightness,int angleDegrees) {
        int angle=Math.clamp(angleDegrees,1,360);
        return new AreaLight(x,y,z,1,0,0,12,12,.12,brightness,false,.6,.3,
                false,false,12,12,12,angle==360?0:angle,0,0,false);
    }
    /** Non-directional spheres otherwise have no use for ux. Zero means a full sphere. */
    public double signalAngle() { return !beam && !directional && !cone ? Math.max(0,ux) : 0; }
    public static double signalAngularGain(double dx,double dy,double dz,double angle) {
        double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(angle<=0 || angle>=360 || distance<.0001)return 1;
        double half=Math.toRadians(angle*.5);
        double outer=Math.cos(half),inner=Math.cos(half*.9);
        double t=Math.clamp((-dy/distance-outer)/(inner-outer),0,1);
        return t*t*(3-2*t);
    }
    public AreaLight(double x,double y,double z,double dx,double dy,double dz,double axial,double lateral,
                     double core,double brightness,boolean cone,double middle,double tail) {
        this(x,y,z,dx,dy,dz,axial,lateral,core,brightness,cone,middle,tail,false,false,axial,lateral,axial,0,0,0,false);
    }
    /** Origin is the brightest source point, not the midpoint between unequal boundaries. */
    public static AreaLight directional(double x,double y,double z,double dx,double dy,double dz,
            double front,double rear,double width,double height,double rearCutoff,
            double core,double middle,double tail,double brightness,boolean diamond) {
        double h=Math.hypot(dx,dz);
        double ux=h>1e-8 ? dz/h : 1, uz=h>1e-8 ? -dx/h : 0;
        return new AreaLight(x,y,z,dx,dy,dz,front,width,core,brightness,false,middle,tail,
                true,diamond,rear,height,rearCutoff,ux,0,uz,false);
    }
    /**
     * A beam: brightness depends solely on the angle between a point and the axis, and the range is a
     * hard cut-off. {@code axial} is that range and {@code lateral} is tan(half angle).
     */
    public static AreaLight beam(double x,double y,double z,double dx,double dy,double dz,
            double range,double halfAngleTangent,double core,double middle,double tail,double brightness) {
        return beam(x,y,z,dx,dy,dz,range,halfAngleTangent,core,middle,tail,brightness,false);
    }
    /**
     * A beam that can also fade along its length.
     *
     * <p>A ship's searchlight is meant to light the far end of its beam as brightly as the near end, so it
     * keeps the angle-only profile; a hand torch is not, and asking for {@code distanceFalloff} is how it
     * says so. Both are the same shape, and this is the only difference between them.
     */
    public static AreaLight beam(double x,double y,double z,double dx,double dy,double dz,
            double range,double halfAngleTangent,double core,double middle,double tail,double brightness,
            boolean distanceFalloff) {
        // A negative axial is the flag; the magnitude is still the range.
        return new AreaLight(x,y,z,dx,dy,dz,distanceFalloff?-range:range,halfAngleTangent,core,brightness,false,
                middle,tail,false,false,range,halfAngleTangent,0,0,0,0,true);
    }
    /**
     * A beam that fades along its length at a chosen rate.
     *
     * <p>{@code distanceKnee} is the knee of a linear ramp from full brightness at the emitter to nothing at
     * the end of the range: 0.5 gives half brightness at half the range, which is the shape the flashlight
     * shipped with, and 0 leaves the length alone entirely. The cross-section is unaffected -- it stays on the
     * profile {@code core}/{@code middle}/{@code tail} describe.
     */
    public static AreaLight beam(double x,double y,double z,double dx,double dy,double dz,
            double range,double halfAngleTangent,double core,double middle,double tail,double brightness,
            boolean distanceFalloff,double distanceKnee) {
        double knee=Math.max(0.0,Math.min(0.95,distanceKnee));
        return new AreaLight(x,y,z,dx,dy,dz,distanceFalloff?-range:range,halfAngleTangent,core,brightness,false,
                middle,tail,false,false,range,halfAngleTangent,0,knee,0,0,true);
    }
    public AreaLight(double x,double y,double z,double dx,double dy,double dz,
                     double axial,double lateral,double core,double brightness) {
        this(x,y,z,dx,dy,dz,axial,lateral,core,brightness,false,0.65,0.2);
    }
    public AreaLight {
        if(!Double.isFinite(x+y+z+dx+dy+dz+axial+lateral+core+brightness+middle+tail+rear+vertical+rearCutoff+ux+uy+uz)
                || rear<=0 || vertical<=0 || rearCutoff<0 || axial==0 || lateral<=0 || core<0 || core>=1 || middle<=core || middle>=1
                || tail<0 || tail>1 || brightness<0 || brightness>15
                || Math.abs(dx*dx+dy*dy+dz*dz-1)>0.0001) throw new IllegalArgumentException("Invalid area light");
    }
    public double at(double px,double py,double pz) {
        double vx=px-x,vy=py-y,vz=pz-z,along=vx*dx+vy*dy+vz*dz;
        double side=directional ? 0 : Math.sqrt(Math.max(0,vx*vx+vy*vy+vz*vz-along*along));
        double q;
        if(beam) {
            // The angle from the axis is the cross-section, on its own curve. A beam that is meant to fade
            // along its own length -- which the sign of its axial asks for -- multiplies in a second, separate
            // ramp over that length. The two used to share one profile through q = max(angle, along/range), so
            // a single knob moved both at once; a ship's searchlight keeps the angle-only curve it always had.
            //
            // That second ramp runs on the distance the light actually travels to this point, not on its
            // depth along the axis. `along` is where the point projects onto the centre line, so using it
            // dimmed a point on the cone's rim as if it sat on the centre -- the pool's edge came out as
            // bright as its middle, which is the report "the flashlight's distance falloff is decided by
            // its centre point". A rim point is 1/cos(angle) further from the apex; that is the ratio used
            // now, and it also makes the cut-off a sphere for a torch (the ramp is already zero past the
            // range) while a searchlight, which has no ramp, keeps its hard axial cut.
            double range=beamRange();
            if(along<=0 || along>range) return 0;
            double angle=side/(along*lateral);
            double value=falloff(angle,core,middle,tail);
            if(axialEndFade()) value*=falloff(along/range,.75,1.0,0.0);
            else if(distanceFalloff()) {
                double knee=distanceKnee();
                if(knee>0.0) value*=falloff(Math.sqrt(along*along+side*side)/range,0.0,knee,knee);
            }
            return brightness*value;
        } else if(directional) {
            if(along < -rearCutoff) return 0;
            double a=Math.abs(along)/(along>=0 ? axial : rear);
            double u=Math.abs(vx*ux+vy*uy+vz*uz)/lateral;
            double v=Math.abs(vx*(dy*uz-dz*uy)+vy*(dz*ux-dx*uz)+vz*(dx*uy-dy*ux))/vertical;
            q=diamond ? a+u+v : Math.sqrt(a*a+u*u+v*v);
        } else if(cone) {
            // x/y/z are the cone's bounds center; apex lies axial blocks behind it.
            double t=(along+axial)/(2*axial);
            if(t<0 || t>=1) return 0;
            if(t==0) return side==0?brightness:0;
            double radius=lateral*t; // true full-angle spotlight cone
            q=Math.max(t,side/radius);
        } else q=Math.sqrt(along*along/(axial*axial)+side*side/(lateral*lateral));
        return brightness*falloff(q,core,middle,tail)*signalAngularGain(vx,vy,vz,signalAngle());
    }
    public static double falloff(double q,double core,double middle,double tail) {
        if(q<=core) return 1;
        if(q>=1) return 0;
        if(q<=middle) return 1-(1-tail)*(q-core)/(middle-core);
        return tail*(1-q)/(1-middle);
    }
    public double extentX() { return beam ? beamExtent(dx) : directional ? directedExtent(dx,ux,dy*uz-dz*uy) : extent(dx); }
    public double extentY() { return beam ? beamExtent(dy) : directional ? directedExtent(dy,uy,dz*ux-dx*uz) : extent(dy); }
    public double extentZ() { return beam ? beamExtent(dz) : directional ? directedExtent(dz,uz,dx*uy-dy*ux) : extent(dz); }
    /** Bounds of the cone: the range along the axis plus the radius it reaches there. */
    private double beamExtent(double d) {
        double perpendicular=Math.sqrt(Math.max(0,1-d*d));
        return beamRange()*Math.hypot(d,lateral*perpendicular);
    }
    private double directedExtent(double d,double u,double v) {
        double a=Math.max(axial,rear)*Math.abs(d),b=lateral*Math.abs(u),c=vertical*Math.abs(v);
        return diamond ? Math.max(a,Math.max(b,c)) : Math.sqrt(a*a+b*b+c*c);
    }
    private double extent(double d) {
        return cone ? axial*Math.abs(d)+lateral*Math.sqrt(Math.max(0,1-d*d))
                    : Math.sqrt(lateral*lateral+(axial*axial-lateral*lateral)*d*d);
    }
}
