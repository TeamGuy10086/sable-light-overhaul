package dev.sablelightoverhaul.lighting;

import dev.sablelightoverhaul.SloNetwork;
import dev.sablelightoverhaul.AfterSmokeModel.Point;
import dev.sablelightoverhaul.GroundDustFieldModel.*;
import dev.sablelightoverhaul.GroundDustFieldPayload;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.fml.ModList;

/** Bounded main-world terrain sampling. No chunk loads, repeat rays or post-hit random offsets. */
public final class GroundDustFieldServer {
    private GroundDustFieldServer(){}
    private static final class Budget {long tick=Long.MIN_VALUE;int remaining;}
    private static final Map<ServerLevel,Budget> budgets=new WeakHashMap<>();
    public static void emit(ServerLevel level,List<ServerPlayer> viewers,Vec3 origin,Vec3 axis){
        if(LightingCommon.GROUND_DUST_DENSITY.get()<=0)return;
        int resolution=LightingCommon.GROUND_FIELD_RESOLUTION.get();
        double radius=LightingCommon.CANNON_GROUND_RADIUS.get(),spacing=2*radius/resolution;
        double depth=LightingCommon.CANNON_GROUND_DISTANCE.get();
        Budget budget=budgets.computeIfAbsent(level,k->new Budget());
        if(budget.tick!=level.getGameTime()){budget.tick=level.getGameTime();budget.remaining=256;}
        var patches=new ArrayList<Patch>();
        Vec3 forward=new Vec3(axis.x,0,axis.z);forward=forward.lengthSqr()<1e-6?new Vec3(1,0,0):forward.normalize();
        Vec3 side=new Vec3(-forward.z,0,forward.x);
        var offsets=new ArrayList<Vec3>();
        for(int row=0;row<resolution;row++)for(int col=0;col<resolution;col++){
            double x=(col-(resolution-1)*.5)*spacing,z=(row-(resolution-1)*.5)*spacing;
            if(x*x+z*z<=radius*radius)offsets.add(forward.scale(x).add(side.scale(z)));
        }
        // Either sample the full footprint or omit this field; never let overload produce a narrow strip.
        if(budget.remaining<offsets.size())return;
        budget.remaining-=offsets.size();
        for(Vec3 offset:offsets){
            Vec3 from=origin.add(offset),to=from.subtract(0,depth,0);
            if(!level.hasChunkAt(BlockPos.containing(from)))continue;
            var context=new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,CollisionContext.empty());
            if(ModList.get().isLoaded("sable")&&!SableGroundClip.terrainOnly(context))return;
            var hit=level.clip(context);
            if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=Direction.UP)continue;
            BlockPos support=hit.getBlockPos();
            if(!level.hasChunkAt(support)||!level.getFluidState(support).isEmpty()
                    ||!level.getFluidState(support.above()).isEmpty())continue;
            Vec3 at=hit.getLocation();
            if(at.y>from.y+.001||from.y-at.y>depth+.001)continue;
            var state=level.getBlockState(support);if(state.getCollisionShape(level,support).isEmpty())continue;
            int material=LightingCommon.CANNON_GROUND_TINT.get()?net.minecraft.world.level.block.Block.getId(state):-1;
            patches.add(new Patch(point(at),new Point(0,1,0),LightingCommon.CANNON_GROUND_COLOR.get()&0xFFFFFF,material,support.asLong()));
        }
        if(patches.isEmpty())return;
        var settings=new Settings(radius,spacing,LightingCommon.CANNON_GROUND_LIFETIME.get()*.05,
            LightingCommon.GROUND_FIELD_SPREAD.get(),LightingCommon.GROUND_FIELD_SPEED.get(),LightingCommon.GROUND_FIELD_LIFT.get(),
            LightingCommon.GROUND_FIELD_TURBULENCE.get(),LightingCommon.CANNON_GROUND_ALPHA.get(),LightingCommon.GROUND_FIELD_SIZE.get(),LightingCommon.GROUND_FIELD_STEP.get(),LightingCommon.GROUND_DUST_DENSITY.get(),LightingCommon.GROUND_CORRIDOR_WIDTH.get(),LightingCommon.GROUND_OUTER_LIFT.get(),LightingCommon.GROUND_MOTION_SCALE.get(),LightingCommon.GROUND_HEIGHT_SCALE.get());
        var event=new GroundDustFieldPayload(level.dimension().location(),point(origin),point(axis),level.getGameTime(),level.random.nextLong(),settings,patches);
        for(var viewer:viewers)SloNetwork.sendToPlayer(viewer,event);
        if(LightingCommon.on(LightingCommon.GEN_DEBUG))com.mojang.logging.LogUtils.getLogger().info(
            "SLO ground field: probes={} patches={} spacing={} remainingTickBudget={}",offsets.size(),patches.size(),spacing,budget.remaining);
    }
    private static Point point(Vec3 p){return new Point(p.x,p.y,p.z);}
}
