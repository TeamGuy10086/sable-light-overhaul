package dev.cbcmuzzlelight;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FlashStoreTest {
 @Test void alternatingTwinMuzzlesInSameVoxelKeepIndependentLifetimes() {
  var s=new FlashStore(64);
  s.add(0.25,65,0.5,0,0,1,1,10);
  long left=s.snapshot().getFirst().token();
  s.add(0.75,65,0.5,0,0,1,1,11);
  assertEquals(2,s.snapshot().size());
  s.add(0.25,65,0.5,0,0,1,1,12);
  assertEquals(2,s.snapshot().size()); assertNull(s.find(left));
  s.expire(15); assertEquals(1,s.snapshot().size());
  assertEquals(0.25,s.snapshot().getFirst().x());
 }
 @Test void samePreciseAutoMuzzleRefreshesAcrossVoxelBoundary() {
  var s=new FlashStore(64);
  s.add(-0.001,65,0,1,0,0,1,10); s.add(0.001,65,0,1,0,0,1,11);
  assertEquals(1,s.snapshot().size());
 }
 private static final LightLayout SINGLE=new LightLayout(1,0,0,2,0.9);
 private FlashStore shot() {
  var s=new FlashStore(64); s.add(0,65,0,1,0,0,0,10,new LightCurve(100,0.4,0.1,4),SINGLE); return s;
 }
 private long bind(FlashStore s) { return s.attachBigPlume(0,65,0,1,0,0,10); }
 @Test void tracksActualCloudBeyondSixTicksAndFourBlocksWithoutFixedMuzzle() {
  var s=shot(); long t=bind(s); s.followHotCloud(t,20,67,3,25,true);
  var p=s.samples(25); assertEquals(1,p.size()); assertEquals(20,p.getFirst().x()); assertEquals(67,p.getFirst().y());
  s.followHotCloud(t,25,68,5,26,true); assertEquals(25,s.samples(26).getFirst().x());
 }
 @Test void coldAndStaleCloudsDoNotLeaveFrozenLight() {
  var s=shot(); long t=bind(s); s.followHotCloud(t,10,65,0,20,true);
  assertFalse(s.samples(22).isEmpty()); assertTrue(s.samples(23).isEmpty());
  s.followHotCloud(t,10,65,0,24,false); assertTrue(s.samples(24).isEmpty());
 }
 @Test void fallbackMovesWithoutSmokeAndExpires() {
  var s=shot(); assertEquals(0,s.samples(10).getFirst().x());
  assertTrue(s.samples(20).getFirst().x()>s.samples(15).getFirst().x());
  assertTrue(s.samples(110).isEmpty());
 }
 @Test void shortMuzzleHoldTransfersEntireLightToCloud() {
  var s=shot(); long t=bind(s); s.followHotCloud(t,4,65,0,11,true);
  assertEquals(0,s.samples(11).getFirst().x()); assertEquals(4,s.samples(12).getFirst().x());
 }
 @Test void runawayPositionsSwitchOffRatherThanPinning() {
  var s=shot(); long t=bind(s); s.followHotCloud(t,1000,65,0,20,true); assertTrue(s.samples(20).isEmpty());
 }
 @Test void bindingRequiresNearbyRecentMatchingBigShotAndIsUnique() {
  var s=shot(); assertEquals(0,s.attachBigPlume(0,65,0,-1,0,0,10));
  assertEquals(0,s.attachBigPlume(20,65,0,1,0,0,10));
  assertEquals(0,s.attachBigPlume(0,65,0,1,0,0,15));
  assertNotEquals(0,bind(s)); assertEquals(0,bind(s));
 }
 @Test void autoPeakIsFifteenAndLayoutRemainsAheadOfMuzzle() {
  var s=new FlashStore(64); s.add(0,65,0,1,0,0,1,10,new LightCurve(5,0,0.25,2),LightLayout.AUTO);
  assertEquals(3,s.samples(10).size()); assertTrue(s.samples(10).stream().allMatch(p -> p.level()==15 && p.x()>=0));
  assertEquals(15,s.samples(12).getFirst().level()); assertTrue(s.samples(15).isEmpty());
 }
 @Test void budgetsAndRepeatedFireRemainBounded() {
  var s=new FlashStore(2);
  for(int i=0;i<1000;i++) s.add(i,65,0,1,0,0,1,10);
  assertEquals(2,s.snapshot().size()); assertTrue(s.samples(10).size()<=14);
  assertEquals(2,s.nearest(0,0,0,2,10).size());
  s.add(999.1,65,0,1,0,0,1,11); assertEquals(2,s.snapshot().size());
 }
 @Test void oldWorldTokensCannotMoveNewShot() {
  var s=shot(); long old=bind(s); s.clear(); s.add(0,65,0,1,0,0,0,10);
  long current=bind(s); assertNotEquals(old,current);
  s.followHotCloud(old,20,65,0,12,true); assertEquals(Long.MIN_VALUE,s.find(current).trackedAt());
 }
 @Test void malformedShotsAndMortarsAreRejected() {
  var s=new FlashStore(2);
  assertFalse(s.add(0,0,0,0,0,0,0,0)); assertFalse(s.add(Double.NaN,0,0,1,0,0,0,0));
  assertFalse(s.add(0,0,0,1,0,0,3,0));
  // The three barrel kinds are accepted: big cannon (0), autocannon (1) and tank cannon (2).
  assertTrue(s.add(0,0,0,1,0,0,2,0)); assertFalse(s.add(0,0,0,Double.NaN,0,0,0,0));
 }
 @Test void floorAndConfigSnapshotRespectExpiry() {
  var s=shot(); long t=bind(s); s.followHotCloud(t,5,65,0,109,true);
  assertEquals(6,s.samples(109).getFirst().level()); assertTrue(s.samples(110).isEmpty());
  s.add(6,65,0,1,0,0,0,30,new LightCurve(2,1,0));
  assertEquals(110,s.snapshot().getFirst().expires()); assertEquals(32,s.snapshot().getLast().expires());
 }
}
