package dev.cbcmuzzlelight;
import dev.cbcmuzzlelight.client.AreaSnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SourceSelfLightingTest {
 @Test void aStaticSourceCanBeMarkedAsNotLightingItsOwnBlock(){
  var light=new AreaLight(0,0,0,1,0,0,10,10,0,15);
  var plain=new AreaSnapshot(light,AreaSnapshot.bounds(light),null);
  var culled=new AreaSnapshot(light,AreaSnapshot.bounds(light),null,0,true);
  assertFalse(plain.selfCull,"muzzle flashes and tracers still light their own volume");
  assertTrue(culled.selfCull);
  assertFalse(plain.same(culled),"turning the cull on must count as a change, or a rebuild is skipped");
  assertTrue(culled.same(new AreaSnapshot(light,AreaSnapshot.bounds(light),null,0,true)));
 }
 @Test void theCullFlagTravelsWithTheColourIndex(){
  var light=new AreaLight(0,0,0,1,0,0,10,10,0,15);
  var tinted=new AreaSnapshot(light,AreaSnapshot.bounds(light),null,14,true);
  assertEquals(14,tinted.color);
  assertTrue(tinted.selfCull);
 }
}
