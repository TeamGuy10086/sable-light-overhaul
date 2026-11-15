package dev.sablelightoverhaul;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Bounded ray against outward-facing render triangles; all math relative to a vertex. */
public final class HullVisualGeometry {
    private HullVisualGeometry(){}
    public record Triangle(Vec3 a,Vec3 b,Vec3 c){}
    public record Hit(Vec3 point,Vec3 normal,double shift){}
    public static Hit project(List<Triangle> triangles,Vec3 surface,Vec3 outward,double reach){
        if(triangles==null||!finite(surface)||!finite(outward)||!Double.isFinite(reach)||reach<=0||outward.lengthSqr()<1e-12)return null;
        Vec3 axis=outward.normalize(),dir=axis.scale(-1);Hit best=null;double nearest=2*reach+1e-8;
        for(var face:triangles){
            if(face==null||!finite(face.a)||!finite(face.b)||!finite(face.c))continue;
            Vec3 e1=face.b.subtract(face.a),e2=face.c.subtract(face.a),cross=e1.cross(e2);
            if(!finite(cross)||cross.lengthSqr()<1e-16)continue;
            Vec3 normal=cross.normalize();if(normal.dot(axis)<.25)continue;
            Vec3 h=dir.cross(e2);double det=e1.dot(h);if(Math.abs(det)<1e-12)continue;
            Vec3 rel=surface.subtract(face.a).add(axis.scale(reach));double u=rel.dot(h)/det;
            if(u< -1e-8||u>1+1e-8)continue;
            Vec3 q=rel.cross(e1);double v=dir.dot(q)/det;
            if(v< -1e-8||u+v>1+1e-8)continue;
            double t=e2.dot(q)/det;if(!Double.isFinite(t)||t<0||t>2*reach||t>=nearest)continue;
            double shift=reach-t;Vec3 at=surface.add(axis.scale(shift));if(!finite(at))continue;
            nearest=t;best=new Hit(at,normal,shift);
        }
        return best;
    }
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
}
