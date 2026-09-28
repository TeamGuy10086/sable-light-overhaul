package dev.cbcmuzzlelight;
import dev.cbcmuzzlelight.client.*;
import net.minecraft.core.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class LightStabilityTest {
 @Test void lateDarkBuildCannotOverwriteNewBrightBuild(){
  var order=new BuildOrder();long dark=order.request(),bright=order.request();int[] value={-1};
  assertTrue(order.commit(bright,()->value[0]=15));assertFalse(order.commit(dark,()->value[0]=0));assertEquals(15,value[0]);assertTrue(order.obsolete(dark));
 }
 @Test void lateBrightBuildCannotResurrectExtinguishedLight(){
  var order=new BuildOrder();long bright=order.request(),dark=order.request();int[] value={-1};
  assertTrue(order.commit(dark,()->value[0]=0));assertFalse(order.commit(bright,()->value[0]=15));assertEquals(0,value[0]);
 }
 @Test void pendingNewerRequestDoesNotStarveInFlightResult(){
  var order=new BuildOrder();long first=order.request();order.request();assertTrue(order.commit(first,()->{}));
 }
 @Test void outOfOrderWorkersCommitMonotonically() throws Exception {
  var order=new BuildOrder();long first=order.request(),second=order.request();var done=new CountDownLatch(1);int[] value={0};
  try(var workers=Executors.newFixedThreadPool(2)){
   var old=workers.submit(()->{try{assertTrue(done.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}return order.commit(first,()->value[0]=1);});
   var recent=workers.submit(()->{order.commit(second,()->value[0]=2);done.countDown();});recent.get(5,TimeUnit.SECONDS);assertFalse(old.get(5,TimeUnit.SECONDS));assertEquals(2,value[0]);
  }
 }
 @Test void fourNeighborSectionsReadOnePinnedGenerationWithNestedCleanup(){
  var light=new AreaLight(16,64,16,1,0,0,60,60,.9,15,false,.95,.1);
  var area=new AreaSnapshot(light,AreaSnapshot.bounds(light),null);var map=new HashMap<Long,List<AreaSnapshot>>();
  for(int x=0;x<2;x++)for(int z=0;z<2;z++)map.put(SectionPos.asLong(x,4,z),List.of(area));
  try(var scope=LightReadScope.enter(Map.copyOf(map))){
   for(int x:new int[]{8,24})for(int z:new int[]{8,24})assertEquals(15,AdvancedLights.lightAt(new BlockPos(x,64,z)));
   try(var nested=LightReadScope.enter(Map.of())){assertEquals(0,AdvancedLights.lightAt(new BlockPos(8,64,8)));}
   assertEquals(15,AdvancedLights.lightAt(new BlockPos(8,64,8)));
  }assertFalse(LightReadScope.active());
 }
 @Test void scopeDoesNotLeakAfterFailure(){
  assertThrows(IllegalStateException.class,()->{try(var ignored=LightReadScope.enter(Map.of())){throw new IllegalStateException();}});assertFalse(LightReadScope.active());
 }
}
