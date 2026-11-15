package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.AfterSmokeModel;
import dev.sablelightoverhaul.TankAfterSmokePayload;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

/** Resolves Create rotation, CBCMW's actual animated barrel and then the Sable body, exactly once. */
final class TankSmokeAnchor {
    private final TankAfterSmokePayload source;
    private BlockPos springPos;
    private Method animate;
    private boolean searched;
    TankSmokeAnchor(TankAfterSmokePayload source){this.source=source;}
    AfterSmokeModel.Pose pose(ClientLevel level,float partial){
        var entity=level.getEntity(source.entityId());
        if(!(entity instanceof PitchOrientedContraptionEntity cannon)||entity.isRemoved()
                ||!entity.getUUID().equals(source.entityUuid())||cannon.getContraption()==null)return null;
        Vec3 local=source.mouth();
        var contraption=cannon.getContraption();
        if(!searched){
            searched=true;
            // Match the last barrel block to the renderer's toAnimate set; don't animate an unrelated spring.
            BlockPos end=BlockPos.containing(local.subtract(source.axis().scale(.05)));
            for(var entry:contraption.getBlocks().entrySet()){
                if(!entry.getValue().state().getBlock().getClass().getName().endsWith("MediumcannonRecoilBarrelBlock"))continue;
                BlockEntity be=contraption.getBlockEntityClientSide(entry.getKey());
                if(be==null){searched=false;continue;}
                try{
                    Field members=be.getClass().getField("toAnimate");
                    if(members.get(be) instanceof Map<?,?> map && map.containsKey(end.subtract(entry.getKey()))){
                        animate=be.getClass().getMethod("getAnimateOffset",float.class);
                        springPos=entry.getKey();break;
                    }
                }catch(ReflectiveOperationException incompatible){/* Static barrel remains valid on other CBCMW versions. */}
            }
        }
        if(springPos!=null){
            BlockEntity be=contraption.getBlockEntityClientSide(springPos);
            if(be!=null && be.getBlockState().hasProperty(BlockStateProperties.FACING))try{
                float scale=((Number)animate.invoke(be,partial)).floatValue();
                if(Float.isFinite(scale))local=local.add(Vec3.atLowerCornerOf(be.getBlockState()
                    .getValue(BlockStateProperties.FACING).getNormal()).scale((1-scale)*-.5));
            }catch(ReflectiveOperationException incompatible){springPos=null;}
        }
        Vec3 at=cannon.toGlobalVector(local,partial);
        Vec3 tip=cannon.toGlobalVector(local.add(source.axis()),partial);
        Vec3 translation=cannon.getPrevAnchorVec().lerp(cannon.getAnchorVec(),partial).subtract(cannon.getAnchorVec());
        at=at.add(translation);tip=tip.add(translation);
        if(source.body()!=null){
            if(!ModList.get().isLoaded("sable"))return null;
            Vec3[] world=Body.transform(level,source.body(),at,tip,partial);
            if(world==null)return null;at=world[0];tip=world[1];
        }
        Vec3 dir=tip.subtract(at).normalize();
        // User-tunable forward shift only for this effect; shells and the accepted blast stay untouched.
        at=at.add(dir.scale(MuzzleClientConfig.AFTER_SMOKE_OFFSET.get()));
        return new AfterSmokeModel.Pose(point(at),point(dir));
    }
    private static AfterSmokeModel.Point point(Vec3 v){return new AfterSmokeModel.Point(v.x,v.y,v.z);}
    private static final class Body {
        static Vec3[] transform(ClientLevel level,java.util.UUID id,Vec3 at,Vec3 tip,float partial){
            var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            var body=container==null?null:container.getSubLevel(id);
            if(body==null||body.isRemoved())return null;
            var pose=new dev.ryanhcode.sable.companion.math.Pose3d();
            body.lastPose().lerp(body.logicalPose(),partial,pose);
            return new Vec3[]{pose.transformPosition(at),pose.transformPosition(tip)};
        }
    }
}
