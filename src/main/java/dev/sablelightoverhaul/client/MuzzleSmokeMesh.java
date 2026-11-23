package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Camera-facing quads; UVs may address the atlas or carry the owned soft-smoke plane coordinates. */
public final class MuzzleSmokeMesh {
    private MuzzleSmokeMesh() {}

    /** One authoritative quad, kept for the atlas/fallback path and for the existing mesh contract test. */
    public static void draw(VertexConsumer out, Camera camera,
            double x,double y,double z,double xo,double yo,double zo,
            float partialTick,float size,float roll,float u0,float u1,float v0,float v1,
            float red,float green,float blue,float alpha,int light) {
        quad(out,camera,x,y,z,xo,yo,zo,partialTick,size,roll,u0,u1,v0,v1,
                red,green,blue,alpha,light,0,0);
    }

    static void drawAtlasBillboard(VertexConsumer out, Camera camera,
            double x,double y,double z,double xo,double yo,double zo,
            float partial,float width,float height,float u0,float u1,float v0,float v1,
            float red,float green,float blue,float alpha,int light) {
        quadShaped(out,camera,x,y,z,xo,yo,zo,partial,width,height,0,u0,u1,v0,v1,
            red,green,blue,alpha,light,0,0);
    }

    /**
     * Three overlapping, offset quads for the procedural smoke mask.
     *
     * <p>A single camera-facing quad with a radial mask is mathematically a disc; at the sizes the tank
     * smoke reaches it reads as a perfect sphere. The offset lobes give the silhouette a second and third
     * centre without changing the particle count, size budget or lifetime. Each lobe uses its own mask
     * seed, so it is not the same circle stamped three times.
     */
    public static void drawPuff(VertexConsumer out, Camera camera,
            double x,double y,double z,double xo,double yo,double zo,
            float partialTick,float size,float roll,int seed,
            float red,float green,float blue,float alpha,int light) {
        drawPuffShaped(out,camera,x,y,z,xo,yo,zo,partialTick,size,size,roll,seed,
                red,green,blue,alpha,light);
    }

    /**
     * Three lobes with independent horizontal/vertical growth. The non-uniform size is what gives
     * the cloud a little volume without a second particle system: two puffs from the same emitter
     * no longer grow as the same circle. {@code sizeX == sizeY} is exactly {@link #drawPuff}.
     */
    public static void drawPuffShaped(VertexConsumer out, Camera camera,
            double x,double y,double z,double xo,double yo,double zo,
            float partialTick,float sizeX,float sizeY,float roll,int seed,
            float red,float green,float blue,float alpha,int light) {
        if (MuzzleParticleLayer.mode() == MuzzleParticleLayer.Mode.SINGLE) {
            // Diagnostic only: the same mask at full extent and full alpha, so a surviving rim can
            // be attributed to the mask itself rather than to the union of three offset lobes.
            quadShaped(out,camera,x,y,z,xo,yo,zo,partialTick,sizeX,sizeY,roll,
                    uv0(seed),uv0(seed)+2,1,-1,red,green,blue,alpha,light,0,0);
            return;
        }
        quadShaped(out,camera,x,y,z,xo,yo,zo,partialTick,sizeX*0.68F,sizeY*0.68F,roll,
                uv0(seed),uv0(seed)+2,1,-1,red,green,blue,alpha*0.58F,light,0,0);
        quadShaped(out,camera,x,y,z,xo,yo,zo,partialTick,sizeX*0.48F,sizeY*0.48F,roll+0.82F,
                uv0(seed+17),uv0(seed+17)+2,1,-1,red,green,blue,alpha*0.44F,light,sizeX*0.22F,sizeY*0.12F);
        quadShaped(out,camera,x,y,z,xo,yo,zo,partialTick,sizeX*0.40F,sizeY*0.40F,roll-1.13F,
                uv0(seed+33),uv0(seed+33)+2,1,-1,red,green,blue,alpha*0.36F,light,-sizeX*0.20F,sizeY*0.16F);
    }

    /**
     * The same three-lobe silhouette, but sampling a real atlas sprite instead of the procedural
     * mask. This is the Photon-safe route: the vanilla translucent particle program draws it,
     * while the custom muzzle_particles program is ignored under Iris. UVs come from the sprite.
     */
    public static void drawAtlasPuff(VertexConsumer out, Camera camera,
            double x,double y,double z,double xo,double yo,double zo,
            float partialTick,float sizeX,float sizeY,float roll,
            float u0,float u1,float v0,float v1,
            float red,float green,float blue,float alpha,int light) {
        quadShaped(out,camera,x,y,z,xo,yo,zo,partialTick,sizeX*0.68F,sizeY*0.68F,roll,
                u0,u1,v0,v1,red,green,blue,alpha*0.58F,light,0,0);
        quadShaped(out,camera,x,y,z,xo,yo,zo,partialTick,sizeX*0.48F,sizeY*0.48F,roll+0.82F,
                u0,u1,v0,v1,red,green,blue,alpha*0.44F,light,sizeX*0.22F,sizeY*0.12F);
        quadShaped(out,camera,x,y,z,xo,yo,zo,partialTick,sizeX*0.40F,sizeY*0.40F,roll-1.13F,
                u0,u1,v0,v1,red,green,blue,alpha*0.36F,light,-sizeX*0.20F,sizeY*0.16F);
    }

    /** Three soft lobes sharing the projected hull plane axes, including their offsets. */
    public static void drawHullCluster(VertexConsumer out,Camera camera,double x,double y,double z,double xo,double yo,double zo,
            float partial,float width,float height,float roll,int seed,float red,float green,float blue,float alpha,int light,
            boolean procedural,float u0,float u1,float v0,float v1){
        float cs=(float)Math.cos(roll),sn=(float)Math.sin(roll);
        for(int i=0;i<3;i++){
            float scale=i==0?.68f:i==1?.48f:.40f,weight=i==0?.58f:i==1?.44f:.36f;
            float dx=i==0?0:i==1?width*.22f:-width*.20f,dy=i==0?0:i==1?height*.12f:height*.16f;
            int id=seed+(i==0?0:i==1?17:33);
            quadShaped(out,camera,x,y,z,xo,yo,zo,partial,width*scale,height*scale,roll,
                procedural?uv0(id):u0,procedural?uv0(id)+2:u1,procedural?1:v0,procedural?-1:v1,
                red,green,blue,alpha*weight,light,dx*cs-dy*sn,dx*sn+dy*cs);
        }
    }

    /** A thin dust skin constrained to one real surface rectangle; feather its cropped edges. */
    static void drawSurfaceSkin(VertexConsumer out,Camera camera,HullSurfaceAnchor.Skin skin,int seed,
            float red,float green,float blue,float alpha,int light,boolean procedural,float u0,float u1,float v0,float v1,float thickness){
        var eye=camera.getPosition();var c=skin.center().subtract(eye);
        var normal=skin.right().cross(skin.up()).normalize();
        float depth=Math.max(0,Math.min(thickness,Math.min(skin.halfU(),skin.halfV())*.65f));
        for(int row=0;row<3;row++)for(int col=0;col<3;col++){
            for(int corner=0;corner<4;corner++){
                int gx=col+(corner<2?1:0),gy=row+(corner==1||corner==2?1:0);
                float fx=gx/3f,fy=gy/3f;
                double dx=(fx*2-1)*skin.halfU(),dy=(fy*2-1)*skin.halfV();
                // Raised interior, zero-height boundary: a shallow skin, not a floating billboard.
                float edge=gx==0||gx==3||gy==0||gy==3?0:1;
                double relief=depth*edge*(.85+.15*Math.sin(seed*1.7+gx*2.1+gy*1.3));
                var at=c.add(skin.right().scale(dx)).add(skin.up().scale(dy)).add(normal.scale(relief));
                float su=skin.minU()+(skin.maxU()-skin.minU())*fx,sv=skin.minV()+(skin.maxV()-skin.minV())*fy;
                float tu=procedural?uv0(seed)+1+su:u0+(u1-u0)*(su+1)*.5f;
                float tv=procedural?sv:v0+(v1-v0)*(sv+1)*.5f;
                vertex(out,(float)at.x,(float)at.y,(float)at.z,tu,tv,red,green,blue,alpha*edge,light);
            }
        }
    }

    /** Reserved seed range carries thermal age; normal smoke seeds remain strictly 0..63. */
    public static void drawThermalPuff(VertexConsumer out,Camera camera,double x,double y,double z,double xo,double yo,double zo,
            float partial,float sizeX,float sizeY,float roll,int encoded,float alpha,float steady,float breakup,int light){
        drawThermalPuff(out,camera,x,y,z,xo,yo,zo,partial,sizeX,sizeY,roll,encoded,alpha,steady,breakup,0,light);
    }

    public static void drawThermalPuff(VertexConsumer out,Camera camera,double x,double y,double z,double xo,double yo,double zo,
            float partial,float sizeX,float sizeY,float roll,int encoded,float alpha,float steady,float breakup,float densityDetail,int light){
        int frame=(encoded-128)/64,seed=(encoded-128)&63;
        for(int i=0;i<3;i++){
            float u=(128+64*frame+((seed+i*17)&63))*4.0f-1;
            float scale=i==0?.68f:i==1?.48f:.40f;
            float weight=i==0?.58f:i==1?.44f:.36f;
            // Red carries steady opacity*weight so the shader can tell the lifecycle fade from the
            // configured density: green carries the late breakup weight.
            quadShaped(out,camera,x,y,z,xo,yo,zo,partial,sizeX*scale,sizeY*scale,roll+(i==1?.82f:i==2?-1.13f:0),
                u,u+2,1,-1,steady*weight,breakup,1-densityDetail/3,alpha*weight,light,i==1?sizeX*.22f:i==2?-sizeX*.20f:0,i==1?sizeY*.12f:i==2?sizeY*.16f:0);
        }
    }

    /** One lighting pass for the same three soft lobes; negative seed retains the existing float precision. */
    public static void drawThermalCluster(VertexConsumer out,Camera camera,double x,double y,double z,double xo,double yo,double zo,
            float partial,float sizeX,float sizeY,float roll,int encoded,float alpha,float steady,float breakup,int light){
        drawThermalCluster(out,camera,x,y,z,xo,yo,zo,partial,sizeX,sizeY,roll,encoded,alpha,steady,breakup,0,light);
    }

    public static void drawThermalCluster(VertexConsumer out,Camera camera,double x,double y,double z,double xo,double yo,double zo,
            float partial,float sizeX,float sizeY,float roll,int encoded,float alpha,float steady,float breakup,float densityDetail,int light){
        drawThermalCluster(out,camera,x,y,z,xo,yo,zo,partial,sizeX,sizeY,roll,encoded,alpha,steady,breakup,densityDetail,light,false);
    }

    static void drawThermalCluster(VertexConsumer out,Camera camera,double x,double y,double z,double xo,double yo,double zo,
            float partial,float sizeX,float sizeY,float roll,int encoded,float alpha,float steady,float breakup,float densityDetail,int light,
            boolean groundFireball){
        float u=-encoded*4.0f;
        float v=groundFireball ? 2.0f : 0.0f;
        quadShaped(out,camera,x,y,z,xo,yo,zo,partial,sizeX*.8f,sizeY*.8f,roll,
            u-.8f,u+.8f,v+.8f,v-.8f,steady,breakup,1-densityDetail/3,alpha,light,0,0);
    }

    /** Reserved -64..-127 seeds select a cold cached cluster; cannot collide with thermal >=128. */
    public static void drawCachedPuff(VertexConsumer out,Camera camera,double x,double y,double z,double xo,double yo,double zo,
            float partial,float sizeX,float sizeY,float roll,int seed,float red,float green,float blue,float alpha,int light){
        drawCachedPuff(out,camera,x,y,z,xo,yo,zo,partial,sizeX,sizeY,roll,seed,false,red,green,blue,alpha,light,false,false);
    }

    static void drawCachedPuff(VertexConsumer out, Camera camera,double x,double y,double z,double xo,double yo,double zo,
            float partial,float sizeX,float sizeY,float roll,int seed,boolean dense,float red,float green,float blue,float alpha,int light,
            boolean impactSmoke,boolean thickCore){
        float u = (dense ? 64 + (seed & 63) : -(64 + (seed & 63))) * 4.0f;
        // Separate UV bands distinguish thick ground smoke and the softer buried smoke foot.
        float v = thickCore ? 2.0f : impactSmoke && !dense ? 4.0f : 0.0f;
        quadShaped(out,camera,x,y,z,xo,yo,zo,partial,sizeX*.8f,sizeY*.8f,roll,
            u-.8f,u+.8f,v+.8f,v-.8f,red,green,blue,alpha,light,0,0);
    }

    private static float uv0(int seed) {
        return (seed & 63)*4.0F-1.0F;
    }

    private static void quadShaped(VertexConsumer out, Camera camera,
            double x,double y,double z,double xo,double yo,double zo,
            float partialTick,float sizeX,float sizeY,float roll,float u0,float u1,float v0,float v1,
            float red,float green,float blue,float alpha,int light,
            float offsetRight,float offsetUp) {
        Quaternionf rotation=new Quaternionf(camera.rotation()).rotateZ(roll);
        Vector3f right=new Vector3f(sizeX,0,0).rotate(rotation);
        Vector3f up=new Vector3f(0,sizeY,0).rotate(rotation);
        Vector3f cameraRight=new Vector3f(1,0,0).rotate(camera.rotation());
        Vector3f cameraUp=new Vector3f(0,1,0).rotate(camera.rotation());
        float shiftX=cameraRight.x*offsetRight+cameraUp.x*offsetUp;
        float shiftY=cameraRight.y*offsetRight+cameraUp.y*offsetUp;
        float shiftZ=cameraRight.z*offsetRight+cameraUp.z*offsetUp;
        var eye=camera.getPosition();
        float cx=(float)(Mth.lerp(partialTick,xo,x)-eye.x)+shiftX;
        float cy=(float)(Mth.lerp(partialTick,yo,y)-eye.y)+shiftY;
        float cz=(float)(Mth.lerp(partialTick,zo,z)-eye.z)+shiftZ;
        vertex(out,cx+right.x-up.x,cy+right.y-up.y,cz+right.z-up.z,u1,v1,red,green,blue,alpha,light);
        vertex(out,cx+right.x+up.x,cy+right.y+up.y,cz+right.z+up.z,u1,v0,red,green,blue,alpha,light);
        vertex(out,cx-right.x+up.x,cy-right.y+up.y,cz-right.z+up.z,u0,v0,red,green,blue,alpha,light);
        vertex(out,cx-right.x-up.x,cy-right.y-up.y,cz-right.z-up.z,u0,v1,red,green,blue,alpha,light);
    }

    private static void quad(VertexConsumer out, Camera camera,
            double x,double y,double z,double xo,double yo,double zo,
            float partialTick,float size,float roll,float u0,float u1,float v0,float v1,
            float red,float green,float blue,float alpha,int light,
            float offsetRight,float offsetUp) {
        Quaternionf rotation=new Quaternionf(camera.rotation()).rotateZ(roll);
        Vector3f right=new Vector3f(size,0,0).rotate(rotation);
        Vector3f up=new Vector3f(0,size,0).rotate(rotation);
        Vector3f cameraRight=new Vector3f(1,0,0).rotate(camera.rotation());
        Vector3f cameraUp=new Vector3f(0,1,0).rotate(camera.rotation());
        float shiftX=cameraRight.x*offsetRight+cameraUp.x*offsetUp;
        float shiftY=cameraRight.y*offsetRight+cameraUp.y*offsetUp;
        float shiftZ=cameraRight.z*offsetRight+cameraUp.z*offsetUp;
        var eye=camera.getPosition();
        float cx=(float)(Mth.lerp(partialTick,xo,x)-eye.x)+shiftX;
        float cy=(float)(Mth.lerp(partialTick,yo,y)-eye.y)+shiftY;
        float cz=(float)(Mth.lerp(partialTick,zo,z)-eye.z)+shiftZ;
        vertex(out,cx+right.x-up.x,cy+right.y-up.y,cz+right.z-up.z,u1,v1,red,green,blue,alpha,light);
        vertex(out,cx+right.x+up.x,cy+right.y+up.y,cz+right.z+up.z,u1,v0,red,green,blue,alpha,light);
        vertex(out,cx-right.x+up.x,cy-right.y+up.y,cz-right.z+up.z,u0,v0,red,green,blue,alpha,light);
        vertex(out,cx-right.x-up.x,cy-right.y-up.y,cz-right.z-up.z,u0,v1,red,green,blue,alpha,light);
    }

    private static void vertex(VertexConsumer out,float x,float y,float z,float u,float v,
            float r,float g,float b,float a,int light) {
        out.addVertex(x,y,z).setUv(u,v).setColor(r,g,b,a).setLight(light);
    }
}
