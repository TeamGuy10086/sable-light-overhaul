package dev.sablelightoverhaul.client;

import java.util.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.sablelightoverhaul.*;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Prototype continuous smoke ribbons. Uses the established smoke material/depth contract without ParticleEngine objects. */
@EventBusSubscriber(modid=SableLightOverhaul.ID,value=Dist.CLIENT)
public final class MissileSmokeMesh {
    private static final int MAX_TRACKS=128,MAX_TOTAL_NODES=8192;
    private static final LinkedHashMap<Object,Track> tracks=new LinkedHashMap<>();
    private static ClientLevel world;
    private static int epoch;
    private static Vec3 wind=Vec3.ZERO;
    private static double clock;
    public static double width=.45,expansion=.6,lifetime=160,alpha=1;
    private static int drawnSegments,drawnQuads;
    private static Demo demo;
    private static final class Track {final MissileSmokePath path=new MissileSmokePath();int light=LightTexture.pack(0,15),lod=4;final double seed;Track(Object id){seed=(id.hashCode()&65535)*.017;}}
    private record Ring(Vec3 center,Vec3 side,Vec3 view,double radius,double alpha,double uv,int strip){}
    private record Segment(Ring a,Ring b,int light,int lanes,double depth){}
    private record Demo(Object id,Vec3 start,Vec3 forward,Vec3 side,double startTime,boolean curve){}
    private MissileSmokeMesh(){}
    public static boolean available(ClientLevel level){return level!=null&&level==Minecraft.getInstance().level&&ClientFlashes.effectsEnabled()
        &&MuzzleParticleLayer.ribbonReady();}
    private static void world(ClientLevel level){
        if(world!=level||epoch!=ClientFlashes.visualEpoch()){
            tracks.clear();demo=null;world=level;epoch=ClientFlashes.visualEpoch();clock=0;wind=Vec3.ZERO;
        }
    }
    public static boolean sample(ClientLevel level,Object id,long sequence,Vec3 pos,boolean emitting){
        if(!available(level)||id==null||!MissileSmokePath.finite(pos))return false;
        world(level);Track track=tracks.get(id);
        if(track==null){
            // Stable admission: a flood of new IDs must not evict every live trail on every update.
            // Expiration releases slots. Rejection still owns the effect, avoiding fallback particle floods.
            if(!emitting||tracks.size()>=MAX_TRACKS)return true;
            track=new Track(id);tracks.put(id,track);
        }
        track.path.sample(pos.subtract(wind),sequence,clock,emitting);
        BlockPos block=BlockPos.containing(pos);
        if(level.hasChunkAt(block))track.light=LevelRenderer.getLightColor(level,block);
        trim();return true;
    }
    private static void trim(){
        int count=tracks.values().stream().mapToInt(t->t.path.nodes.size()).sum();
        while(count>MAX_TOTAL_NODES){
            Track oldest=null;double age=Double.POSITIVE_INFINITY;
            for(Track t:tracks.values())if(!t.path.nodes.isEmpty()&&t.path.nodes.getFirst().born()<age){oldest=t;age=t.path.nodes.getFirst().born();}
            if(oldest==null)break;oldest.path.nodes.removeFirst();count--;
        }
    }
    public static void stop(ClientLevel level,Object id){if(world==level){Track t=tracks.get(id);if(t!=null)t.path.stop();}}
    public static void clear(){tracks.clear();demo=null;drawnSegments=drawnQuads=0;}
    public static String stats(){return "SLO mesh smoke: tracks="+tracks.size()+" nodes="+tracks.values().stream().mapToInt(t->t.path.nodes.size()).sum()
        +" segments="+drawnSegments+" quads="+drawnQuads+" depthWrite=false; width="+width+" expansion="+expansion+" lifetime="+(lifetime/20)+"s alpha="+alpha;}
    public static boolean demo(boolean curve){
        var mc=Minecraft.getInstance();if(!available(mc.level)||mc.player==null)return false;
        world(mc.level);Vec3 forward=mc.player.getLookAngle().normalize();Vec3 side=forward.cross(new Vec3(0,1,0));
        if(side.lengthSqr()<1e-6)side=new Vec3(1,0,0);else side=side.normalize();
        demo=new Demo(UUID.randomUUID(),mc.player.getEyePosition().add(forward.scale(12)),forward,side,clock,curve);return true;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();world(mc.level);if(mc.level==null||mc.isPaused())return;
        if(!ClientFlashes.effectsEnabled()){clear();return;}
        clock++;var step=ParticleWind.step();wind=wind.add(step.x(),0,step.z());
        if(demo!=null){
            double t=(clock-demo.startTime())/20;
            if(t>4){stop(mc.level,demo.id());demo=null;}
            else {double angle=t*.65;Vec3 p=demo.curve()?demo.start().add(demo.forward().scale(22*Math.sin(angle))).add(demo.side().scale(22*(1-Math.cos(angle))))
                :demo.start().add(demo.forward().scale(t*16));sample(mc.level,demo.id(),(long)clock,p,true);}
        }
        tracks.entrySet().removeIf(entry->{
            Track track=entry.getValue();
            track.path.expire(clock, entry.getKey() instanceof BurningMissileFragment.Trail ? 64 : lifetime);
            return track.path.nodes.isEmpty();
        });
    }
    /** Called only in the translucent ParticleEngine pass, before its GL cleanup. No extra fullscreen passes. */
    public static void render(Camera camera,float partial,Frustum frustum){
        var mc=Minecraft.getInstance();world(mc.level);drawnSegments=drawnQuads=0;
        if(!available(mc.level)||tracks.isEmpty())return;
        double now=clock+partial;Vec3 eye=camera.getPosition();var step=ParticleWind.step();
        Vec3 drift=wind.add(step.x()*partial,0,step.z()*partial);
        List<Segment> segments=new ArrayList<>();
        for(var entry:tracks.entrySet()){
            Track track=entry.getValue();
            var wreck = entry.getKey() instanceof BurningMissileFragment.Trail trail ? trail : null;
            double initialWidth = wreck == null ? width : wreck.width;
            double growth = wreck == null ? expansion : 1.75;
            double smokeLife = wreck == null ? lifetime : 64;
            double lift = wreck == null ? .06 : 1.65;
            var nodes=new ArrayList<>(track.path.nodes);
            if(track.path.emitting()){
                var endpoint = wreck != null ? wreck.endpoint(partial)
                    : dev.sablelightoverhaul.api.ClientMissileTrailApi.frameEndpoint(mc.level,entry.getKey(),partial);
                if(endpoint!=null&&endpoint.emitting()){
                    // The fresh nozzle is pinned to the rendered model; old smoke alone receives wind.
                    var frame=track.path.frameNodes(endpoint.sequence(),endpoint.displayTimeTicks(),
                            endpoint.nozzle()==null?null:endpoint.nozzle().subtract(drift),now);
                    nodes=new ArrayList<>(frame.nodes());
                }
            }
            if(nodes.size()<2)continue;
            double pixels=0;
            for(var n:nodes) {
                double radius=MissileSmokePath.radius(Math.max(0,now-n.born()),initialWidth,growth);
                double distance=Math.max(1,eye.distanceTo(n.position().add(drift)));
                pixels=Math.max(pixels,radius*mc.getWindow().getHeight()/(distance*Math.tan(Math.toRadians(mc.options.fov().get()*.5))));
            }
            // Decide once per trail per frame, with hysteresis; do not oscillate as segments are traversed.
            if(track.lod==4&&pixels<14)track.lod=2;else if(track.lod==2&&pixels>20)track.lod=4;
            else if(track.lod==2&&pixels<3)track.lod=1;else if(track.lod==1&&pixels>5)track.lod=2;
            Ring previous=null;Vec3 previousSide=null;
            for(int i=0;i<nodes.size();i++){
                var node=nodes.get(i);double age=Math.max(0,now-node.born());
                double radius=MissileSmokePath.radius(age,initialWidth,growth);
                double phase=node.station()*.63+track.seed;
                radius*=1+.12*Math.sin(phase)+.07*Math.sin(phase*2.71);
                Vec3 center=node.position().add(drift).add(Math.sin(phase)*.10*age/20,lift*age/20,Math.cos(phase*.79)*.10*age/20);
                Vec3 before=i>0&&nodes.get(i-1).strip()==node.strip()?nodes.get(i-1).position():node.position();
                Vec3 after=i+1<nodes.size()&&nodes.get(i+1).strip()==node.strip()?nodes.get(i+1).position():node.position();
                Vec3 tangent=after.subtract(before).normalize(),view=eye.subtract(center).normalize();
                Vec3 side=tangent.cross(view);
                if(side.lengthSqr()<1e-8)side=previousSide==null?new Vec3(camera.getLeftVector()):previousSide;else side=side.normalize();
                if(previousSide!=null&&side.dot(previousSide)<0)side=side.scale(-1);previousSide=side;
                double opacity=MissileSmokePath.opacity(age,smokeLife)*alpha;
                if(wreck!=null)opacity=Math.min(.96,opacity*1.70);
                // Fade exposed ends rather than leaving square caps on stopping/budget eviction.
                if(i==0||i==nodes.size()-1||(i>0&&nodes.get(i-1).strip()!=node.strip())||(i+1<nodes.size()&&nodes.get(i+1).strip()!=node.strip()))
                    opacity *= wreck == null ? 0 : .32;
                Ring ring=new Ring(center,side,view,radius,opacity,node.station()*.17+track.seed,node.strip());
                if(previous!=null&&previous.strip()==ring.strip()&&previous.center().distanceToSqr(center)>1e-8){
                    AABB box=new AABB(previous.center(),center).inflate(Math.max(previous.radius(),radius));
                    if((frustum==null||frustum.isVisible(box))&&(previous.alpha()+opacity)>.005){
                        double depth=center.subtract(eye).dot(new Vec3(camera.getLookVector()));
                        segments.add(new Segment(previous,ring,track.light,track.lod,depth));
                    }
                }
                previous=ring;
            }
        }
        if(segments.isEmpty())return;
        segments.sort(Comparator.comparingDouble(Segment::depth).reversed());
        RenderSystem.setShader(GameRenderer::getParticleShader);
        BufferBuilder out=MuzzleParticleLayer.TYPE.begin(Tesselator.getInstance(),mc.getTextureManager());
        RenderSystem.depthMask(false); // Deliberate even when the old smoke-debug DEPTH mode is selected.
        boolean cull=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        RenderSystem.disableCull();
        try {
        for(Segment segment:segments){
            for(int j=0;j<segment.lanes();j++){
                double lo=-1+2.0*j/segment.lanes(),hi=-1+2.0*(j+1)/segment.lanes();
                vertex(out,segment.a(),lo,eye,segment.light());vertex(out,segment.b(),lo,eye,segment.light());
                vertex(out,segment.b(),hi,eye,segment.light());vertex(out,segment.a(),hi,eye,segment.light());drawnQuads++;
            }
            drawnSegments++;
        }
        MeshData mesh=out.build();if(mesh!=null)BufferUploader.drawWithShader(mesh);
        } finally {if(cull)RenderSystem.enableCull();}
    }
    private static void vertex(VertexConsumer out,Ring ring,double across,Vec3 eye,int light){
        Vec3 p=ring.center().add(ring.side().scale(across*ring.radius()))
            .add(ring.view().scale(.22*ring.radius()*(1-across*across))).subtract(eye);
        float shade=(float)(.58+.12*(1-across*across));
        out.addVertex((float)p.x,(float)p.y,(float)p.z).setUv((float)ring.uv(),(float)across)
            .setColor(shade,shade,shade,(float)ring.alpha()).setLight(GasVertexData.ribbonLight(light));
    }
}
