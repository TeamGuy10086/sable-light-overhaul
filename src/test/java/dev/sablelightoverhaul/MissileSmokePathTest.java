package dev.sablelightoverhaul;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MissileSmokePathTest {
    @Test void bendsAppendHistoryRatherThanMovingOldSmoke(){
        var p=new MissileSmokePath();p.sample(Vec3.ZERO,1,0,true);p.sample(new Vec3(10,0,0),2,2,true);
        var old=java.util.List.copyOf(p.nodes);p.sample(new Vec3(10,0,10),3,4,true);
        assertEquals(old,java.util.List.copyOf(p.nodes).subList(0,old.size()));
        assertEquals(new Vec3(10,0,10),p.nodes.getLast().position());
    }
    @Test void stopPreservesTailAndRestartCreatesANewStrip(){
        var p=new MissileSmokePath();p.sample(Vec3.ZERO,1,0,true);p.sample(new Vec3(8,0,0),2,1,true);
        int size=p.nodes.size(),strip=p.nodes.getLast().strip();p.sample(new Vec3(16,0,0),3,2,false);
        assertEquals(size,p.nodes.size());p.sample(new Vec3(25,0,0),4,3,true);
        assertNotEquals(strip,p.nodes.getLast().strip());assertEquals(size+1,p.nodes.size());
    }
    @Test void terminalEventDoesNotDeleteHistory(){
        var p=new MissileSmokePath();p.sample(Vec3.ZERO,1,0,true);p.sample(new Vec3(8,0,0),2,1,true);
        var old=java.util.List.copyOf(p.nodes);p.stop();assertEquals(old,java.util.List.copyOf(p.nodes));
        p.expire(162,160);assertTrue(p.nodes.isEmpty());
    }
    @Test void missingNetworkDataAndTeleportsNeverGenerateAConnectingBeam(){
        var p=new MissileSmokePath();p.sample(Vec3.ZERO,1,0,true);p.sample(new Vec3(200,0,0),2,1,true);
        assertEquals(2,p.nodes.size());assertNotEquals(p.nodes.getFirst().strip(),p.nodes.getLast().strip());
        int strip=p.nodes.getLast().strip();p.sample(new Vec3(201,0,0),3,12,true);
        assertEquals(3,p.nodes.size());assertNotEquals(strip,p.nodes.getLast().strip());
    }
    @Test void duplicateAndOutOfOrderUpdatesCannotExtendSmoke(){
        var p=new MissileSmokePath();p.sample(Vec3.ZERO,10,0,true);p.sample(new Vec3(10,0,0),10,1,true);p.sample(new Vec3(10,0,0),9,2,true);
        assertEquals(1,p.nodes.size());
    }
    @Test void interpolatedSamplesHaveContinuousAgeAndDistance(){
        var p=new MissileSmokePath();p.sample(Vec3.ZERO,1,0,true);p.sample(new Vec3(10,0,0),2,5,true);
        int i=0;for(var n:p.nodes){assertEquals(i,n.born(),1e-9);assertEquals(i*2,n.station(),1e-9);i++;}
        assertEquals(6,i);
    }
    @Test void historyIsBoundedEvenForUnboundedFlight(){
        var p=new MissileSmokePath();for(int i=0;i<2000;i++)p.sample(new Vec3(i*10,0,0),i,i,true);
        assertEquals(MissileSmokePath.MAX_NODES,p.nodes.size());assertEquals(19990,p.nodes.getLast().position().x);
    }
    @Test void invalidPositionsAreRejectedAndEnvelopeIsFinite(){
        var p=new MissileSmokePath();p.sample(new Vec3(Double.NaN,0,0),1,0,true);assertTrue(p.nodes.isEmpty());
        assertEquals(0,MissileSmokePath.opacity(160,160));assertEquals(0,MissileSmokePath.opacity(200,160));
        assertTrue(MissileSmokePath.radius(100,.45,.6)>MissileSmokePath.radius(10,.45,.6));
    }
}
