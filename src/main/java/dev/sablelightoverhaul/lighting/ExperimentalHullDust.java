package dev.sablelightoverhaul.lighting;

import dev.sablelightoverhaul.CannonDustPayload;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Cached exposed collision faces, then a fresh body pose per shot. No duplicate physics body. */
public final class ExperimentalHullDust {
    private record Sample(Vec3 local,Direction face){}
    private record Snapshot(long tick,List<Sample> faces){}
    private static final Map<ServerSubLevel,Snapshot> CACHE=new WeakHashMap<>();
    private ExperimentalHullDust(){}
    public static List<CannonDustPayload.Surface> collect(ServerLevel level,Vec3 muzzle,Vec3 axis){
        var candidates=new ArrayList<CannonDustPayload.Surface>();
        for(var body:SableMarkers.cluster(level,muzzle))candidates.addAll(surfaces(level,body,muzzle));
        return dev.sablelightoverhaul.HullDustPattern.select(candidates,muzzle,axis,
            LightingCommon.HULL_FRONT_COVERAGE.get(),CannonDustPayload.MAX_SURFACES);
    }
    private static List<CannonDustPayload.Surface> surfaces(ServerLevel level,ServerSubLevel body,Vec3 muzzle){
        var cache=CACHE.get(body);long now=level.getGameTime();
        if(cache==null||now<cache.tick||now-cache.tick>=40){
            var faces=new ArrayList<Sample>();int seen=0;
            // Stable geometry choices: rebuilding the cache must not randomly move the dust pattern.
            var stable=new Random(body.getUniqueId().getMostSignificantBits()^body.getUniqueId().getLeastSignificantBits());
            for(var holder:body.getPlot().getLoadedChunks()){
                var chunk=holder.getChunk();if(chunk==null)continue;
                var sections=chunk.getSections();
                for(int sectionId=0;sectionId<sections.length;sectionId++){
                    var section=sections[sectionId];if(section==null||section.hasOnlyAir())continue;
                    int baseY=chunk.getSectionYFromSectionIndex(sectionId)<<4;
                    for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                        if(section.getBlockState(x,y,z).isAir())continue;
                        var pos=new BlockPos(chunk.getPos().getMinBlockX()+x,baseY+y,chunk.getPos().getMinBlockZ()+z);
                        var shape=HullDustCollision.shape(level,pos);if(shape.isEmpty())continue;
                        var boxes=shape.toAabbs();int boxCount=Math.min(4,boxes.size());
                        // At most one roof and one side per block, including partial-block collision boxes.
                        for(int kind=0;kind<2;kind++){
                            Sample chosen=null;int options=0;
                            for(int j=0;j<boxCount;j++){
                                var box=boxes.get(j);
                                for(var face:Direction.values()){
                                    if(face==Direction.DOWN||(kind==0)!=(face==Direction.UP))continue;
                                    double px=(box.minX+box.maxX)*.5,py=(box.minY+box.maxY)*.5,pz=(box.minZ+box.maxZ)*.5;
                                    switch(face){case UP->py=box.maxY;case NORTH->pz=box.minZ;case SOUTH->pz=box.maxZ;case WEST->px=box.minX;case EAST->px=box.maxX;default->{}}
                                    Vec3 local=new Vec3(pos.getX()+px,pos.getY()+py,pos.getZ()+pz);
                                    Vec3 probe=local.add(Vec3.atLowerCornerOf(face.getNormal()).scale(.025));
                                    var samplePos=BlockPos.containing(probe);
                                    var obstacles=!body.getPlot().contains(new net.minecraft.world.level.ChunkPos(samplePos))||!level.hasChunkAt(samplePos)?List.<net.minecraft.world.phys.AABB>of():samplePos.equals(pos)?boxes:HullDustCollision.shape(level,samplePos).toAabbs();
                                    double qx=probe.x-samplePos.getX(),qy=probe.y-samplePos.getY(),qz=probe.z-samplePos.getZ();
                                    boolean covered=false;for(var obstacle:obstacles)if(obstacle.contains(qx,qy,qz)){covered=true;break;}
                                    if(!covered&&stable.nextInt(++options)==0)chosen=new Sample(local,face);
                                }
                            }
                            if(chosen!=null){if(++seen<=8192)faces.add(chosen);else{int index=stable.nextInt(seen);if(index<8192)faces.set(index,chosen);}}
                        }
                    }
                }
            }
            cache=new Snapshot(now,List.copyOf(faces));CACHE.put(body,cache);
        }
        var tops=new ArrayList<CannonDustPayload.Surface>();var sides=new ArrayList<CannonDustPayload.Surface>();
        // Retired surface solver; world-space source sampling remains active.
        boolean coupling=dev.sablelightoverhaul.RetiredHullSettings.ENABLED;
        double radius2=Math.pow(LightingCommon.VEHICLE_DUST_RADIUS.get(),2);
        for(var sample:cache.faces){
            if(!coupling&&sample.face!=Direction.UP)continue;
            Vec3 n=Vec3.atLowerCornerOf(sample.face.getNormal());var support=BlockPos.containing(sample.local.subtract(n.scale(.01)));
            if(!level.hasChunkAt(support)||HullDustCollision.shape(level,support).isEmpty())continue;
            var pose=body.logicalPose();Vec3 world=pose.transformPosition(sample.local),normal=pose.transformNormal(n).normalize();
            if(world.distanceToSqr(muzzle)>radius2||normal.y<-.35)continue;
            var surface=coupling?new CannonDustPayload.Surface(world,normal,body.getUniqueId(),sample.local,n):new CannonDustPayload.Surface(world,normal);
            (sample.face==Direction.UP?tops:sides).add(surface);
        }
        tops.addAll(sides);
        return tops;
    }
}
