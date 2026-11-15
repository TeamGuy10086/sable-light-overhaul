package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.CorridorClipper;
import dev.sablelightoverhaul.GroundDustFieldModel.Node;
import net.minecraft.world.phys.Vec3;

/** Per-particle reusable adapter; feathers the barrel corridor across rendered lobes. */
final class CorridorVertexConsumer implements VertexConsumer {
    private final CorridorClipper clip=new CorridorClipper(this::quad);
    private VertexConsumer target;
    private float x,y,z,u,v,r,g,b,a;
    private int light;
    VertexConsumer bind(VertexConsumer target,Vec3 eye,Node node){
        this.target=target;
        var origin=node.corridorOrigin();
        var axis=node.corridorAxis();
        clip.cylinder(origin.x()-eye.x,origin.y()-eye.y,origin.z()-eye.z,
            axis.x(),axis.y(),axis.z(),node.corridorHalfWidth(),node.corridorPhase());
        return this;
    }
    @Override public VertexConsumer addVertex(float x,float y,float z){this.x=x;this.y=y;this.z=z;return this;}
    @Override public VertexConsumer setColor(int r,int g,int b,int a){this.r=r/255f;this.g=g/255f;this.b=b/255f;this.a=a/255f;return this;}
    @Override public VertexConsumer setUv(float u,float v){this.u=u;this.v=v;return this;}
    @Override public VertexConsumer setUv1(int u,int v){return this;}
    @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
    @Override public VertexConsumer setUv2(int u,int v){light=(u&65535)|((v&65535)<<16);clip.vertex(x,y,z,this.u,this.v,r,g,b,a);return this;}
    private void quad(float[] a,float[] b,float[] c,float[] d){emit(a);emit(b);emit(c);emit(d);}
    private void emit(float[] p){target.addVertex(p[0],p[1],p[2]).setUv(p[3],p[4]).setColor(p[5],p[6],p[7],p[8]).setLight(light);}
}
