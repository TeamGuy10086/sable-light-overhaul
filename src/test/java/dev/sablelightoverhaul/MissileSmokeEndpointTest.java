package dev.sablelightoverhaul;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MissileSmokeEndpointTest {
    private MissileSmokePath path(){
        var p=new MissileSmokePath();p.sample(Vec3.ZERO,100,1,true);
        p.sample(new Vec3(10,0,0),110,2,true);return p;
    }
    @Test void timestampsUseAuthoritativeDomainEvenWithDifferentLocalClock(){
        var p=path();int i=0;
        for(var n:p.nodes){assertEquals(100+2*i,n.sourceTick());assertEquals(1+.2*i,n.born(),1e-8);i++;}
    }
    @Test void laggingHeadCropsFutureOnlyInRenderView(){
        var p=path();var saved=List.copyOf(p.nodes);
        var f=p.frameNodes(110,105,new Vec3(5,0,0),2.5);
        assertTrue(f.liveHead());assertEquals(4,f.nodes().size());
        assertEquals(new Vec3(5,0,0),f.nodes().getLast().position());
        for(var n:f.nodes())assertTrue(n.sourceTick()<=105);
        assertEquals(saved,List.copyOf(p.nodes));
        p.sample(new Vec3(20,0,0),120,3,true);
        assertEquals(120,p.nodes.getLast().sourceTick());
    }
    @Test void hundredsOfRenderFramesDoNotConsumeNodeBudgetOrAlterAges(){
        var p=path();var saved=List.copyOf(p.nodes);
        for(int i=0;i<1000;i++)p.frameNodes(110,100+i*.01,new Vec3(i*.01,0,0),2.5);
        assertEquals(saved,List.copyOf(p.nodes));
    }
    @Test void exactSampleTimeReplacesEndpointWithoutDuplicateKnot(){
        var f=path().frameNodes(110,106,new Vec3(6,0,0),2.5);
        assertTrue(f.liveHead());assertEquals(4,f.nodes().size());
        assertEquals(2.5,f.nodes().getLast().born());
        assertEquals(6,f.nodes().getLast().station());
    }
    @Test void terminalAndInvalidEndpointsDoNotReanimateStoppedSmoke(){
        var p=path();var saved=List.copyOf(p.nodes);p.stop();
        assertFalse(p.frameNodes(110,105,new Vec3(5,0,0),3).liveHead());assertEquals(saved,List.copyOf(p.nodes));
        p=path();assertFalse(p.frameNodes(109,105,Vec3.ZERO,3).liveHead());
        assertFalse(p.frameNodes(110,Double.NaN,Vec3.ZERO,3).liveHead());
        assertFalse(p.frameNodes(110,111,Vec3.ZERO,3).liveHead());
        assertFalse(p.frameNodes(110,105,new Vec3(Double.NaN,0,0),3).liveHead());
    }
    @Test void headBeforeRetainedHistoryCannotConnectToFuture(){
        var f=path().frameNodes(110,99,Vec3.ZERO,3);
        assertTrue(f.nodes().isEmpty());assertFalse(f.liveHead());
    }
    @Test void teleportAndStaleSamplesCannotDrawAConnectingBeam(){
        var p=path();p.sample(new Vec3(200,0,0),120,3,true);
        assertFalse(p.frameNodes(120,115,new Vec3(100,0,0),3.5).liveHead());
        assertFalse(path().frameNodes(110,110,new Vec3(1000,0,0),3).liveHead());
        assertFalse(path().frameNodes(110,110,new Vec3(10,0,0),20).liveHead());
    }
    @Test void freshNozzleCompensatesRenderWindWithoutMovingSavedHistory(){
        var p=path();Vec3 wind=new Vec3(1,0,2),worldNozzle=new Vec3(5,2,3);
        var f=p.frameNodes(110,105,worldNozzle.subtract(wind),2.5);
        assertEquals(worldNozzle,f.nodes().getLast().position().add(wind));
        assertEquals(0,2.5-f.nodes().getLast().born());
    }
}
