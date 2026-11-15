package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.*;
import dev.sablelightoverhaul.AfterSmokeModel.Point;
import java.util.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Optional Sable query bridge; all geometry stays in plot space until the boundary transform. */
final class HullSurfaceAnchor implements HullSurfaceFlow.Surface {
    record Skin(Vec3 center,Vec3 right,Vec3 up,float halfU,float halfV,float minU,float maxU,float minV,float maxV){
        Skin shift(Vec3 delta){return new Skin(center.add(delta),right,up,halfU,halfV,minU,maxU,minV,maxV);}
    }
    static boolean reusableHit(BlockHitResult hit,VoxelShape previous,VoxelShape current,BlockPos support,Vec3 local){
        return hit!=null&&previous==current&&!current.isEmpty()&&hit.getBlockPos().equals(support)&&hit.getLocation().distanceToSqr(local)<1e-12;
    }
    private final Bridge bridge;
    HullSurfaceAnchor(ClientLevel level,CannonDustPayload.Surface source,Vec3 wind,double clearance){bridge=new Bridge(level,source,wind,clearance);}
    @Override public boolean valid(){return bridge.valid();}
    @Override public boolean available(){return !bridge.unavailable;}
    @Override public HullSurfaceFlow.Contact sample(Point local,Point normal,double step){return bridge.sample(vec(local),vec(normal),step);}
    @Override public Point sweep(Point from,Point to){return point(bridge.sweep(vec(from),vec(to)));}
    @Override public Point slideVelocity(Point from,Point to){
        return point(bridge.body.logicalPose().transformNormal(vec(to.subtract(from)))).scale(1/HullSurfaceFlow.STEP);
    }
    @Override public double supportRadius(Point local,Point normal,double requested){return bridge.supportRadius(vec(local),vec(normal),requested);}
    Skin skin(Point local,Point normal,double halfSize,float partial){return bridge.skin(vec(local),vec(normal),halfSize,partial);}
    Vec3 renderNormal(Point fallback,float partial){return bridge.renderNormal(vec(fallback),partial);}
    Diagnostic diagnostic(){return bridge.diagnostic;}
    record Diagnostic(Vec3 collision,Vec3 visual,Vec3 normal,HullVisualSurfaces.Status status){}
    private static Vec3 vec(Point p){return new Vec3(p.x(),p.y(),p.z());}
    private static Point point(Vec3 p){return new Point(p.x,p.y,p.z);}
    private static final class Bridge {
        private record Shape(VoxelShape shape,List<AABB> boxes){}
        private static final Shape EMPTY=new Shape(Shapes.empty(),List.of());
        private static java.lang.ref.WeakReference<ClientLevel> cachedWorld=new java.lang.ref.WeakReference<>(null);
        private static long cachedTick=Long.MIN_VALUE;
        private static int remaining;
        private static final Map<BlockPos,Shape> shapes=new HashMap<>();
        private record PatchKey(UUID body,BlockPos block,int axis,long plane){}
        private static final Map<PatchKey,List<HullDustFootprint.Rect>> patches=new HashMap<>();
        private final ClientLevel level;
        private final UUID id;
        private final Vec3 wind,sourceLocal;
        private final double clearance;
        private dev.ryanhcode.sable.sublevel.SubLevel body;
        private boolean unavailable;
        private AABB bounds;
        private BlockHitResult lastHit;
        private VoxelShape lastShape;
        private boolean firstSample=true;
        private Vec3 visualLocalNormal,visualOffset=Vec3.ZERO;
        private BlockPos visualBlock;
        private Diagnostic diagnostic;
        private Vec3 fitCenter,fitNormal,fitVisualNormal;
        private double fitRadius;
        private long fitTick;
        private VoxelShape fitShape;
        private BlockPos fitBlock;
        private Vec3 renderNormal(Vec3 fallback,float partial){
            if(body==null||body.isRemoved()||visualLocalNormal==null)return fallback;
            var pose=new dev.ryanhcode.sable.companion.math.Pose3d();body.lastPose().lerp(body.logicalPose(),partial,pose);
            return pose.transformNormal(visualLocalNormal).normalize();
        }
        Bridge(ClientLevel level,CannonDustPayload.Surface source,Vec3 wind,double clearance){this.level=level;id=source.body();sourceLocal=source.local();this.wind=wind;this.clearance=clearance;}
        boolean valid(){
            unavailable=false;
            var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            body=container==null?null:container.getSubLevel(id);
            if(body==null||body.isRemoved())return false;
            Vec3 current=body.logicalPose().transformPosition(sourceLocal),previous=body.lastPose().transformPosition(sourceLocal);
            return point(current).finite()&&point(previous).finite()&&current.distanceToSqr(previous)<=64;
        }
        private Shape shape(BlockPos pos){
            beginTick();
            if(!body.getPlot().contains(new net.minecraft.world.level.ChunkPos(pos))||!level.hasChunkAt(pos))return EMPTY;
            Shape value=shapes.get(pos);if(value!=null)return value;
            var actual=dev.sablelightoverhaul.lighting.HullDustCollision.shape(level,pos);
            value=new Shape(actual,actual.toAabbs());if(shapes.size()<4096)shapes.put(pos.immutable(),value);return value;
        }
        private void beginTick(){
            if(cachedWorld.get()!=level||cachedTick!=level.getGameTime()){
                if(cachedWorld.get()==level&&cachedTick%100==0&&dev.sablelightoverhaul.lighting.LightingCommon.on(dev.sablelightoverhaul.lighting.LightingCommon.GEN_DEBUG))
                    com.mojang.logging.LogUtils.getLogger().info("SLO hull contact: cachedShapes={} remainingRayBudget={}",shapes.size(),remaining);
                cachedWorld=new java.lang.ref.WeakReference<>(level);cachedTick=level.getGameTime();remaining=MuzzleClientConfig.HULL_QUERY_BUDGET.get();shapes.clear();patches.clear();
            }
        }
        double supportRadius(Vec3 local,Vec3 normal,double requested){
            beginTick();
            Vec3 u=Math.abs(normal.x)>.5?new Vec3(0,0,1):new Vec3(1,0,0),v=normal.cross(u);
            var pose=body.logicalPose();
            double unit=Math.min(pose.transformNormal(u).length(),pose.transformNormal(v).length());
            if(unit<1e-6)return 0;
            Vec3 center=local.add(visualOffset);var support=BlockPos.containing(local.subtract(normal.scale(.01)));
            boolean reusable=fitCenter!=null&&normal.equals(fitNormal)&&Objects.equals(visualLocalNormal,fitVisualNormal)&&support.equals(fitBlock)&&lastShape==fitShape;
            double movement=reusable?center.subtract(fitCenter).length():Double.POSITIVE_INFINITY;
            double remainingFit=reusable?Math.max(0,fitRadius-movement):Double.NaN;
            if(reusable&&movement<.25&&level.getGameTime()-fitTick<4)return remainingFit*unit;
            double plane=local.dot(normal);
            int axis=(int)((normal.x+1)*9+(normal.y+1)*3+normal.z+1);
            var key=new PatchKey(id,support,axis,Double.doubleToLongBits(plane));
            var faces=patches.get(key);
            if(faces==null){
                // A bounded 7x7 plane patch, shared by sibling nodes in this block/plane this tick.
                // Charge the existing query budget before any work; never accept partial support.
                if(remaining<49)return remainingFit*unit;
                remaining-=49;var found=new ArrayList<HullDustFootprint.Rect>();
                for(int a=-3;a<=3;a++)for(int b=-3;b<=3;b++){
                    var pos=support.offset((int)(u.x*a+v.x*b),(int)(u.y*a+v.y*b),(int)(u.z*a+v.z*b));
                    for(var box:shape(pos).boxes){
                        var world=box.move(pos);
                        if(Math.abs(max(world,normal)-plane)>.025)continue;
                        if(found.size()<128)found.add(new HullDustFootprint.Rect(min(world,u),min(world,v),max(world,u),max(world,v)));
                    }
                }
                faces=List.copyOf(found);if(patches.size()<128)patches.put(key,faces);
            }
            // The visual face may be tilted/offset from collision geometry. Include its outward
            // display offset in the allowance, rather than assuming the collision centre is enough.
            Vec3 visualN=visualLocalNormal==null?normal:visualLocalNormal;
            double tilt=visualN.subtract(normal.scale(visualN.dot(normal))).length();
            double localRequest=requested/unit;
            double margin=tilt*(clearance/unit+HullDustProjection.supportLift(localRequest,MuzzleClientConfig.HULL_CLUSTER_THICKNESS.get()));
            fitRadius=Math.max(0,HullDustFootprint.radius(faces,center.dot(u),center.dot(v),Math.min(3,localRequest+margin))-margin);
            fitCenter=center;fitNormal=normal;fitVisualNormal=visualLocalNormal;fitTick=level.getGameTime();fitShape=lastShape;fitBlock=support;
            return fitRadius*unit;
        }
        private BlockHitResult ray(Vec3 from,Vec3 to){
            beginTick();
            if(remaining<=0){unavailable=true;return null;}remaining--;
            if(from.distanceToSqr(to)>16){unavailable=true;return null;}
            return net.minecraft.world.level.BlockGetter.traverseBlocks(from,to,null,(unused,pos)->{
                Shape data=shape(pos);return data==null||data.shape.isEmpty()?null:data.shape.clip(from,to,pos);
            },unused->null);
        }
        private Vec3 tangent(Vec3 normal){
            Vec3 local=body.logicalPose().transformNormalInverse(wind);
            local=local.subtract(normal.scale(local.dot(normal)));
            if(local.lengthSqr()<1e-8)local=normal.cross(Math.abs(normal.y)>.5?new Vec3(1,0,0):new Vec3(0,1,0));
            return local.normalize();
        }
        private HullSurfaceFlow.Contact at(Vec3 local,Vec3 normal,Vec3 flow){
            // A ray may start in unallocated air beside the hull. Only missing support data stalls it.
            var support=BlockPos.containing(local.subtract(normal.scale(.01)));
            if(!body.getPlot().contains(new net.minecraft.world.level.ChunkPos(support)))return null;
            if(!level.hasChunkAt(support)){unavailable=true;return null;}
            var currentShape=shape(support).shape;
            var hit=reusableHit(lastHit,lastShape,currentShape,support,local)?lastHit:ray(local.add(normal.scale(.30)),local.subtract(normal.scale(.35)));
            if(hit==null||hit.isInside())return null;
            Vec3 n=Vec3.atLowerCornerOf(hit.getDirection().getNormal());if(n.dot(normal)<.7)return null;
            Vec3 on=hit.getLocation();var data=shape(hit.getBlockPos());if(data==null)return null;
            // Keep the skin inside the actual collision box at the hit, not the whole vessel bounds.
            AABB chosen=null;Vec3 inner=on.subtract(n.scale(.003)).subtract(Vec3.atLowerCornerOf(hit.getBlockPos()));
            for(var box:data.boxes)if(box.inflate(.004).contains(inner)){chosen=box.move(hit.getBlockPos());break;}
            if(chosen==null)return null;
            bounds=chosen;lastHit=hit;lastShape=data.shape;var pose=body.logicalPose();
            boolean correct=HullDustDebug.renderer()==MuzzleClientConfig.HullRenderer.SOFT_CLUSTER;
            var visual=correct?HullVisualSurfaces.resolve(level,hit.getBlockPos(),on,n):new HullVisualSurfaces.Result(on,n,HullVisualSurfaces.Status.DISABLED);
            if(visual.status()==HullVisualSurfaces.Status.BUDGET&&hit.getBlockPos().equals(visualBlock)&&visualLocalNormal!=null)
                visual=new HullVisualSurfaces.Result(on.add(visualOffset),visualLocalNormal,visual.status());
            visualBlock=hit.getBlockPos();visualOffset=visual.point().subtract(on);visualLocalNormal=visual.normal();
            Vec3 world=pose.transformPosition(visual.point()),previous=body.lastPose().transformPosition(visual.point());
            Vec3 worldNormal=pose.transformNormal(visual.normal()).normalize(),worldFlow=pose.transformNormal(flow).normalize();
            worldFlow=worldFlow.subtract(worldNormal.scale(worldFlow.dot(worldNormal)));
            diagnostic=new Diagnostic(pose.transformPosition(on),world,worldNormal,visual.status());
            if(world.distanceToSqr(previous)>64){unavailable=true;return null;}
            Vec3 velocity=world.subtract(previous).scale(20);
            if(velocity.lengthSqr()>1024)velocity=velocity.normalize().scale(32);
            return new HullSurfaceFlow.Contact(point(on),point(n),point(world),point(worldNormal),point(worldFlow),point(velocity));
        }
        HullSurfaceFlow.Contact sample(Vec3 local,Vec3 normal,double step){
            if(body==null||body.isRemoved())return null;
            Vec3 flow=tangent(normal);
            if(step<=0){
                var result=at(local,normal,flow);
                if(firstSample&&!unavailable){
                    if(result==null&&local.distanceToSqr(sourceLocal)>1e-8)result=at(sourceLocal.lerp(local,.5),normal,flow);
                    if(!unavailable)firstSample=false;
                }
                return result;
            }
            Vec3 delta=flow.scale(step),start=local.add(normal.scale(clearance));
            var wall=ray(start,start.add(delta));if(unavailable)return null;
            if(wall==null)return at(local.add(delta),normal,flow);
            for(int axis=0;axis<3;axis++){
                double v=axis==0?delta.x:axis==1?delta.y:delta.z;if(Math.abs(v)<1e-5)continue;
                Vec3 slide=axis==0?new Vec3(v,0,0):axis==1?new Vec3(0,v,0):new Vec3(0,0,v);
                if(ray(start,start.add(slide))==null&&!unavailable){var contact=at(local.add(slide),normal,slide.normalize());if(contact!=null)return contact;}
                if(unavailable)return null;
            }
            return at(local,normal,Vec3.ZERO);
        }
        Vec3 sweep(Vec3 from,Vec3 to){
            if(!valid())return to;
            // Reuse Sable's existing world bounds before touching detailed block shapes.
            if(!body.boundingBox().intersects(Math.min(from.x,to.x)-.05,Math.min(from.y,to.y)-.05,Math.min(from.z,to.z)-.05,
                    Math.max(from.x,to.x)+.05,Math.max(from.y,to.y)+.05,Math.max(from.z,to.z)+.05))return to;
            var pose=body.logicalPose();var hit=ray(pose.transformPositionInverse(from),pose.transformPositionInverse(to));
            if(hit==null||unavailable)return to;
            Vec3 n=Vec3.atLowerCornerOf(hit.getDirection().getNormal());return pose.transformPosition(hit.getLocation().add(n.scale(.025)));
        }
        Skin skin(Vec3 local,Vec3 normal,double half,float partial){
            if(body==null||body.isRemoved()||bounds==null||half<=0)return null;
            Vec3 u=Math.abs(normal.x)>.5?new Vec3(0,0,1):new Vec3(1,0,0),v=normal.cross(u);
            double lowU=Math.max(-half,min(bounds,u)-local.dot(u)),highU=Math.min(half,max(bounds,u)-local.dot(u));
            double lowV=Math.max(-half,min(bounds,v)-local.dot(v)),highV=Math.min(half,max(bounds,v)-local.dot(v));
            if(lowU>=highU||lowV>=highV)return null;
            var pose=new dev.ryanhcode.sable.companion.math.Pose3d();body.lastPose().lerp(body.logicalPose(),partial,pose);
            Vec3 center=local.add(u.scale((lowU+highU)*.5)).add(v.scale((lowV+highV)*.5)).add(normal.scale(clearance));
            Vec3 world=pose.transformPosition(center),du=pose.transformPosition(center.add(u)).subtract(world),dv=pose.transformPosition(center.add(v)).subtract(world);
            Vec3 right=du.normalize(),up=dv.normalize();
            return new Skin(world,right,up,(float)((highU-lowU)*.5*du.length()),(float)((highV-lowV)*.5*dv.length()),(float)(lowU/half),(float)(highU/half),(float)(lowV/half),(float)(highV/half));
        }
        private static double min(AABB b,Vec3 n){return (n.x>=0?b.minX:b.maxX)*n.x+(n.y>=0?b.minY:b.maxY)*n.y+(n.z>=0?b.minZ:b.maxZ)*n.z;}
        private static double max(AABB b,Vec3 n){return (n.x>=0?b.maxX:b.minX)*n.x+(n.y>=0?b.maxY:b.minY)*n.y+(n.z>=0?b.maxZ:b.minZ)*n.z;}
    }
}
