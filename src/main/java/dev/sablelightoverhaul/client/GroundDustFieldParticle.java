package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.GasVertexData;
import dev.sablelightoverhaul.GroundDustFieldModel.Node;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.world.phys.AABB;

/** Soft carriers sorted separately by the existing smoke pass; never write opaque depth. */
final class GroundDustFieldParticle extends TextureSheetParticle {
    private final Node node;
    private final CorridorVertexConsumer corridor=new CorridorVertexConsumer();
    private final int color;
    private final int epoch=ClientFlashes.visualEpoch();
    GroundDustFieldParticle(ClientLevel level,Node node,SpriteSet sprites){
        super(level,0,0,0);this.node=node;hasPhysics=false;lifetime=Integer.MAX_VALUE;pickSprite(sprites);updatePosition();
        int fallback=dev.sablelightoverhaul.ParticleColorAverage.tint(node.patch.color(),0xFFFFFF,.64);
        color=BlockDebrisColor.resolve(level,node.patch.materialState(),node.patch.materialPos(),fallback);
    }
    void updatePosition(){
        var old=node.previous;var now=node.position;
        setPos(now.x(),now.y(),now.z());xo=old.x();yo=old.y();zo=old.z();
        setBoundingBox(new AABB(TankAfterSmokeClient.vec(old),TankAfterSmokeClient.vec(now))
            .minmax(new AABB(TankAfterSmokeClient.vec(node.patch.position()),TankAfterSmokeClient.vec(node.patch.position())))
            .inflate(node.width(1)));
    }
    @Override public void tick(){}
    @Override public ParticleRenderType getRenderType(){return MuzzleParticleLayer.TYPE;}
    @Override public void render(VertexConsumer out,Camera camera,float partial){
        if(epoch!=ClientFlashes.visualEpoch()||!ClientFlashes.effectsEnabled())return;
        boolean debug=MuzzleClientConfig.GROUND_FIELD_DEBUG.get();
        float alpha=debug?.95f:(float)node.alpha(partial);if(alpha<=0)return;
        float w=(float)(node.width(partial)*.5),h=(float)(node.height(partial)*.5),roll=(float)node.roll(partial);
        // Approximate the projected support of a flattened world-space cloud: broad from above,
        // low from the side, instead of an ellipse glued flat to the screen at every elevation.
        var eye=camera.getPosition();double dx=eye.x-x,dy=eye.y-y,dz=eye.z-z;
        double vertical=dy*dy/Math.max(1e-8,dx*dx+dy*dy+dz*dz);
        h=(float)Math.sqrt(h*h*(1-vertical)+w*w*vertical);
        int rgb=color,light=super.getLightColor(partial);
        float r=((rgb>>16)&255)/255f,g=((rgb>>8)&255)/255f,b=(rgb&255)/255f;
        double px=x,py=y,pz=z,ox=xo,oy=yo,oz=zo;
        if(debug){
            var p=node.patch.position();boolean arrived=node.age(partial)>0;
            px=ox=p.x();py=oy=p.y()+.12;pz=oz=p.z();w=h=.22f;roll=0;
            r=.1f;g=arrived?1f:.25f;b=arrived?.1f:1f;light=0x00F000F0;
        }
        if(!debug&&node.corridorHalfWidth()>0)out=corridor.bind(out,camera.getPosition(),node);
        if(MuzzleParticleLayer.proceduralSmoke()){
            if(!debug&&MuzzleClientConfig.GROUND_FIELD_CACHED.get()&&MuzzleParticleLayer.mode()!=MuzzleParticleLayer.Mode.SINGLE)
                MuzzleSmokeMesh.drawCachedPuff(out,camera,px,py,pz,ox,oy,oz,partial,w,h,roll,node.materialSeed,
                    r,g,b,alpha,GasVertexData.smokeLight(light));
            else MuzzleSmokeMesh.drawPuffShaped(out,camera,px,py,pz,ox,oy,oz,partial,w,h,roll,node.materialSeed,
                r,g,b,alpha,GasVertexData.smokeLight(light));
        }else{
            MuzzleSmokeMesh.drawAtlasPuff(out,camera,px,py,pz,ox,oy,oz,partial,w,h,roll,getU0(),getU1(),getV0(),getV1(),
                r,g,b,alpha,MuzzleParticleLayer.markLight(light,false));
        }
    }
}
