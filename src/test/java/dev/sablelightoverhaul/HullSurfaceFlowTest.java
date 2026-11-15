package dev.sablelightoverhaul;
import dev.sablelightoverhaul.AfterSmokeModel.Point;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HullSurfaceFlowTest {
 static final Point ZERO=new Point(0,0,0),UP=new Point(0,1,0);
 static class Fake implements HullSurfaceFlow.Surface {
  double offset,velocity,edge=100;boolean enabled=true,starved,blocked;int samples;
  public boolean valid(){return enabled;}public boolean available(){return !starved;}
  public HullSurfaceFlow.Contact sample(Point local,Point normal,double step){samples++;if(starved)return null;double x=local.x()+(blocked?0:step);if(x>edge)return null;var p=new Point(x,0,0);return new HullSurfaceFlow.Contact(p,UP,new Point(x+offset,0,0),UP,blocked?ZERO:new Point(1,0,0),new Point(velocity,0,0));}
  public Point sweep(Point a,Point b){return b;}
 }
 HullDustModel.Settings settings(double attach){return new HullDustModel.Settings(.8,0,0,1,attach,.15,.06,.8);}
 HullSurfaceFlow flow(Fake source,double attach){return new HullSurfaceFlow(source,ZERO,UP,ZERO,7,2.5,2,3,.8,.1,.3,.5,settings(attach));}
 @Test void followsBodyOnlyBeforeIrreversibleRelease(){var s=new Fake();var f=flow(s,.35);for(int i=0;i<4;i++){s.offset=i*.5;f.tick();assertTrue(f.attached());assertEquals(s.offset+f.local().x(),f.position.x(),1e-8);}for(int i=0;i<10&&f.attached();i++)f.tick();assertFalse(f.attached());int samples=s.samples;double x=f.position.x();s.offset+=100;f.tick();assertEquals(samples,s.samples);assertTrue(Math.abs(f.position.x()-x)<1);}
 @Test void edgeReleasesBeforeMaximumHold(){var s=new Fake();s.edge=.05;var f=flow(s,1);for(int i=0;i<4;i++)f.tick();assertFalse(f.attached());assertEquals(0,f.blend(0),1e-8);}
 @Test void queryStarvationHoldsThenRecovers(){var s=new Fake();var f=flow(s,.8);f.tick();var p=f.local();s.starved=true;f.tick();f.tick();assertTrue(f.attached());assertEquals(p,f.local());s.starved=false;f.tick();assertTrue(f.local().x()>p.x());}
 @Test void lostBodyRemovesAttachedDust(){var s=new Fake();var f=flow(s,.8);f.tick();s.enabled=false;f.tick();assertTrue(f.finished());assertEquals(0,f.alpha(1));}
 @Test void inheritedPointVelocityChangesFreeMotion(){var a=new Fake();a.velocity=2;var b=new Fake();var f=flow(a,0);var g=flow(b,0);f.tick();g.tick();double x=f.position.x(),y=g.position.x();f.tick();g.tick();assertTrue(f.position.x()-x>g.position.x()-y+.04);}
 @Test void blockedSurfaceDoesNotDriftIntoObstacle(){var s=new Fake();s.blocked=true;var f=flow(s,.8);for(int i=0;i<8;i++)f.tick();assertTrue(f.attached());assertEquals(ZERO,f.local());assertEquals(.06,f.position.y(),1e-8);}
 @Test void extraBirthDelayPreservesLifetimeAndDoesNotSpin(){
  var s=new Fake();var f=new HullSurfaceFlow(s,ZERO,UP,ZERO,7,2.5,1,3,.8,.1,.3,.5,settings(.35),.3);
  double roll=f.roll(1);for(int i=0;i<5;i++){f.tick();assertEquals(0,f.alpha(1));}
  double peak=0;for(int i=0;i<25;i++){f.tick();peak=Math.max(peak,f.alpha(1));assertEquals(roll,f.roll(.5),1e-8);}assertTrue(peak>0);assertTrue(f.finished());
 }
 @Test void blockedRoofDoesNotEjectVerticallyEvenWithMaximumOldLift(){
  var s=new Fake();s.blocked=true;
  var settings=new HullDustModel.Settings(16,0,0,1,.2,.15,.06,0);
  var f=new HullSurfaceFlow(s,ZERO,UP,ZERO,3,2,2,20,.8,0,.9,.5,settings);
  while(f.attached())f.tick();Point at=f.position;
  for(int i=0;i<6;i++){f.tick();assertEquals(at,f.position);}
 }
 @Test void releaseUsesDampedActualSlideNotFreshWindSpeed(){
  var s=new Fake();var f=flow(s,.35);
  Point before=ZERO,last=ZERO;
  while(f.attached()){before=last;last=f.position;f.tick();}
  double actual=last.x()-before.x(),at=f.position.x();f.tick();
  assertTrue(f.position.x()>at);
  assertTrue(f.position.x()-at<=actual+1e-8,"release must not regain undamped wind speed");
 }
 @Test void localTransportUsesWorldVectorWithoutVisualSurfaceJump(){
  var s=new Fake(){
   @Override public Point slideVelocity(Point from,Point to){return new Point(0,0,(to.x()-from.x())/HullSurfaceFlow.STEP);}
  };
  var f=flow(s,.2);while(f.attached()){s.offset+=.5;f.tick();}
  Point at=f.position;f.tick();assertEquals(at.x(),f.position.x(),1e-8);assertTrue(f.position.z()>at.z());
 }
 @Test void grazingVisualProjectionCannotAmplifyTinyVerticalComponent(){
  Point input=new Point(1,0,0),normal=new Point(1,.001,0).normalized();
  Point projected=HullSurfaceFlow.tangentVelocity(input,normal);
  assertTrue(projected.length()<.002);assertTrue(Math.abs(projected.y())<.002);
  assertEquals(0,projected.x()*normal.x()+projected.y()*normal.y(),1e-12);
 }
 @Test void releaseDensityFallsWhileWidthExpandsAndNoRollReturns(){
  var s=new Fake();var f=flow(s,.35);while(f.attached())f.tick();
  double first=f.alpha(1),last=first,width=f.width(1),roll=f.roll(1);
  assertEquals(f.alpha(0.999),first,1e-3);
  for(int i=0;i<8;i++){
   f.tick();assertTrue(f.alpha(1)<=last+1e-10);assertTrue(f.width(1)>=width);
   assertEquals(roll,f.roll(.5));last=f.alpha(1);width=f.width(1);
  }
  assertTrue(last<first*.04,"detached silhouette should be faint by 0.4 seconds");
  for(int i=0;i<4;i++)f.tick();assertTrue(f.finished());assertEquals(0,f.alpha(1));
 }
 @Test void earlyEdgeAndMinimumFadeStillExposeAContinuousFinalInterval(){
  var s=new Fake();s.edge=.001;
  var settings=new HullDustModel.Settings(.8,0,0,1,.8,.15,.06,0,2,.3,.1,.05,0);
  var f=new HullSurfaceFlow(s,ZERO,UP,ZERO,3,2,2,3,.8,0,.9,.5,settings);
  f.tick();assertFalse(f.attached());double start=f.alpha(1);assertTrue(start>0);
  f.tick();assertFalse(f.finished());assertEquals(start,f.alpha(0),1e-10);
  double last=start;for(int i=0;i<=100;i++){double a=f.alpha(i/100.0);assertTrue(a<=last+1e-10);last=a;}
  assertEquals(0,last,1e-10);f.tick();assertTrue(f.finished());
 }
 @Test void lateReleaseStillRespectsOriginalLifetimeAndLongFadeDoesNotExtendIt(){
  var s=new Fake();
  var settings=new HullDustModel.Settings(.8,0,0,1,.6,.15,.06,0,2,.3,.1,10,8);
  var f=new HullSurfaceFlow(s,ZERO,UP,ZERO,3,2,1,3,.8,0,.9,.5,settings);
  for(int i=0;i<19;i++)f.tick();assertFalse(f.finished());assertTrue(f.alpha(1)<.02);
  f.tick();assertTrue(f.finished());assertEquals(0,f.alpha(1));
 }
 @Test void supportedSizeCapsGrowthAndFreeExpansionStartsAtTheFittedSize(){
  var s=new Fake(){@Override public double supportRadius(Point p,Point n,double requested){return .4;}};s.blocked=true;
  var f=flow(s,.35);while(f.attached()){f.tick();assertTrue(f.width(1)<=.8+1e-9);}
  double released=f.width(1);assertEquals(.8,released,1e-9);f.tick();f.tick();
  assertTrue(f.width(1)>released);assertTrue(f.width(1)<released*1.8);assertTrue(f.alpha(1)>0);
 }
 @Test void insufficientBirthSupportSkipsThePuff(){
  var s=new Fake(){@Override public double supportRadius(Point p,Point n,double requested){return .03;}};
  var f=flow(s,.35);f.tick();assertTrue(f.finished());assertEquals(0,f.alpha(1));
 }
 @Test void pendingFootprintIsHiddenThenRecoversAndCannotWaitForever(){
  var waiting=new Fake(){@Override public double supportRadius(Point p,Point n,double requested){return starved?Double.NaN:.5;}
    @Override public HullSurfaceFlow.Contact sample(Point p,Point n,double step){return new HullSurfaceFlow.Contact(p,UP,p,UP,new Point(1,0,0),ZERO);}};
  waiting.starved=true;var f=flow(waiting,.35);
  for(int i=0;i<6;i++){f.tick();assertEquals(0,f.alpha(1));assertFalse(f.finished());}
  waiting.starved=false;f.tick();assertTrue(f.attached());assertTrue(f.alpha(1)>0);
  waiting.starved=true;var forever=flow(waiting,.35);for(int i=0;i<12;i++)forever.tick();assertTrue(forever.finished());
 }
 @Test void movingTowardAnEdgeDoesNotRegrowBeyondItsSupportedRadius(){
  var s=new Fake(){@Override public double supportRadius(Point p,Point n,double requested){return Math.max(0,.4-p.x());}};
  var f=flow(s,1);for(int i=0;i<10&&f.attached();i++){f.tick();if(f.attached())assertTrue(f.width(1)*.5<=.4-f.local().x()+1e-8);}
 }
}
