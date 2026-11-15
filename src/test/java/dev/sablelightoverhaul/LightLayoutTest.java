package dev.sablelightoverhaul;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LightLayoutTest {
 @Test void fallbackStartsAtMuzzleAndHasBoundedDrag() {
  assertEquals(0,LightLayout.BIG.fallbackTravel(2));
  assertEquals(0.9,LightLayout.BIG.fallbackTravel(3),1e-9);
  assertTrue(LightLayout.BIG.fallbackTravel(10000)<=18.0001);
 }
 @Test void peakHoldDelaysDecayButNotExpiry() {
  var c=new LightCurve(10,0.2,0.1,4);
  assertEquals(1,c.brightness(4)); assertEquals(0.9,c.brightness(5),1e-9); assertEquals(0,c.brightness(10));
 }
 @Test void invalidTimingRejected() {
  assertThrows(IllegalArgumentException.class,()->new LightLayout(-1,0.9));
  assertThrows(IllegalArgumentException.class,()->new LightLayout(2,Double.NaN));
 }
}
