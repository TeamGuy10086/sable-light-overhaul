package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.AfterSmokeModel.Node;
import dev.sablelightoverhaul.GasVertexData;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.phys.AABB;

/** One independently sorted soft cloud; its controller owns world-space motion and lifetime. */
final class TankAfterSmokeParticle extends TextureSheetParticle {
    private final Node node;
    TankAfterSmokeParticle(ClientLevel level,Node node,SpriteSet sprites){
        super(level,0,0,0);this.node=node;
        hasPhysics=false;lifetime=Integer.MAX_VALUE;pickSprite(sprites);updatePosition();
    }
    void updatePosition(){
        var old=node.previous;var now=node.position;
        setPos(now.x(),now.y(),now.z());xo=old.x();yo=old.y();zo=old.z();
        // Enclose all three rotated lobes, their offsets, and both interpolation endpoints.
        setBoundingBox(new AABB(TankAfterSmokeClient.vec(old),TankAfterSmokeClient.vec(now))
            .inflate(Math.max(node.diameter(0),node.diameter(1))));
    }
    @Override public void tick(){/* Controller updates positions once per game tick. */}
    @Override public ParticleRenderType getRenderType(){return MuzzleParticleLayer.TYPE;}
    @Override public void render(VertexConsumer out,Camera camera,float partial){
        float alpha=(float)node.alpha(partial);
        if(alpha<=0)return;
        float radius=(float)(node.diameter(partial)*.5),aspect=(float)Math.sqrt(node.aspect(partial));
        float roll=(float)node.roll(partial);
        int light=super.getLightColor(partial);
        if(MuzzleParticleLayer.proceduralSmoke()){
            if(MuzzleClientConfig.TANK_SMOKE_CACHED.get() && MuzzleParticleLayer.mode()!=MuzzleParticleLayer.Mode.SINGLE)
                MuzzleSmokeMesh.drawCachedPuff(out,camera,x,y,z,xo,yo,zo,partial,
                    radius*aspect,radius/aspect,roll,node.materialSeed,.64f,.63f,.61f,alpha,GasVertexData.smokeLight(light));
            else MuzzleSmokeMesh.drawPuffShaped(out,camera,x,y,z,xo,yo,zo,partial,
                radius*aspect,radius/aspect,roll,node.materialSeed,
                .64f,.63f,.61f,alpha,GasVertexData.smokeLight(light));
        }else{
            MuzzleSmokeMesh.drawAtlasPuff(out,camera,x,y,z,xo,yo,zo,partial,
                radius*aspect,radius/aspect,roll,getU0(),getU1(),getV0(),getV1(),
                .64f,.63f,.61f,alpha,MuzzleParticleLayer.markLight(light,false));
        }
    }
}
