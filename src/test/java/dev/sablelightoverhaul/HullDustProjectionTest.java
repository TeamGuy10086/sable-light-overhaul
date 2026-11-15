package dev.sablelightoverhaul;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HullDustProjectionTest {
 static final Vec3 RIGHT=new Vec3(1,0,0),UP=new Vec3(0,1,0),FRONT=new Vec3(0,0,1);
 @Test void faceAndEdgeViewsKeepTheirFootprintWithoutRoll(){
  var face=HullDustProjection.project(FRONT,RIGHT,UP,2,.3,1.2,0);
  var roof=HullDustProjection.project(UP,RIGHT,UP,2,.3,1.2,0);
  var side=HullDustProjection.project(RIGHT,RIGHT,UP,2,.3,1.2,0);
  assertEquals(2,face.halfHeight(),1e-7);assertEquals(.6,roof.halfHeight(),1e-7);
  assertEquals(.6,side.halfWidth(),1e-7);assertEquals(2,side.halfHeight(),1e-7);
  assertEquals(.45,roof.lift(),1e-7);assertEquals(0,side.roll());
 }
 @Test void releaseRemainsContinuousAndFixedThroughItsEntireWindow(){
  var n=new Vec3(1,1,0).normalize();var start=HullDustProjection.project(n,RIGHT,UP,2,.3,1.2,0);
  var near=HullDustProjection.project(n,RIGHT,UP,2,.3,1.2,1e-6);
  assertEquals(start.halfWidth(),near.halfWidth(),1e-5);assertEquals(start.halfHeight(),near.halfHeight(),1e-5);
  for(int i=0;i<=100;i++)assertEquals(0,HullDustProjection.project(n,RIGHT,UP,2,.3,1.2,i/100.0).roll());
  var free=HullDustProjection.project(n,RIGHT,UP,2,.3,1.2,1);
  assertEquals(2,free.halfWidth());assertEquals(1.2,free.halfHeight(),1e-6);assertEquals(start.lift(),free.lift());
 }
 @Test void everyFixedAxisEllipseStaysInsideTheOriginalOblateProjection(){
  // Independent boundary-point test against the inverse projected covariance.
  for(double thickness:new double[]{.05,.3,1})for(int tilt=0;tilt<=90;tilt+=10)for(int azimuth=0;azimuth<360;azimuth+=15){
   double t=Math.toRadians(tilt),z=Math.toRadians(azimuth);
   var n=new Vec3(Math.sin(t)*Math.cos(z),Math.sin(t)*Math.sin(z),Math.cos(t));
   var s=HullDustProjection.project(n,RIGHT,UP,2,thickness,1.2,0);
   assertEquals(0,s.roll());assertTrue(s.halfWidth()>0&&s.halfHeight()>0);
   double delta=4*(thickness*thickness-1),a=4+delta*n.x*n.x,b=4+delta*n.y*n.y,c=delta*n.x*n.y,det=a*b-c*c;
   for(int angle=0;angle<360;angle+=15){
    double r=Math.toRadians(angle),x=s.halfWidth()*Math.cos(r),y=s.halfHeight()*Math.sin(r);
    assertTrue((b*x*x-2*c*x*y+a*y*y)/det<=1.00001,"fixed footprint must not become a rotated bounding box");
   }
  }
 }
 @Test void crossingFormerAtanBranchNeverFlipsOrJumps(){
  HullDustProjection.Shape previous=null;
  for(int i=-100;i<=100;i++){
   double a=Math.PI+i*.0001;var n=new Vec3(Math.sin(a),-Math.cos(a),0);
   var s=HullDustProjection.project(n,RIGHT,UP,2,.3,1.2,0);assertEquals(0,s.roll());
   if(previous!=null){assertEquals(previous.halfWidth(),s.halfWidth(),.002);assertEquals(previous.halfHeight(),s.halfHeight(),.002);}
   previous=s;
  }
 }
 @Test void motionStrainIsSmallNonRotatingAndCannotEnlargeTheSupport(){
  for(int x=-32;x<=32;x++)for(int y=-32;y<=32;y++){
   var s=HullDustDeformation.apply(2,1,new Vec3(x,y,0),RIGHT,UP);
   assertTrue(s.width()>=1.83999&&s.width()<=2);assertTrue(s.height()>=.91999&&s.height()<=1);
  }
  var still=HullDustDeformation.apply(2,1,Vec3.ZERO,RIGHT,UP);assertEquals(2,still.width());assertEquals(1,still.height());
  var moving=HullDustDeformation.apply(2,1,RIGHT.scale(8),RIGHT,UP);assertEquals(2,moving.width());assertEquals(.92,moving.height(),1e-6);
 }
}
