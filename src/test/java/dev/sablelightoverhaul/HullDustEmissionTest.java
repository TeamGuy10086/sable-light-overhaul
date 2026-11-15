package dev.sablelightoverhaul;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HullDustEmissionTest {
 @Test void densityChangesQuantityWithoutChangingHardBudget(){assertEquals(80,HullDustEmission.count(40,2));assertEquals(400,HullDustEmission.count(200,2));assertEquals(512,HullDustEmission.count(200,8));assertEquals(0,HullDustEmission.count(200,0));}
 @Test void firstPassKeepsAllSourcesThenDistributesSiblings(){
  for(int i=0;i<80;i++){var s=HullDustEmission.sample(i,40,13,new Vec3(0,1,0),.3,.1);assertEquals(i%40,s.source());if(i<40){assertEquals(Vec3.ZERO,s.offset());assertEquals(0,s.delay());}else{assertTrue(s.offset().length()>.09&&s.offset().length()<=.3);assertTrue(s.delay()>0&&s.delay()<=.1);}}
 }
 @Test void samplesAreDeterministicAndTangentOnBothRoofAndSkirt(){
  for(var n:java.util.List.of(new Vec3(0,1,0),new Vec3(1,0,0),new Vec3(.3,.8,.2).normalize())){
   var offsets=new java.util.HashSet<Vec3>();
   for(int i=64;i<400;i++){var a=HullDustEmission.sample(i,64,77,n,.3,.1);assertEquals(a,HullDustEmission.sample(i,64,77,n,.3,.1));assertEquals(0,a.offset().dot(n),1e-10);assertTrue(offsets.add(a.offset()));}
  }
 }
 @Test void zeroSpreadAndStaggerProvideUsefulSamePointControl(){var s=HullDustEmission.sample(70,64,4,new Vec3(0,1,0),0,0);assertEquals(0,s.offset().length());assertEquals(0,s.delay());}
}
