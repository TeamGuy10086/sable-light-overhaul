package dev.sablelightoverhaul;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FlareFootprintTest {
 @Test void footprintTracksAltitudeBeyondTheOldSphericalLimit(){
  var low=AreaLight.axialFadeBeam(0,30,0,160,6);
  var high=AreaLight.axialFadeBeam(0,100,0,160,6);
  double tangent=Math.tan(Math.toRadians(75));
  assertEquals(3,low.at(15*tangent,0,0),1e-9);
  assertEquals(3,high.at(50*tangent,0,0),1e-9);
  assertEquals(0,low.at(150,0,0));
  assertTrue(high.at(200,0,0)>0,"high cone must not stop at a 160-block sphere");
  assertEquals(0,high.at(100*tangent+1,0,0));
  assertEquals(0,high.at(0,101,0));
 }
 @Test void axialEndFadeIsBoundedAndLeavesOrdinaryLightsUnchanged(){
  var f=AreaLight.axialFadeBeam(0,140,0,160,6);
  assertEquals(3,f.at(0,0,0),1e-9);
  assertEquals(-1,f.distanceKnee());
  assertEquals(0,AreaLight.axialFadeBeam(0,161,0,160,6).at(0,0,0));
  var normal=AreaLight.beam(0,140,0,0,-1,0,160,1,0,.5,.5,6);
  assertEquals(0,normal.distanceKnee());assertFalse(normal.axialEndFade());
  assertEquals(6,normal.at(0,0,0));
 }
}
