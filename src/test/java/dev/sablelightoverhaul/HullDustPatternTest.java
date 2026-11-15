package dev.sablelightoverhaul;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HullDustPatternTest {
 static final Vec3 UP=new Vec3(0,1,0),PX=new Vec3(1,0,0),NX=new Vec3(-1,0,0),SHIFT=new Vec3(300000,71,-250000);
 static List<CannonDustPayload.Surface> deck(Vec3 shift){
  var out=new ArrayList<CannonDustPayload.Surface>();
  for(int x=-5;x<=5;x++)for(int z=-2;z<=2;z++)out.add(new CannonDustPayload.Surface(new Vec3(x,1,z).add(shift),UP,new UUID(0,1),new Vec3(x,1,z),UP));
  for(int x=-5;x<=5;x++)for(int y=0;y<=3;y++)for(double s:new double[]{-2.1,2.1})out.add(new CannonDustPayload.Surface(new Vec3(x,y,s).add(shift),new Vec3(0,0,Math.signum(s)),new UUID(0,1),new Vec3(x,y,s),new Vec3(0,0,Math.signum(s))));
  return out;
 }
 static List<Vec3> locals(List<CannonDustPayload.Surface> rs){return rs.stream().map(CannonDustPayload.Surface::local).toList();}
 @Test void selectsForwardHalfWithRoofAndSide(){
  var r=HullDustPattern.select(deck(Vec3.ZERO),new Vec3(5,1,0),PX,.6,64);
  assertFalse(r.isEmpty());assertTrue(r.size()<=64);assertTrue(r.stream().anyMatch(s->s.normal().y>.55));assertTrue(r.stream().anyMatch(s->Math.abs(s.normal().z)>.9));
  long front=r.stream().filter(s->s.local().x>0).count(),rear=r.stream().filter(s->s.local().x<-1).count();
  assertTrue(front>r.size()/2,()->"front="+front+" of "+r.size());assertEquals(0,rear);
 }
 @Test void reversedAxisSelectsOppositeHalf(){
  var d=deck(Vec3.ZERO);
  var f=HullDustPattern.select(d,new Vec3(5,1,0),PX,.6,64);
  var b=HullDustPattern.select(d,new Vec3(5,1,0),NX,.6,64);
  assertFalse(b.isEmpty());assertEquals(0,b.stream().filter(s->s.local().x>1).count());
  assertTrue(b.stream().filter(s->s.local().x<0).count()>b.size()/2);
  assertTrue(f.stream().filter(s->s.local().x>0).count()>f.size()/2);
  assertNotEquals(new HashSet<>(locals(f)),new HashSet<>(locals(b)));
 }
 @Test void translatesWithWorldButKeepsLocalSources(){
  var a=HullDustPattern.select(deck(Vec3.ZERO),new Vec3(5,1,0),PX,.6,64);
  var b=HullDustPattern.select(deck(SHIFT),new Vec3(5,1,0).add(SHIFT),PX,.6,64);
  assertEquals(locals(a),locals(b));
  for(int i=0;i<b.size();i++)assertEquals(0,a.get(i).position().add(SHIFT).distanceToSqr(b.get(i).position()),1e-12);
 }
 @Test void shuffleIsStableAndBoundsHold(){
  var c=deck(Vec3.ZERO);var base=locals(HullDustPattern.select(c,new Vec3(5,1,0),PX,.6,64));
  var s=new ArrayList<>(c);Collections.shuffle(s,new Random(42));
  assertEquals(base,locals(HullDustPattern.select(s,new Vec3(5,1,0),PX,.6,64)));
  assertTrue(HullDustPattern.select(List.of(),new Vec3(5,1,0),PX,.6,64).isEmpty());
  assertEquals(3,HullDustPattern.select(c,new Vec3(5,1,0),PX,.6,3).size());
  assertEquals(0,HullDustPattern.select(c,new Vec3(5,1,0),PX,.6,0).size());
  assertEquals(64,HullDustPattern.select(c,new Vec3(5,1,0),PX,.6,500).size());
  assertThrows(IllegalArgumentException.class,()->HullDustPattern.select(c,new Vec3(5,1,0),PX,0,8));
 }
 @Test void rotatesWithTheVehicleAndUsesNoWorldNorthPreference(){
  var c=deck(Vec3.ZERO);
  var rotated=c.stream().map(s->new CannonDustPayload.Surface(turn(s.position()),turn(s.normal()),s.body(),s.local(),s.localNormal())).toList();
  assertEquals(locals(HullDustPattern.select(c,new Vec3(5,1,0),PX,.6,64)),locals(HullDustPattern.select(rotated,new Vec3(0,1,5),new Vec3(0,0,1),.6,64)));
 }
 static Vec3 turn(Vec3 p){return new Vec3(-p.z,p.y,p.x);}
 @Test void fullCoverageAndVerticalFireDoNotSilentlyExcludeTheRear(){
  var c=deck(Vec3.ZERO);
  for(var axis:List.of(PX,UP)){
   var selected=HullDustPattern.select(c,new Vec3(5,1,0),axis,axis==UP?.6:1,64);
   assertTrue(selected.stream().anyMatch(s->s.local().x<0));
   assertTrue(selected.stream().anyMatch(s->s.local().x>0));
  }
 }
 @Test void rearConnectedBodyGetsNoCompulsoryQuota(){
  var c=new ArrayList<>(deck(Vec3.ZERO));var rearId=new UUID(0,2);
  for(int x=-4;x<=-2;x++)for(int z=-1;z<=1;z++)c.add(new CannonDustPayload.Surface(new Vec3(x,2,z),UP,rearId,new Vec3(x,2,z),UP));
  var selected=HullDustPattern.select(c,new Vec3(5,1,0),PX,.6,64);
  assertFalse(selected.isEmpty());assertTrue(selected.stream().noneMatch(s->rearId.equals(s.body())));
 }
 @Test void longBarrelsDoNotReplaceHull(){
  var c=deck(Vec3.ZERO);
  for(double x:new double[]{40,80})c.add(new CannonDustPayload.Surface(new Vec3(x,1,0),UP,new UUID(0,1),new Vec3(x,1,0),UP));
  c.add(new CannonDustPayload.Surface(new Vec3(80,1,.1),UP,new UUID(0,1),new Vec3(80,1,.1),UP));
  var r=HullDustPattern.select(c,new Vec3(5,1,0),PX,.6,64);
  assertTrue(r.stream().filter(s->s.local().x<=5).count()>r.size()/2);
  assertTrue(r.stream().anyMatch(s->s.normal().y>.55));assertTrue(r.stream().anyMatch(s->Math.abs(s.normal().z)>.9));
  long roofHull=r.stream().filter(s->s.normal().y>.55&&s.local().x>-2).count();
  assertTrue(roofHull>0);assertTrue(r.stream().filter(s->s.local().x>40).count()<roofHull);
 }
}
