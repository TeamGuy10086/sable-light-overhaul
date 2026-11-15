package dev.sablelightoverhaul;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import dev.ryanhcode.sable.companion.math.Pose3d;
import org.joml.Vector3d;
import org.joml.Quaterniond;
class HullCollisionGeometryTest {
 @Test void querySeesPartialCollisionStepsAndSideFaces(){var shape=Shapes.or(Shapes.box(0,0,0,1,.5,1),Shapes.box(.5,.5,0,1,1,1));assertEquals(.5,shape.clip(new Vec3(.25,2,.5),new Vec3(.25,-1,.5),BlockPos.ZERO).getLocation().y,1e-8);assertEquals(1,shape.clip(new Vec3(.75,2,.5),new Vec3(.75,-1,.5),BlockPos.ZERO).getLocation().y,1e-8);assertEquals(0,shape.clip(new Vec3(-1,.25,.5),new Vec3(2,.25,.5),BlockPos.ZERO).getLocation().x,1e-8);}
 @Test void plotStorageCoordinatesRoundTripUnderRotationAndScale(){var pivot=new Vector3d(1_000_000,0,2_000_000);var pose=new Pose3d(new Vector3d(10,20,30),new Quaterniond().rotateY(Math.PI/2),pivot,new Vector3d(2));var local=new Vec3(pivot.x+2,1,pivot.z);var world=pose.transformPosition(local);var back=pose.transformPositionInverse(world);assertEquals(local.x,back.x,1e-7);assertEquals(local.y,back.y,1e-7);assertEquals(local.z,back.z,1e-7);assertTrue(world.distanceToSqr(local)>10000);}
 @Test void rotatingBodyProducesDifferentPointVelocitiesAcrossSameHull(){var pivot=new Vector3d(1_000_000,0,2_000_000);var previous=new Pose3d(new Vector3d(10,0,30),new Quaterniond(),pivot,new Vector3d(1));var current=new Pose3d(new Vector3d(10,0,30),new Quaterniond().rotateY(.05),pivot,new Vector3d(1));var center=new Vec3(pivot.x,0,pivot.z);var edge=center.add(2,0,0);assertEquals(0,current.transformPosition(center).distanceTo(previous.transformPosition(center)),1e-8);assertTrue(current.transformPosition(edge).distanceTo(previous.transformPosition(edge))>.09);}
 @Test void facingSurfaceGetsMoreDustButBackFacesKeepLooseningBaseline(){var point=Vec3.ZERO;var muzzle=new Vec3(0,2,0);assertEquals(1,CannonDustMotion.surfaceExposure(point,new Vec3(0,1,0),muzzle),1e-8);assertEquals(.4,CannonDustMotion.surfaceExposure(point,new Vec3(0,-1,0),muzzle),1e-8);assertEquals(.4,CannonDustMotion.surfaceExposure(point,new Vec3(1,0,0),muzzle),1e-8);}
}
