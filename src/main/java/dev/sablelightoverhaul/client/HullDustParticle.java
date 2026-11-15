package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.*;
import dev.sablelightoverhaul.AfterSmokeModel.Point;
import dev.sablelightoverhaul.lighting.GroundDustParticleData;
import java.util.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.world.phys.Vec3;

/** Bounded detached surface puffs sharing the accepted sorted soft-smoke material. */
final class HullDustParticle extends TextureSheetParticle {
    private static SpriteSet sprites;
    private static final Set<HullDustParticle> active=Collections.newSetFromMap(new WeakHashMap<>());
    private final HullDustModel model;
    private final HullSurfaceFlow flow;
    private final HullSurfaceAnchor anchor;
    private HullSurfaceAnchor.Skin frozenSkin;
    private Point releasedAt;
    private Vec3 releasedVisualOffset;
    private Vec3 shapeVelocity=Vec3.ZERO,previousShapeVelocity=Vec3.ZERO;
    private final int epoch=ClientFlashes.visualEpoch(),seed;
    private final int color;
    static void sprites(SpriteSet value){sprites=value;}
    static void beginBatch(ClientLevel level){active.removeIf(p->!p.isAlive()||p.level!=level||p.epoch!=ClientFlashes.visualEpoch());}
    static void spawn(ClientLevel level,Vec3 at,Vec3 normal,Vec3 direction,CannonDustPayload event,int index,int origins,GroundDustParticleData data,CannonDustPayload.Surface source){
        if(sprites==null||active.size()>=512||!ClientFlashes.effectsEnabled())return;
        long seed=event.seed()+index*0x9E3779B97F4A7C15L;
        if(RetiredHullSettings.ENABLED && source.body()!=null){
            float exposed=(float)CannonDustMotion.surfaceExposure(source.position(),normal,event.muzzle());
            data=data.with(data.size(),data.lifetime(),data.stretch(),data.damping(),data.gravity(),data.alpha()*exposed,data.fade());
        }
        var sample=HullDustEmission.sample(index,origins,event.seed(),!RetiredHullSettings.ENABLED || source.body()==null?normal:source.localNormal(),event.hullFlow().spread(),event.hullFlow().stagger());
        if(!RetiredHullSettings.ENABLED || source.body()==null)at=at.add(sample.offset());
        // Disabled even for legacy payloads containing a body UUID.
        HullSurfaceAnchor anchor=RetiredHullSettings.ENABLED&&source.body()!=null&&net.neoforged.fml.ModList.get().isLoaded("sable")
            ?new HullSurfaceAnchor(level,source,direction,event.hullFlow().clearance()):null;
        HullSurfaceFlow flow=anchor==null?null:new HullSurfaceFlow(anchor,point(source.local().add(sample.offset())),point(source.localNormal()),point(at),event.seed(),
            data.size(),data.lifetime()*.05,event.burstSpeed()*20,data.damping(),data.gravity(),data.alpha(),Math.max(.02,Math.min(1,data.fade())),event.hullFlow(),sample.delay());
        var model=anchor==null?new HullDustModel(point(at),point(normal),point(direction),point(event.muzzle()),event.seed(),
            data.size(),data.lifetime()*.05,event.burstSpeed()*20,data.damping(),data.gravity(),data.alpha(),Math.max(.02,Math.min(1,data.fade())),event.hullFlow(),sample.delay()):null;
        var puff=new HullDustParticle(level,model,flow,anchor,(int)(seed&63),data.color());active.add(puff);
        Minecraft.getInstance().particleEngine.add(puff);
    }
    private static Point point(Vec3 p){return new Point(p.x,p.y,p.z);}
    private HullDustParticle(ClientLevel level,HullDustModel model,HullSurfaceFlow flow,HullSurfaceAnchor anchor,int seed,int color){
        super(level,flow!=null?flow.position.x():model.position.x(),flow!=null?flow.position.y():model.position.y(),flow!=null?flow.position.z():model.position.z());
        this.model=model;this.flow=flow;this.anchor=anchor;this.seed=seed;this.color=color;
        hasPhysics=false;lifetime=1264;pickSprite(sprites);
    }
    @Override public void tick(){
        if(epoch!=ClientFlashes.visualEpoch()||!ClientFlashes.effectsEnabled()){remove();return;}
        Point old,now;double width;
        if(flow!=null){
            boolean attached=flow.attached();flow.tick();if(flow.finished()){remove();return;}
            if(attached&&!flow.attached()){
                frozenSkin=anchor.skin(flow.local(),flow.normal(),flow.width(1)*.5,1);releasedAt=flow.position;
                Point n=flow.contact().worldNormal();
                releasedVisualOffset=new Vec3(n.x(),n.y(),n.z()).scale(HullDustProjection.supportLift(flow.width(1)*.5,MuzzleClientConfig.HULL_CLUSTER_THICKNESS.get()));
            }
            old=flow.previous;now=flow.position;width=flow.width(1);
        }else{model.tick(ParticleWind.step());if(model.finished()){remove();return;}old=model.previous;now=model.position;width=model.width(1);}
        previousShapeVelocity=shapeVelocity;
        var movement=now.subtract(old).scale(20);
        shapeVelocity=shapeVelocity.lerp(CannonDustMotion.cap(new Vec3(movement.x(),movement.y(),movement.z()),32),.25);
        setPos(now.x(),now.y(),now.z());xo=old.x();yo=old.y();zo=old.z();
        setBoundingBox(new net.minecraft.world.phys.AABB(x,y,z,x,y,z).inflate(width));
    }
    @Override public ParticleRenderType getRenderType(){return MuzzleParticleLayer.TYPE;}
    @Override public void render(VertexConsumer out,Camera camera,float partial){
        if(!HullDustDebug.capture()){renderDust(out,camera,partial);return;}
        var capture=new HullDustDebug.Capture(out,camera.getPosition());renderDust(capture,camera,partial);
        HullDustDebug.record(this,anchor==null?null:anchor.diagnostic(),new Vec3(x,y,z),flow!=null&&flow.attached(),capture.vertices);
    }
    private void renderDust(VertexConsumer out,Camera camera,float partial){
        if(epoch!=ClientFlashes.visualEpoch()||!ClientFlashes.effectsEnabled())return;
        float alpha=(float)(flow!=null?flow.alpha(partial):model.alpha(partial));if(alpha<=0)return;
        float w=(float)((flow!=null?flow.width(partial):model.width(partial))*.5),h=(float)((flow!=null?flow.height(partial):model.height(partial))*.5),roll=0;
        float r=((color>>16)&255)/255f,g=((color>>8)&255)/255f,b=(color&255)/255f;
        int light=super.getLightColor(partial);
        if(flow!=null&&HullDustDebug.renderer()==MuzzleClientConfig.HullRenderer.SURFACE_SKIN){
            float blend=(float)flow.blend(partial);
            var skin=flow.attached()?anchor.skin(flow.local(),flow.normal(),w,partial):frozenSkin;
            if(skin!=null&&blend<1){
                if(!flow.attached()&&releasedAt!=null){var at=flow.previous.lerp(flow.position,partial);skin=skin.shift(new Vec3(at.x()-releasedAt.x(),at.y()-releasedAt.y(),at.z()-releasedAt.z()));}
                MuzzleSmokeMesh.drawSurfaceSkin(out,camera,skin,seed,r,g,b,alpha*(1-blend),MuzzleParticleLayer.proceduralSmoke()?GasVertexData.smokeLight(light):MuzzleParticleLayer.markLight(light,false),MuzzleParticleLayer.proceduralSmoke(),getU0(),getU1(),getV0(),getV1(),MuzzleClientConfig.HULL_SKIN_THICKNESS.get().floatValue());
            }
            alpha*=blend;if(alpha<=0)return;
        }
        double px=x,py=y,pz=z,ox=xo,oy=yo,oz=zo;
        var cr=new org.joml.Vector3f(1,0,0).rotate(camera.rotation());var cu=new org.joml.Vector3f(0,1,0).rotate(camera.rotation());
        Vec3 right=new Vec3(cr.x,cr.y,cr.z),up=new Vec3(cu.x,cu.y,cu.z);
        if(flow!=null&&flow.contact()!=null&&HullDustDebug.renderer()==MuzzleClientConfig.HullRenderer.SOFT_CLUSTER){
            var n=flow.contact().worldNormal();Vec3 normal=new Vec3(n.x(),n.y(),n.z());
            if(flow.attached())normal=anchor.renderNormal(n,partial);
            var shape=HullDustProjection.project(normal,right,up,w,MuzzleClientConfig.HULL_CLUSTER_THICKNESS.get(),h,flow.blend(partial));
            w=shape.halfWidth();h=shape.halfHeight();
            Vec3 offset=flow.attached()||releasedVisualOffset==null?normal.scale(shape.lift()):releasedVisualOffset;
            px+=offset.x;py+=offset.y;pz+=offset.z;ox+=offset.x;oy+=offset.y;oz+=offset.z;
        }
        var deformed=HullDustDeformation.apply(w,h,previousShapeVelocity.lerp(shapeVelocity,partial),right,up);
        w=deformed.width();h=deformed.height();
        if(flow!=null&&HullDustDebug.renderer()==MuzzleClientConfig.HullRenderer.SOFT_CLUSTER){
            boolean procedural=MuzzleParticleLayer.proceduralSmoke();
            MuzzleSmokeMesh.drawHullCluster(out,camera,px,py,pz,ox,oy,oz,partial,w,h,roll,seed,r,g,b,alpha,
                procedural?GasVertexData.smokeLight(light):MuzzleParticleLayer.markLight(light,false),procedural,getU0(),getU1(),getV0(),getV1());
            return;
        }
        if(MuzzleParticleLayer.proceduralSmoke())MuzzleSmokeMesh.drawPuffShaped(out,camera,px,py,pz,ox,oy,oz,partial,w,h,roll,seed,r,g,b,alpha,GasVertexData.smokeLight(light));
        else MuzzleSmokeMesh.drawAtlasPuff(out,camera,px,py,pz,ox,oy,oz,partial,w,h,roll,getU0(),getU1(),getV0(),getV1(),r,g,b,alpha,MuzzleParticleLayer.markLight(light,false));
    }
}
