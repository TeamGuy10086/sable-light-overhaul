package dev.sablelightoverhaul;
import dev.sablelightoverhaul.AfterSmokeModel.Point;
import dev.sablelightoverhaul.GroundDustFieldModel.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundDustDensityTest {
    private GroundDustFieldModel field(double density){var patches=new ArrayList<Patch>();for(int i=0;i<64;i++)patches.add(new Patch(new Point(i%8,0,i/8),new Point(0,1,0),0xB39A78));return new GroundDustFieldModel(new Point(4,0,4),new Point(1,0,0),patches,new Settings(16,4,3,.2,3.5,1.8,.7,.4,1,1.25,density),31);}
    @Test void countIsOnePointFiveTimesWithoutMoreTerrainSamples(){var f=field(1.5);assertEquals(96,f.nodes().size());assertEquals(64,f.nodes().stream().map(n->n.patch).distinct().count());}
    @Test void zeroAndMaximumAreBounded(){assertEquals(0,field(0).nodes().size());assertTrue(field(0).finished());assertEquals(GroundDustFieldModel.MAX_NODES,field(8).nodes().size());}
    @Test void invalidationAppliesToAllCarriersOfSameSource(){var f=field(8);f.invalidate(0);for(int i=0;i<7;i++)f.tick();assertTrue(f.nodes().stream().filter(n->n.patchIndex==0).allMatch(n->n.alpha(1)==0));assertTrue(f.nodes().stream().anyMatch(n->n.patchIndex!=0&&!n.finished()));}
    @Test void addedCarriersMoveAndFadeWithoutNonFiniteValues(){var f=field(8);for(int i=0;i<100;i++){f.tick();for(var n:f.nodes()){assertTrue(n.position.finite());assertTrue(Double.isFinite(n.alpha(.5)));}}assertTrue(f.finished());}
}
