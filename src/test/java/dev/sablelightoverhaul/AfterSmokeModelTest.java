package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.*;
import dev.sablelightoverhaul.AfterSmokeModel.*;
import org.junit.jupiter.api.Test;

/** DeepSeek proposed the cases; Codex corrected assumptions about moving smoke and emission duration. */
class AfterSmokeModelTest {
    private static final double EPS=1e-8;
    private static Settings settings(){return new Settings(0,.6,1.2,.2,1,2,.5,0,.3,.8);}
    private static Pose pose(double x,double y,double z){return new Pose(new Point(x,y,z),new Point(0,1,0));}
    private static void ticks(AfterSmokeModel m,int count){for(int i=0;i<count;i++)m.tick(pose(0,0,0));}
    @Test void stationaryEmitsThenCompletelyTerminates(){
        var m=new AfterSmokeModel(settings(),1);ticks(m,20);assertFalse(m.nodes().isEmpty());
        for(var n:m.nodes()){assertTrue(n.position.finite());assertTrue(n.position.y()>0);}
        ticks(m,140);assertTrue(m.finished());
    }
    @Test void translationDoesNotChangeDynamics(){
        var a=new AfterSmokeModel(settings(),5);var b=new AfterSmokeModel(settings(),5);
        for(int i=0;i<20;i++){a.tick(pose(0,0,0));b.tick(pose(10,4,-3));}
        assertFalse(a.nodes().isEmpty());assertEquals(a.nodes().size(),b.nodes().size());
        for(int i=0;i<a.nodes().size();i++){
            var x=a.nodes().get(i);var y=b.nodes().get(i);
            assertEquals(x.u,y.u);assertEquals(x.age,y.age);
            assertEquals(10,y.position.x()-x.position.x(),EPS);
            assertEquals(4,y.position.y()-x.position.y(),EPS);
            assertEquals(-3,y.position.z()-x.position.z(),EPS);
        }
    }
    @Test void windAdvectsOldSmokeWithoutMovingNewBirths(){
        var config=new Settings(0,2,3,.3,1,2,0,0,0,.5);
        var calm=new AfterSmokeModel(config,1);
        var windy=new AfterSmokeModel(config,1);
        var source=pose(0,0,0);
        for(int i=0;i<21;i++){
            calm.tick(source);
            windy.tick(source,null,new Point(.2,0,0));
        }
        assertEquals(4,windy.nodes().getFirst().position.x()-calm.nodes().getFirst().position.x(),EPS);
        assertEquals(calm.nodes().getLast().position,windy.nodes().getLast().position);
    }
    @Test void teleportBreaksChainButOldSmokeContinuesSameTrajectory(){
        var a=new AfterSmokeModel(settings(),7);var b=new AfterSmokeModel(settings(),7);
        ticks(a,6);ticks(b,6);var old=a.nodes().getFirst();var control=b.nodes().getFirst();
        a.tick(pose(40,0,0));b.tick(pose(0,0,0));
        assertEquals(control.position,old.position);assertNotEquals(old.chain,a.nodes().getLast().chain);
        assertEquals(40,a.nodes().getLast().position.x(),EPS);
        a.tick(pose(40,0,0));assertTrue(Math.abs(a.nodes().get(a.nodes().size()-2).position.x()-40)<.1);
    }
    @Test void repeatedShotsBoundNodesAndDoNotResetOldAge(){
        var m=new AfterSmokeModel(settings(),9);ticks(m,6);var old=m.nodes().getFirst();double age=old.age;
        m.restart();m.tick(pose(0,0,0));assertEquals(age+.05,old.age,EPS);
        for(int i=0;i<1000;i++){m.restart();m.tick(pose(i*.5,0,0));assertTrue(m.nodes().size()<=64);}
        for(int i=0;i<160;i++)m.tick(pose(500,0,0));assertTrue(m.finished());
    }
    @Test void nullSourceStopsBirthsButDoesNotFreezeOldSmoke(){
        var m=new AfterSmokeModel(settings(),10);ticks(m,6);int count=m.nodes().size();var old=m.nodes().getFirst();
        Point p=old.position;double age=old.age;m.tick(null);
        assertEquals(count,m.nodes().size());assertEquals(age+.05,old.age,EPS);assertNotEquals(p,old.position);
        for(int i=0;i<160;i++)m.tick(null);assertTrue(m.finished());
    }
    @Test void trimmingDoesNotResetMaterialPhase(){
        var m=new AfterSmokeModel(new Settings(0,10,10,.2,1,1,.5,0,.3,.8),11);ticks(m,80);
        assertEquals(64,m.nodes().size());var survivor=m.nodes().get(10);double u=survivor.u;
        m.trimOldest(10);assertSame(survivor,m.nodes().getFirst());assertEquals(u,m.nodes().getFirst().u);
    }
    @Test void zeroOpacityNeverBecomesVisible(){
        var m=new AfterSmokeModel(new Settings(0,1,2,.2,1,1,.5,0,.3,0),12);ticks(m,12);
        for(var n:m.nodes()){assertEquals(0,n.alpha(0));assertEquals(0,n.alpha(1));}
    }
    @Test void rotatingSourceUsesProvidedPoseSamplerAndKeepsChain(){
        var m=new AfterSmokeModel(settings(),1);m.tick(pose(0,0,0));int chain=m.nodes().getFirst().chain;
        m.tick(pose(1,0,0),t->pose(t,Math.sin(t*Math.PI),0));
        assertTrue(m.nodes().stream().allMatch(n->n.chain==chain));
        assertTrue(m.nodes().stream().anyMatch(n->n.position.y()>.9));
    }
    @Test void invalidSourceStopsWithoutPoisoningNodes(){
        var m=new AfterSmokeModel(settings(),1);ticks(m,5);
        m.tick(pose(Double.NaN,0,0));for(var n:m.nodes())assertTrue(n.position.finite());
        ticks(m,100);assertTrue(m.finished());
    }
    @Test void ribbonMarkerPreservesQuantizedLightingAndDiffersFromOldSmoke(){
        for(int b=0;b<=240;b+=16)for(int s=0;s<=240;s+=16){
            int packed=(s<<16)|b,marked=GasVertexData.ribbonLight(packed);
            assertEquals(packed,marked&~0x000F000F);assertEquals(11,marked&15);
            assertNotEquals(GasVertexData.smokeLight(packed),marked);
        }
    }
    @Test void referenceJetTravelsForwardBeforeRising(){
        var m=new AfterSmokeModel(new Settings(0,1.8,2.5,.3,1.4,2.6,.15,0,.7,.55),123);
        var pose=new Pose(new Point(0,0,0),new Point(1,0,0));
        m.tick(pose);var first=m.nodes().getFirst();
        for(int i=0;i<20;i++)m.tick(pose);
        assertTrue(first.position.x()>1.5,"forward jet should not stall at the mouth");
        assertTrue(Math.abs(first.position.y())<first.position.x()*.5,"rise must remain secondary");
        assertTrue(first.alpha(1)>0);
    }
    @Test void extraRotationSamplesDoNotMultiplyOpticalThickness(){
        var s=new Settings(0,2,3,.3,1.4,2.6,.15,0,.7,.55);
        var slow=new AfterSmokeModel(s,1);var fast=new AfterSmokeModel(s,1);
        for(int i=0;i<10;i++){
            slow.tick(pose(0,0,0));fast.tick(pose(i*.6,0,0));
        }
        assertTrue(fast.nodes().size()>slow.nodes().size());
        double a=slow.nodes().stream().mapToDouble(n->-Math.log1p(-n.alpha(1))).sum();
        double b=fast.nodes().stream().mapToDouble(n->-Math.log1p(-n.alpha(1))).sum();
        assertEquals(a,b,1e-10,"sub-samples partition the emission, not duplicate it");
    }
    @Test void noCurlAndNoRisePreservesShotAxis(){
        var m=new AfterSmokeModel(new Settings(0,1,2,.3,1,2,0,0,0,.5),1);
        var pose=new Pose(new Point(0,0,0),new Point(1,0,0));
        for(int i=0;i<20;i++)m.tick(pose);
        for(var n:m.nodes()){
            assertEquals(0,n.position.y(),EPS);assertEquals(0,n.position.z(),EPS);
            assertTrue(n.alpha(1)>=0&&n.alpha(1)<1);
        }
    }
}
