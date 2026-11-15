package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.FlameVolume;
import dev.sablelightoverhaul.GasVertexData;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Textured reference mesh and fragment-integrated gas proxy, with a simple fallback for unsupported packs. */
public final class FlameVolumeMesh {
    private FlameVolumeMesh() {}
    /** Reference renderer: three axis-locked textured planes, one common lifetime and opacity envelope. */
    public static void baseline(VertexConsumer out,Camera camera,Vec3 origin,Vec3 axis,double length,double width,
            float opacity,double roll,float u0,float u1,float v0,float v1) {
        if(opacity<=0.002)return;
        Vec3 side=axis.cross(Math.abs(axis.y)>0.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        Vec3 up=axis.cross(side),base=origin.subtract(camera.getPosition());
        float a=(float)-Math.expm1(Math.log1p(-Math.clamp(opacity,0,0.995))/3);
        int light=MuzzleParticleLayer.markLight(LightTexture.FULL_BRIGHT,true);
        for(int i=0;i<3;i++) {
            double angle=roll+i*Math.PI/3;
            Vec3 across=side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).scale(width);
            Vec3 p0=base.subtract(across),p1=base.add(across),p2=p1.add(axis.scale(length)),p3=p0.add(axis.scale(length));
            boolean front=across.cross(axis).dot(base.add(axis.scale(length*0.5)).scale(-1))>=0;
            plainVertex(out,p0,u0,v1,a,light);
            plainVertex(out,front?p1:p3,front?u0:u1,front?v0:v1,a,light);
            plainVertex(out,p2,u1,v0,a,light);
            plainVertex(out,front?p3:p1,front?u1:u0,front?v1:v0,a,light);
        }
    }
    private static void plainVertex(VertexConsumer out,Vec3 p,float u,float v,float a,int light) {
        out.addVertex((float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(1f,1f,1f,a).setLight(light);
    }
    /** One conservative front proxy. The fragment integrates through the field; no visible density planes. */
    public static void gas(VertexConsumer out, Camera camera, Vec3 origin, Vec3 axis,
            double length, double width, float opacity, double age, double seed, boolean tank, int quality,
            float whiteU, float whiteV) {
        if (opacity<=0.002 || length<=0 || width<=0) return;
        if (!MuzzleParticleLayer.proceduralGas()) {
            render(out,camera,origin,axis,length,width,opacity,seed+age*2.5,whiteU,whiteU,whiteV,whiteV,1,1,1);
            return;
        }
        Vec3 c=origin.add(axis.scale(length*0.5)).subtract(camera.getPosition());
        Vector3f r=new Vector3f(1,0,0).rotate(camera.rotation());
        Vector3f u=new Vector3f(0,1,0).rotate(camera.rotation());
        Vector3f v=new Vector3f(camera.getLookVector());
        Vec3 side=axis.cross(Math.abs(axis.y)>0.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        Vec3 up=axis.cross(side);
        double ar=axis.x*r.x+axis.y*r.y+axis.z*r.z;
        double au=axis.x*u.x+axis.y*u.y+axis.z*u.z;
        double av=axis.x*v.x+axis.y*v.y+axis.z*v.z;
        double ex=Math.abs(ar)*length*0.5+width*Math.sqrt(Math.max(0,1-ar*ar));
        double ey=Math.abs(au)*length*0.5+width*Math.sqrt(Math.max(0,1-au*au));
        double ez=Math.abs(av)*length*0.5+width*Math.sqrt(Math.max(0,1-av*av));
        double depth=c.x*v.x+c.y*v.y+c.z*v.z;
        if(depth+ez<=0.05)return;
        double near=Math.max(0.05,depth-ez),far=Math.max(near+0.001,depth+ez);
        double cx=c.x*r.x+c.y*r.y+c.z*r.z,cy=c.x*u.x+c.y*u.y+c.z*u.z;
        // Project all corners of the clipped view-space box onto its near plane. Includes off-axis/inside views.
        double x0=Math.min((cx-ex)/near,(cx-ex)/far)*near-cx;
        double x1=Math.max((cx+ex)/near,(cx+ex)/far)*near-cx;
        double y0=Math.min((cy-ey)/near,(cy-ey)/far)*near-cy;
        double y1=Math.max((cy+ey)/near,(cy+ey)/far)*near-cy;
        float phaseData=(float)Math.clamp(age,0,1),aspect=GasVertexData.aspect(width/length);
        int samples=c.lengthSqr()>16384?8:Math.clamp(quality,8,24);
        double z=near-depth;
        gasVertex(out,c,r,u,v,x0,y0,z,axis,side,up,length,width,opacity,phaseData,aspect,seed,tank,samples);
        gasVertex(out,c,r,u,v,x1,y0,z,axis,side,up,length,width,opacity,phaseData,aspect,seed,tank,samples);
        gasVertex(out,c,r,u,v,x1,y1,z,axis,side,up,length,width,opacity,phaseData,aspect,seed,tank,samples);
        gasVertex(out,c,r,u,v,x0,y1,z,axis,side,up,length,width,opacity,phaseData,aspect,seed,tank,samples);
    }
    private static void gasVertex(VertexConsumer out,Vec3 c,Vector3f r,Vector3f u,Vector3f v,
            double x,double y,double z,Vec3 axis,Vec3 side,Vec3 up,double length,double width,
            float opacity,float phase,float step,double seed,boolean tank,int samples) {
        double dx=r.x*x+u.x*y+v.x*z,dy=r.y*x+u.y*y+v.y*z,dz=r.z*x+u.z*y+v.z*z;
        float along=(float)((dx*axis.x+dy*axis.y+dz*axis.z)/length+0.5);
        float across=(float)((dx*side.x+dy*side.y+dz*side.z)/width);
        double height=(dx*up.x+dy*up.y+dz*up.z)/width;
        out.addVertex((float)(c.x+dx),(float)(c.y+dy),(float)(c.z+dz))
            .setUv(along,across).setColor(phase,step,((tank?32:0)+samples)/64f,opacity).setLight(GasVertexData.light(height,seed));
    }
    // Render-thread scratch: no grid/vertex allocations while walking a slice.
    private static final float[] ALPHA = new float[81];
    public static void render(VertexConsumer out, Camera camera, Vec3 origin, Vec3 axis,
            double length, double width, float opacity, double phase,
            float u0, float u1, float v0, float v1, float red, float green, float blue) {
        if (opacity <= 0.001 || length <= 0 || width <= 0) return;
        Vec3 centre = origin.add(axis.scale(length*0.5)).subtract(camera.getPosition());
        Vector3f right = new Vector3f(1,0,0).rotate(camera.rotation());
        Vector3f up = new Vector3f(0,1,0).rotate(camera.rotation());
        Vector3f look = new Vector3f(camera.getLookVector());
        Vec3 side = axis.cross(Math.abs(axis.y)>0.9 ? new Vec3(1,0,0) : new Vec3(0,1,0)).normalize();
        double ar = axis.x*right.x + axis.y*right.y + axis.z*right.z;
        double au = axis.x*up.x + axis.y*up.y + axis.z*up.z;
        double av = axis.x*look.x + axis.y*look.y + axis.z*look.z;
        double ex = Math.abs(ar)*length*0.5 + width*Math.sqrt(Math.max(0,1-ar*ar));
        double ey = Math.abs(au)*length*0.5 + width*Math.sqrt(Math.max(0,1-au*au));
        double ez = Math.abs(av)*length*0.5 + width*Math.sqrt(Math.max(0,1-av*av));
        int grid = centre.lengthSqr() > 4096 ? 6 : 8;
        int slices = grid == 6 ? 3 : 5;
        double step = 2*ez/slices;
        int light = MuzzleParticleLayer.markLight(LightTexture.FULL_BRIGHT, true);
        for (int s = 0; s < slices; s++) {
            double z = ez-(s+0.5)*step; // far to near; every layer tests terrain depth, none writes it
            for (int y = 0; y <= grid; y++) for (int x = 0; x <= grid; x++) {
                double px = ex*(2.0*x/grid-1), py = ey*(2.0*y/grid-1);
                double axial = px*ar + py*au + z*av;
                double radial = Math.sqrt(Math.max(0, px*px+py*py+z*z-axial*axial))/width;
                double density = FlameVolume.density(axial/length+0.5, radial, phase);
                ALPHA[y*(grid+1)+x] = FlameVolume.opacity(density,step,width,opacity);
            }
            for (int y = 0; y < grid; y++) for (int x = 0; x < grid; x++) {
                int i = y*(grid+1)+x;
                if (ALPHA[i]+ALPHA[i+1]+ALPHA[i+grid+1]+ALPHA[i+grid+2] < 0.001) continue;
                vertex(out,centre,right,up,look,ex,ey,z,x,y,grid,ALPHA[i],axis,side,length,width,phase,u0,u1,v0,v1,light,red,green,blue);
                vertex(out,centre,right,up,look,ex,ey,z,x+1,y,grid,ALPHA[i+1],axis,side,length,width,phase,u0,u1,v0,v1,light,red,green,blue);
                vertex(out,centre,right,up,look,ex,ey,z,x+1,y+1,grid,ALPHA[i+grid+2],axis,side,length,width,phase,u0,u1,v0,v1,light,red,green,blue);
                vertex(out,centre,right,up,look,ex,ey,z,x,y+1,grid,ALPHA[i+grid+1],axis,side,length,width,phase,u0,u1,v0,v1,light,red,green,blue);
            }
        }
    }
    private static void vertex(VertexConsumer out, Vec3 c, Vector3f r, Vector3f up, Vector3f look,
            double ex,double ey,double z,int x,int y,int grid,float alpha,Vec3 axis,Vec3 side,
            double length,double width,double phase,float u0,float u1,float v0,float v1,int light,
            float red,float green,float blue) {
        double px=ex*(2.0*x/grid-1), py=ey*(2.0*y/grid-1);
        double dx=r.x*px+up.x*py+look.x*z, dy=r.y*px+up.y*py+look.y*z, dz=r.z*px+up.z*py+look.z*z;
        double along=(dx*axis.x+dy*axis.y+dz*axis.z)/length+0.5;
        double across=(dx*side.x+dy*side.y+dz*side.z)/width;
        float u=u0+(u1-u0)*FlameVolume.textureU(along,across,phase);
        float v=v0+(v1-v0)*FlameVolume.textureV(along,across,phase);
        float hot=Math.min(1,alpha*3);
        out.addVertex((float)(c.x+r.x*px+up.x*py+look.x*z),
                (float)(c.y+r.y*px+up.y*py+look.y*z), (float)(c.z+r.z*px+up.z*py+look.z*z))
                .setUv(u,v).setColor(red,green*(0.65f+0.35f*hot),blue*(0.35f+0.65f*hot),alpha).setLight(light);
    }
}
