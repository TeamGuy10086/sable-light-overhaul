package dev.cbcmuzzlelight;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LightLayoutTest {
 @Test void sparseLayoutHasRequestedSpanAndNoDirectionSingularity() {
  for(var axis:new double[][]{{1,0,0},{0,1,0},{0,-1,0},{1,2,3}}) {
   var l=new LightLayout(7,6,4,2,0.9); var o=l.offsets(axis[0],axis[1],axis[2]);
   assertEquals(7,o.size()); assertEquals(9,o.get(1).dot(o.get(1)),1e-9);
   assertEquals(4,o.get(3).dot(o.get(3)),1e-9);
   assertEquals(0,o.get(1).add(o.get(2)).dot(o.get(1).add(o.get(2))),1e-9);
   assertEquals(0,o.get(1).dot(o.get(3)),1e-9);
  }
 }
 @Test void fallbackStartsAtMuzzleAndHasBoundedDrag() {
  assertEquals(0,LightLayout.BIG.fallbackTravel(2));
  assertEquals(0.9,LightLayout.BIG.fallbackTravel(3),1e-9);
  assertTrue(LightLayout.BIG.fallbackTravel(10000)<=18.0001);
 }
 @Test void peakHoldDelaysDecayButNotExpiry() {
  var c=new LightCurve(10,0.2,0.1,4);
  assertEquals(1,c.brightness(4)); assertEquals(0.9,c.brightness(5),1e-9); assertEquals(0,c.brightness(10));
 }
 @Test void invalidBudgetsAndSizesRejected() {
  assertThrows(IllegalArgumentException.class,()->new LightLayout(8,6,4,2,0.9));
  assertThrows(IllegalArgumentException.class,()->new LightLayout(3,Double.NaN,4,2,0.9));
 }
}
