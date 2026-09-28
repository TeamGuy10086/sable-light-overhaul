package dev.cbcmuzzlelight.client;

import dev.cbcmuzzlelight.AreaLight;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/** Matrix is copied on creation and never exposed; worker threads do not touch live Sable poses. */
public final class AreaSnapshot {
    public final AreaLight light;
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
    /** Most lights have no opinion: the class they belong to decides (see the client's config). */
    public static final int NO_SHADOW_OPINION=-1;
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
        this.light=light; this.bounds=bounds; this.color=color; this.selfCull=selfCull;
        this.shadowMode=shadowMode; this.shadowSteps=shadowSteps;
        this.toWorld=toWorld==null?null:new Matrix4d(toWorld);
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
        return other!=null && color==other.color && selfCull==other.selfCull && light.equals(other.light) && bounds.equals(other.bounds)
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
