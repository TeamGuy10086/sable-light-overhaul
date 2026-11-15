package dev.sablelightoverhaul;

import dev.sablelightoverhaul.AfterSmokeModel.Point;
import dev.sablelightoverhaul.GroundDustFieldModel.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundDustRollTest {
    private static Point turn(Point p){return new Point(p.z(),p.y(),-p.x());}
    private static GroundDustFieldModel field(boolean rotated,double scale,double turbulence){
        var patches=new ArrayList<Patch>();
        for(int x=0;x<8;x++)for(int z=0;z<8;z++){
            var p=new Point((x-3.5)*4,0,(z-3.5)*4);
            if(p.length()<=16)patches.add(new Patch(rotated?turn(p):p,new Point(0,1,0),0xFFFFFF));
        }
        assertEquals(52,patches.size());
        return new GroundDustFieldModel(new Point(0,3,0),rotated?new Point(0,0,-1):new Point(1,0,0),patches,
            new Settings(16,4,3,.55,3.5,1.8,turbulence,.3,scale,1.25),424242);
    }
    @Test void frontBankMovesOutThenReturnsInwardAboveGround(){
        var m=field(false,1,0);
        var n=m.nodes().stream().filter(v->v.patch.position().x()==6&&v.patch.position().z()==2).findFirst().orElseThrow();
        double start=n.position.z(),peak=start;boolean returned=false;
        for(int t=0;t<60;t++){
            m.tick();peak=Math.max(peak,n.position.z());
            if(peak-start>1&&peak-n.position.z()>.5&&n.position.y()>1.5&&n.alpha(1)>.01)returned=true;
        }
        assertTrue(returned,"visible smoke must return inward while elevated, not merely rotate its billboard");
        assertTrue(n.position.x()-n.patch.position().x()>2,"bank also travels along firing direction");
    }
    @Test void frontRisesHigherAndIsDenserThanRear(){
        var m=field(false,1,0);double fp=0,bp=0,fa=0,ba=0;
        for(int t=0;t<60;t++){
            m.tick();double fy=0,by=0;int fc=0,bc=0;
            for(var n:m.nodes()){
                if(n.patch.position().x()>4){fy+=n.position.y();fa+=n.alpha(1);fc++;}
                if(n.patch.position().x()<-4){by+=n.position.y();ba+=n.alpha(1);bc++;}
            }
            fp=Math.max(fp,fy/fc);bp=Math.max(bp,by/bc);
        }
        assertTrue(fp>bp+1.5,"front/rear peak heights "+fp+" / "+bp);
        assertTrue(fa>ba*1.8,"front/rear integrated opacity "+fa+" / "+ba);
    }
    @Test void sizeControlEnlargesSupportWithoutCancellingItsOpacity(){
        var a=field(false,1,0);var b=field(false,2,0);
        for(int t=0;t<60;t++){
            a.tick();b.tick();
            for(int i=0;i<a.nodes().size();i++){
                var n=a.nodes().get(i);var v=b.nodes().get(i);
                assertEquals(n.width(1)*2,v.width(1),1e-9);
                assertEquals(n.alpha(1),v.alpha(1),1e-9);
            }
        }
    }
    @Test void entireMotionRotatesWithTheCannonAxis(){
        var a=field(false,1,.7);var b=field(true,1,.7);
        for(int t=0;t<70;t++){
            a.tick();b.tick();
            for(int i=0;i<a.nodes().size();i++){
                var expected=turn(a.nodes().get(i).position);var actual=b.nodes().get(i).position;
                assertEquals(expected.x(),actual.x(),1e-7);assertEquals(expected.y(),actual.y(),1e-7);assertEquals(expected.z(),actual.z(),1e-7);
            }
        }
    }
}
