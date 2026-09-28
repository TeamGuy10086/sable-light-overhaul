package dev.cbcmuzzlelight;
import org.junit.jupiter.api.Test;
import dev.cbcmuzzlelight.client.AreaSnapshot;
import org.joml.Matrix4d;
import static org.junit.jupiter.api.Assertions.*;
class DirectionalLightTest {
    private AreaLight cone(double dx,double dy,double dz) {
        return new AreaLight(5*dx,5*dy,5*dz,dx,dy,dz,5,3,0.25,15,true,0.65,0.2);
    }
    @Test void coneHasNoRearLightAndWidensForward() {
        var c=cone(1,0,0);
        assertEquals(0,c.at(-0.01,0,0)); assertEquals(15,c.at(1,0,0));
        assertEquals(0,c.at(1,1,0)); assertTrue(c.at(5,1,0)>0);
        assertEquals(0,c.at(10,0,0)); assertEquals(0,c.at(5,3,0));
    }
    @Test void falloffIsContinuousAndTailHasSmallerSlope() {
        double c=0.25,m=0.65,t=0.2;
        assertEquals(1,AreaLight.falloff(c,c,m,t)); assertEquals(t,AreaLight.falloff(m,c,m,t),1e-12);
        assertEquals(0,AreaLight.falloff(1,c,m,t));
        assertEquals(AreaLight.falloff(m-1e-9,c,m,t),AreaLight.falloff(m+1e-9,c,m,t),1e-7);
        assertTrue((1-t)/(m-c)>t/(1-m));
        double prev=1;
        for(int i=0;i<=1000;i++) { double v=AreaLight.falloff(i/1000.0,c,m,t); assertTrue(v<=prev+1e-12 && v>=0); prev=v; }
    }
    @Test void rotatedConeFitsDeclaredBoundsAndSurvivesReceiverTransform() {
        double n=1/Math.sqrt(3); var light=cone(n,n,n); var bounds=AreaSnapshot.bounds(light);
        for(double x=-5;x<15;x+=0.4) for(double y=-5;y<15;y+=0.4) for(double z=-5;z<15;z+=0.4)
            if(light.at(x,y,z)>0) assertTrue(bounds.contains(x,y,z));
        var matrix=new Matrix4d().translation(100,40,-200).rotateY(0.8);
        var local=AreaSnapshot.transform(bounds,new Matrix4d(matrix).invert());
        var area=new AreaSnapshot(light,local,matrix);
        var q=new Matrix4d(matrix).invert().transformPosition(new org.joml.Vector3d(3*n,3*n,3*n));
        assertEquals(light.at(3*n,3*n,3*n),area.at(q.x,q.y,q.z),1e-9);
    }
    @Test void radiiScaleLinearlyUntilExplicitCap() {
        assertEquals(7.5,LightSizing.radius(30,1.0/4,64));
        assertEquals(30,LightSizing.radius(30,4.0/4,64));
        assertEquals(60,LightSizing.radius(30,8.0/4,64));
        assertEquals(64,LightSizing.radius(30,16.0/4,64));
        assertThrows(IllegalArgumentException.class,() -> LightSizing.radius(30,Double.NaN,64));
    }
    @Test void trackedSizeUpdatesWithoutMovingOrLosingShotMetadata() {
        var s=new FlashStore(4);
        s.add(0,0,0,1,0,0,0,10,LightCurve.DEFAULT_BIG,LightLayout.BIG,new FlashStore.Emitter(1,0,0,0,8,12));
        long token=s.snapshot().getFirst().token();
        s.smokeSize(token,2.5); var f=s.find(token);
        assertEquals(2.5,f.smokeSize()); assertEquals(8,f.emitter().charge()); assertEquals(12,f.emitter().plumeSize());
        s.smokeSize(token,Double.NaN); assertEquals(2.5,s.find(token).smokeSize());
    }
}
