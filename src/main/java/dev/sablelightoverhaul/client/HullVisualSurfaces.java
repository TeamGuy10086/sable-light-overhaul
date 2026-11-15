package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.HullVisualGeometry;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Client-only visual-source correction. Never treats a BakedModel as the complete dynamic vehicle. */
final class HullVisualSurfaces {
    enum Status { MODEL, PARTIAL_MODEL, NO_FACE, UNSUPPORTED, BUDGET, ERROR, DISABLED }
    record Result(Vec3 point,Vec3 normal,Status status){}
    private record Entry(BlockState state,BakedModel model,ModelData data,long tick,List<HullVisualGeometry.Triangle> triangles,Status status){}
    private static WeakReference<ClientLevel> world=new WeakReference<>(null);
    private static final Map<BlockPos,Entry> cache=new LinkedHashMap<>();
    private static long tick=Long.MIN_VALUE;
    private static int builds,tests;
    static Result resolve(ClientLevel level,BlockPos pos,Vec3 on,Vec3 normal){
        if(!HullDustDebug.visualEnabled())return new Result(on,normal,Status.DISABLED);
        if(world.get()!=level){cache.clear();world=new WeakReference<>(level);tick=Long.MIN_VALUE;}
        if(tick!=level.getGameTime()){tick=level.getGameTime();builds=32;tests=32768;}
        if(!level.hasChunkAt(pos))return new Result(on,normal,Status.UNSUPPORTED);
        var state=level.getBlockState(pos);
        if(state.getRenderShape()!=RenderShape.MODEL)return new Result(on,normal,Status.UNSUPPORTED);
        var model=Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        var data=level.getModelDataManager().getAt(pos);if(data==null)data=ModelData.EMPTY;
        Entry entry=cache.get(pos);
        if(entry==null||entry.state!=state||entry.model!=model||entry.data!=data||tick-entry.tick>=20){
            if(builds--<=0)return new Result(on,normal,Status.BUDGET);
            try{
                var resolved=model.getModelData(level,pos,state,data);
                var random=RandomSource.create();long seed=state.getSeed(pos);
                var triangles=new ArrayList<HullVisualGeometry.Triangle>();
                // Null render type requests all baked layers. Each direction uses the same model seed.
                outer:for(int side=0;side<7;side++){
                    random.setSeed(seed);
                    var quads=model.getQuads(state,side==6?null:Direction.values()[side],random,resolved,null);
                    for(var quad:quads){
                        if(triangles.size()>=256)break outer;
                        int[] raw=quad.getVertices();int stride=raw.length/4;if(stride<3||raw.length%4!=0)continue;
                        Vec3[] v=new Vec3[4];for(int i=0;i<4;i++)v[i]=new Vec3(Float.intBitsToFloat(raw[i*stride]),Float.intBitsToFloat(raw[i*stride+1]),Float.intBitsToFloat(raw[i*stride+2]));
                        triangles.add(new HullVisualGeometry.Triangle(v[0],v[1],v[2]));triangles.add(new HullVisualGeometry.Triangle(v[0],v[2],v[3]));
                    }
                }
                entry=new Entry(state,model,data,tick,List.copyOf(triangles),state.hasBlockEntity()?Status.PARTIAL_MODEL:Status.MODEL);
            }catch(RuntimeException ex){
                // An incompatible third-party model must not interrupt the particle engine.
                entry=new Entry(state,model,data,tick,List.of(),Status.ERROR);
                com.mojang.logging.LogUtils.getLogger().debug("SLO hull visual model unavailable at {}",pos,ex);
            }
            if(cache.size()>=512)cache.remove(cache.keySet().iterator().next());cache.put(pos.immutable(),entry);
        }
        if(tests<entry.triangles.size())return new Result(on,normal,Status.BUDGET);tests-=entry.triangles.size();
        Vec3 offset=Vec3.atLowerCornerOf(pos).add(state.getOffset(level,pos));
        var hit=HullVisualGeometry.project(entry.triangles,on.subtract(offset),normal,MuzzleClientConfig.HULL_VISUAL_REACH.get());
        return hit==null?new Result(on,normal,entry.status==Status.ERROR?Status.ERROR:Status.NO_FACE)
            :new Result(hit.point().add(offset),hit.normal(),entry.status);
    }
}
