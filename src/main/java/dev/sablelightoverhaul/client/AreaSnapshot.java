package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.AreaLight;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/** Matrix is copied on creation and never exposed; worker threads do not touch live Sable poses. */
public final class AreaSnapshot {
    public final AreaLight light;
    /** GPU radiance multiplier; independent of vanilla block-light brightness. */
    public final float radiance;
    public final AABB bounds;
    /**
     * Light colour as a palette index: {@code 0} means the client's default colour, 1..14 a dye
     * tint and 15 the flashing cycle. Only the in-pack (Advanced) tier reads it.
     */
    public final int color;
    /**
     * Whether this light must not illuminate its own block: a registered lamp would otherwise wash
     * its own texture out to white, because the light starts inside it. Only the in-pack tier reads it.
     */
    public final boolean selfCull;
    /**
     * What this light's own page asked its shadow to look like, or {@link #NO_SHADOW_OPINION}.
     *
     * <p>0 means the light does not want a shadow at all, 1 a hard edge, 2 a soft one. It is a wish and
     * not a decision: the client's own config caps the tap count and can force hard edges outright, so
     * a lamp somebody else placed can ask for an expensive shadow but can never spend your frame time
     * without your leave.
     */
    public final int shadowMode;
    /** This light's own tap ceiling, or 0 to take the client's. Only ever lowers it. */
    public final int shadowSteps;
    /**
     * This lamp's own cloud reach, in blocks, or {@link #NO_CLOUD_OPINION} for a light that has no page.
     *
     * <p>Zero is a real answer here and means the lamp follows the client's global
     * {@code clouds.sphereReach}; only a light with no page at all carries the sentinel, and for those the
     * class rule decides (a muzzle flash or a tracer lights the clouds out to its own radius, a lamp does
     * not light them unless it says so). See {@code AdvancedLights.cloudReachFor}.
     */
    public final int cloudReach;
    /** Most lights have no opinion: the class they belong to decides (see the client's config). */
    public static final int NO_SHADOW_OPINION=-1;
    /** A light with no page at all: no registered lamp's answer, so the class rule applies. */
    public static final int NO_CLOUD_OPINION=Integer.MIN_VALUE;
    /**
     * Whether this lamp is lit the way vanilla block light is -- omni-directional, no ambient occlusion
     * and no shadow -- instead of by this mod's shaped term.
     *
     * <p>Only a registered lamp has an opinion; every other light is false, which is the shaping this
     * mod was built for. See {@code RegisteredSources.Entry.flood} for why the default for a lamp is the
     * other way round.
     */
    public final boolean flood;
    /**
     * Which of the six block faces this lamp emits through, bit i for {@code RegisteredSources.FACE_BITS};
     * {@link #FACES_EVERY} for a light with no page. Spheres only (a beam already says where it points),
     * and a sample inside the lamp's own block is exempt so an excluded face cannot leave the fixture dark.
     */
    public final int faces;
    /** Every face emits: the answer for a light that has no page, and the default for a new lamp. */
    public static final int FACES_EVERY=63;
    /**
     * The directions this lamp may cast in, as packed cone words ({@code Cones}); a zero word is no cone,
     * so a light with no page carries four zeros and behaves exactly as it always did.
     *
     * <p>The words are packed on the server from the text the page was given. Nothing here interprets
     * them: the shader is the only side that has to, and it does it once per fragment per lamp.
     */
    public final int coneA,coneB,coneC,coneD;
    /**
     * Which physics body's frame this lamp's cones are measured in, or 0 for the world.
     *
     * <p>The shader's table of body rotations is indexed by this, so that a cone painted on a hull turns
     * with the hull. Everything that is not a registered lamp carries 0, and 0 is the identity, so a light
     * with no cones and no hull behaves exactly as it always did.
     */
    public final int bodySlot;
    /**
     * The rotation of the body this lamp's light field was flooded in, as x,y,z of a unit quaternion with
     * w &gt;= 0, or null when the field lives in the world's own frame.
     *
     * <p>It rides in the light's shape vector rather than in the shared body table: a field lamp never
     * reads core/middle/tail, and a table lookup that came back as the world slot would rotate the sample
     * by the identity -- which is exactly what "the hull's shading changes when the hull turns" looks like.
     */
    public final float[] bodyQuat;
    private final Matrix4d toWorld;
    public AreaSnapshot(AreaLight light, AABB bounds, Matrix4d toWorld) {
        this(light,bounds,toWorld,0);
    }
    public AreaSnapshot(AreaLight light, AABB bounds, Matrix4d toWorld, int color) {
        this(light,bounds,toWorld,color,false);
    }
    public AreaSnapshot(AreaLight light, AABB bounds, Matrix4d toWorld, int color, boolean selfCull) {
        this(light,bounds,toWorld,color,selfCull,NO_SHADOW_OPINION,0);
    }
    public AreaSnapshot(AreaLight light, AABB bounds, Matrix4d toWorld, int color, boolean selfCull,
                        int shadowMode, int shadowSteps) {
        this(light,bounds,toWorld,color,selfCull,shadowMode,shadowSteps,NO_CLOUD_OPINION);
    }
    public AreaSnapshot(AreaLight light, AABB bounds, Matrix4d toWorld, int color, boolean selfCull,
                        int shadowMode, int shadowSteps, int cloudReach) {
        this(light,bounds,toWorld,color,selfCull,shadowMode,shadowSteps,cloudReach,false,FACES_EVERY);
    }
    public AreaSnapshot(AreaLight light, AABB bounds, Matrix4d toWorld, int color, boolean selfCull,
                        int shadowMode, int shadowSteps, int cloudReach, boolean flood, int faces) {
        this(light,bounds,toWorld,color,selfCull,shadowMode,shadowSteps,cloudReach,flood,faces,0,0,0,0);
    }
    public AreaSnapshot(AreaLight light, AABB bounds, Matrix4d toWorld, int color, boolean selfCull,
                        int shadowMode, int shadowSteps, int cloudReach, boolean flood, int faces,
                        int coneA, int coneB, int coneC, int coneD) {
        this(light,bounds,toWorld,color,selfCull,shadowMode,shadowSteps,cloudReach,flood,faces,
                coneA,coneB,coneC,coneD,0);
    }
    public AreaSnapshot(AreaLight light, AABB bounds, Matrix4d toWorld, int color, boolean selfCull,
                        int shadowMode, int shadowSteps, int cloudReach, boolean flood, int faces,
                        int coneA, int coneB, int coneC, int coneD, int bodySlot) {
        this(light,bounds,toWorld,color,selfCull,shadowMode,shadowSteps,cloudReach,flood,faces,
                coneA,coneB,coneC,coneD,bodySlot,null);
    }
    public AreaSnapshot(AreaLight light, AABB bounds, Matrix4d toWorld, int color, boolean selfCull,
                        int shadowMode, int shadowSteps, int cloudReach, boolean flood, int faces,
                        int coneA, int coneB, int coneC, int coneD, int bodySlot, float[] bodyQuat) {
        this(light,bounds,toWorld,color,selfCull,shadowMode,shadowSteps,cloudReach,flood,faces,coneA,coneB,coneC,coneD,bodySlot,bodyQuat,1);
    }
    private AreaSnapshot(AreaLight light,AABB bounds,Matrix4d toWorld,int color,boolean selfCull,
            int shadowMode,int shadowSteps,int cloudReach,boolean flood,int faces,int coneA,int coneB,int coneC,int coneD,
            int bodySlot,float[] bodyQuat,float radiance) {
        if(!Float.isFinite(radiance)||radiance<0||radiance>8) throw new IllegalArgumentException("Invalid radiance");
        this.radiance=radiance;
        this.light=light; this.bounds=bounds; this.color=color; this.selfCull=selfCull;
        this.shadowMode=shadowMode; this.shadowSteps=shadowSteps; this.cloudReach=cloudReach;
        this.flood=flood; this.faces=faces;
        this.coneA=coneA; this.coneB=coneB; this.coneC=coneC; this.coneD=coneD;
        this.bodySlot=bodySlot;
        this.bodyQuat=bodyQuat;
        this.toWorld=toWorld==null?null:new Matrix4d(toWorld);
    }
    public AreaSnapshot withRadiance(float gain) {
        return new AreaSnapshot(light,bounds,toWorld,color,selfCull,shadowMode,shadowSteps,cloudReach,flood,faces,
            coneA,coneB,coneC,coneD,bodySlot,bodyQuat,gain);
    }
    public double at(double x,double y,double z) {
        if (!bounds.contains(x,y,z)) return 0;
        if (toWorld==null) return light.at(x,y,z);
        // No temporary vector allocations in per-block lighting queries.
        return light.at(toWorld.m00()*x+toWorld.m10()*y+toWorld.m20()*z+toWorld.m30(),
                toWorld.m01()*x+toWorld.m11()*y+toWorld.m21()*z+toWorld.m31(),
                toWorld.m02()*x+toWorld.m12()*y+toWorld.m22()*z+toWorld.m32());
    }
    public boolean same(AreaSnapshot other) {
        return other!=null && radiance==other.radiance && color==other.color && selfCull==other.selfCull && light.equals(other.light) && bounds.equals(other.bounds)
                && java.util.Objects.equals(toWorld,other.toWorld);
    }
    public static AABB bounds(AreaLight l) {
        return new AABB(l.x()-l.extentX(),l.y()-l.extentY(),l.z()-l.extentZ(),
                l.x()+l.extentX(),l.y()+l.extentY(),l.z()+l.extentZ());
    }
    public static AABB transform(AABB b,Matrix4d m) {
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
        for(int i=0;i<8;i++) {
            var v=m.transformPosition(new Vector3d((i&1)==0?b.minX:b.maxX,(i&2)==0?b.minY:b.maxY,(i&4)==0?b.minZ:b.maxZ));
            minX=Math.min(minX,v.x); minY=Math.min(minY,v.y); minZ=Math.min(minZ,v.z);
            maxX=Math.max(maxX,v.x); maxY=Math.max(maxY,v.y); maxZ=Math.max(maxZ,v.z);
        }
        return new AABB(minX,minY,minZ,maxX,maxY,maxZ);
    }
}
