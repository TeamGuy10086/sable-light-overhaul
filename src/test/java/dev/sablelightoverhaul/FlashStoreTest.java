package dev.sablelightoverhaul;
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
 private static final LightLayout SINGLE=new LightLayout(2,0.9);
 private FlashStore shot() {
  var s=new FlashStore(64); s.add(0,65,0,1,0,0,0,10,new LightCurve(100,0.4,0.1,4),SINGLE); return s;
 }
 private long bind(FlashStore s) { return s.attachBigPlume(0,65,0,1,0,0,10); }
 @Test void tracksActualCloudBeyondSixTicksAndFourBlocksWithoutFixedMuzzle() {
  var s=shot(); long t=bind(s); s.followHotCloud(t,20,67,3,25,true);
  var p=s.find(t); assertEquals(20,p.tipX()); assertEquals(67,p.tipY()); assertTrue(p.hot());
  s.followHotCloud(t,25,68,5,26,true); assertEquals(25,s.find(t).tipX());
 }
 @Test void runawayPositionsSwitchOffRatherThanPinning() {
  var s=shot(); long t=bind(s); s.followHotCloud(t,1000,65,0,20,true); assertFalse(s.find(t).hot());
 }
 @Test void bindingRequiresNearbyRecentMatchingBigShotAndIsUnique() {
  var s=shot(); assertEquals(0,s.attachBigPlume(0,65,0,-1,0,0,10));
  assertEquals(0,s.attachBigPlume(20,65,0,1,0,0,10));
  assertEquals(0,s.attachBigPlume(0,65,0,1,0,0,15));
  assertNotEquals(0,bind(s)); assertEquals(0,bind(s));
 }
 /** The plume generator is attributed to the shot event, not to whichever contraption happens to be nearest. */
 @Test void plumeKindComesFromTheRecentShot() {
  var s=new FlashStore(64);
  s.add(0,65,0,1,0,0,2,10);   // tank cannon
  s.add(12,65,0,1,0,0,0,10);  // big cannon, far enough to be a different shot
  assertEquals(2,s.plumeKind(1.6,65,0,1,0,0,10),"the generator just past the tank muzzle is tank");
  assertEquals(0,s.plumeKind(13.6,65,0,1,0,0,10),"the one past the big muzzle is big");
  assertEquals(-1,s.plumeKind(1.6,65,0,-1,0,0,10),"a shot pointing the other way is not a match");
  assertEquals(-1,s.plumeKind(1.6,65,0,1,0,0,15),"five ticks later is a different event");
  assertEquals(-1,s.plumeKind(30,65,0,1,0,0,10),"out of range");
 }
 /** No flash (switch off / culled) is a legal answer: the caller falls back to the contraption. */
 @Test void plumeKindSaysUnknownWhenNoShotWasSent() {
  assertEquals(-1,new FlashStore(8).plumeKind(0,65,0,1,0,0,10));
 }
 @Test void budgetsAndRepeatedFireRemainBounded() {
  var s=new FlashStore(2);
  for(int i=0;i<1000;i++) s.add(i,65,0,1,0,0,1,10);
  assertEquals(2,s.snapshot().size()); assertEquals(2,s.areaFlashes().size());
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
  assertFalse(s.add(0,0,0,1,0,0,4,0));
  // The four barrel kinds are accepted: big cannon (0), autocannon (1), tank cannon (2) and twin autocannon (3).
  assertTrue(s.add(0,0,0,1,0,0,2,0)); assertTrue(s.add(0,0,0,1,0,0,3,0)); assertFalse(s.add(0,0,0,Double.NaN,0,0,0,0));
 }
 @Test void floorAndConfigSnapshotRespectExpiry() {
  var s=shot(); long t=bind(s); s.followHotCloud(t,5,65,0,109,true);
  assertEquals(0.4,s.find(t).curve().brightness(99),1e-9);
  assertEquals(0,s.find(t).curve().brightness(100));
  s.add(6,65,0,1,0,0,0,30,new LightCurve(2,1,0));
  assertEquals(110,s.snapshot().getFirst().expires()); assertEquals(32,s.snapshot().getLast().expires());
 }
}
