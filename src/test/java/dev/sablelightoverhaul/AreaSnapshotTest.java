package dev.sablelightoverhaul;

import dev.sablelightoverhaul.client.AreaSnapshot;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AreaSnapshotTest {
    @Test void worldAndTwoMovingReceiversAgreeOnBrightness() {
        var light=new AreaLight(100,70,200,1,0,0,30,12,0.25,15);
        var world=new AreaSnapshot(light,AreaSnapshot.bounds(light),null);
        for(var pose:new Matrix4d[]{new Matrix4d().translation(-1000000,0,-2000000),
                new Matrix4d().translation(5,0,7).rotateY(0.7).translate(-3000000,0,-4000000)}) {
            var inverse=new Matrix4d(pose).invert();
            var localBounds=AreaSnapshot.transform(world.bounds,inverse);
            var receiver=new AreaSnapshot(light,localBounds,pose);
            var local=inverse.transformPosition(new Vector3d(120,72,203));
            assertEquals(world.at(120,72,203),receiver.at(local.x,local.y,local.z),1e-7);
            pose.identity(); // modifying live pose must not affect an existing snapshot
            assertEquals(world.at(120,72,203),receiver.at(local.x,local.y,local.z),1e-7);
        }
    }
    @Test void boundsClipReceiverAndZeroLightCannotLeaveResidual() {
        var light=new AreaLight(0,0,0,1,0,0,30,12,0.25,0);
        var s=new AreaSnapshot(light,new AABB(-1,-1,-1,1,1,1),null);
        assertEquals(0,s.at(0,0,0)); assertEquals(0,s.at(20,0,0));
    }
}
