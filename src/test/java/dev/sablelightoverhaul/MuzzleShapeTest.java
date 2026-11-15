package dev.sablelightoverhaul;
import dev.sablelightoverhaul.client.AreaSnapshot;
import org.joml.Matrix4d; import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MuzzleShapeTest {
    AreaLight diamond(double x,double y,double z) { return AreaLight.directional(0,0,0,x,y,z,32,16,16,8,12,.25,.65,.2,15,true); }
    @Test void widestAtMuzzleAndNarrowsForwardWithDiamondCrossSection() {
        var l=diamond(1,0,0);
        assertEquals(15,l.at(0,0,0)); assertTrue(l.at(0,0,12)>0);
        assertEquals(0,l.at(16,0,12)); assertTrue(l.at(16,0,4)>0);
        assertTrue(l.at(0,4,0)>0); assertEquals(0,l.at(0,4,8));
        assertEquals(0,l.at(32,0,0));
    }
    @Test void rearIsPartiallyClippedInsteadOfDeletingWholeRearHemisphere() {
        var l=diamond(1,0,0);
        assertTrue(l.at(-8,0,0)>0); assertTrue(l.at(-12,0,0)>0); assertEquals(0,l.at(-12.01,0,0));
    }
    @Test void bigAsymmetricRangesKeepPeakAtNativeSource() {
        var l=AreaLight.directional(0,0,0,1,0,0,25,8,12,6,8,.25,.65,.2,15,false);
        assertEquals(15,l.at(0,0,0)); assertEquals(0,l.at(25,0,0)); assertEquals(0,l.at(-8,0,0));
        assertEquals(0,l.at(0,0,12)); assertEquals(0,l.at(0,6,0));
        assertEquals(l.at(12.5,0,0),l.at(-4,0,0),1e-10);
    }
    @Test void boundsContainRotatedDiamondIncludingVerticalBarrel() {
        for(var axis:new double[][]{{0,1,0},{0,-1,0},{1/Math.sqrt(3),1/Math.sqrt(3),1/Math.sqrt(3)}}) {
            var l=diamond(axis[0],axis[1],axis[2]);var b=AreaSnapshot.bounds(l);
            for(int x=-33;x<=33;x+=2) for(int y=-33;y<=33;y+=2) for(int z=-33;z<=33;z+=2)
                if(l.at(x,y,z)>0) assertTrue(b.contains(x,y,z));
            var m=new Matrix4d().translation(100,-20,200).rotateY(.8);
            var local=AreaSnapshot.transform(b,new Matrix4d(m).invert());var receiver=new AreaSnapshot(l,local,m);
            var v=new Matrix4d(m).invert().transformPosition(new Vector3d(axis[0]*10,axis[1]*10,axis[2]*10));
            assertEquals(l.at(axis[0]*10,axis[1]*10,axis[2]*10),receiver.at(v.x,v.y,v.z),1e-9);
        }
    }
    @Test void independentlyTimedStagesCanReachConfiguredMaximumLifetime() {
        assertDoesNotThrow(() -> new LightLayout(600,.9));
        var life=new LightCurve(1200,0,0,0);assertEquals(1,life.brightness(1199)); assertEquals(0,life.brightness(1200));
    }
}

