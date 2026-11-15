package dev.sablelightoverhaul;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SmokeFlipbookLayoutTest {
 @Test void framesMatchShaderContiguousLayout(){
  var seen=new java.util.HashSet<Integer>();
  for(int v=0;v<6;v++)for(int f=0;f<32;f++){
   int i=SmokeFlipbookLayout.tileIndex(v,f);assertEquals(v*32+f,i);assertTrue(seen.add(i));
   assertTrue((i%8)*132+130<1056);assertTrue((i/8)*132+130<3168);
  }assertEquals(192,seen.size());assertEquals(1056,SmokeFlipbookLayout.WIDTH);assertEquals(3168,SmokeFlipbookLayout.HEIGHT);
 }
 @Test void animationClockRoundTripsAndClamps(){
  for(int f=0;f<32;f++)assertEquals(f,SmokeFlipbookLayout.frameAtAge(SmokeFlipbookLayout.ageAtFrame(f)),1e-10);
  assertEquals(15,SmokeFlipbookLayout.frameAtAge(1));assertEquals(31,SmokeFlipbookLayout.frameAtAge(8));
  assertEquals(0,SmokeFlipbookLayout.frameAtAge(Double.NaN));assertEquals(0,SmokeFlipbookLayout.frameAtAge(-2));
  assertEquals(31,SmokeFlipbookLayout.frameAtAge(Double.POSITIVE_INFINITY));
  for(int s=-64;s<64;s++)assertEquals(Math.floorMod(s,6),SmokeFlipbookLayout.variant(s));
  assertThrows(IllegalArgumentException.class,()->SmokeFlipbookLayout.tileIndex(6,0));
  assertThrows(IllegalArgumentException.class,()->SmokeFlipbookLayout.tileIndex(0,32));
  assertThrows(IllegalArgumentException.class,()->SmokeFlipbookLayout.ageAtFrame(-1));
 }
}
