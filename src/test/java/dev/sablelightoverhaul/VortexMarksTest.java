package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import dev.sablelightoverhaul.lighting.VortexMarks;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * The registered-block set behind a vortex generator.
 *
 * <p>The shape is the point of these tests: a wing is not a box, so the set is arbitrary and the
 * edge of the set -- which is what the emitter draws from -- has to be worked out from the set
 * itself rather than from its bounding rectangle.
 */
class VortexMarksTest {
 @Test void addingAndRemovingTracksTheCount(){
  VortexMarks marks=new VortexMarks();
  assertTrue(marks.add(0,0,0));
  assertTrue(marks.add(1,0,0));
  assertFalse(marks.add(1,0,0),"the same block twice registers once");
  assertEquals(2,marks.count());
  assertTrue(marks.marked(1,0,0));
  assertFalse(marks.marked(1,1,0));
  assertTrue(marks.remove(1,0,0));
  assertFalse(marks.remove(1,0,0),"removing what is not there is not a change");
  assertEquals(1,marks.count());
  marks.clear();
  assertTrue(marks.empty());
  assertEquals(0,marks.volume());
 }
 @Test void theBoxGrowsAroundNegativeOffsets(){
  VortexMarks marks=new VortexMarks();
  assertTrue(marks.add(-3,-2,-1));
  assertTrue(marks.add(4,0,2));
  assertTrue(marks.marked(-3,-2,-1),"an offset left of the first one is still inside");
  assertTrue(marks.marked(4,0,2));
  assertFalse(marks.marked(0,1,0));
  assertEquals(2,marks.count());
  assertEquals(2,marks.offsets().size());
 }
 @Test void onlyTheOutlineOfASlabCountsAsEdge(){
  VortexMarks marks=new VortexMarks();
  for(int x=0;x<3;x++)for(int z=0;z<3;z++)marks.add(x,0,z);
  assertEquals(9,marks.count());
  assertFalse(marks.edge(1,0,1),"the middle of a slab has a neighbour on all four sides");
  // A vertical neighbour is deliberately not asked for: a wing is one block thick, so every one of
  // its blocks has air above it and testing that would call the whole wing an edge.
  assertTrue(marks.edge(0,0,1));
  assertTrue(marks.edge(1,0,0));
  assertEquals(8,marks.edgeCount());
  assertEquals(8,marks.edgeOffsets().size());
 }
 @Test void anIrregularShapeIsKeptAsItIs(){
  // An L-shaped wing: a 5x3 slab with a 2x2 bite out of one corner. A bounding box would have
  // claimed the bite back, and every reader of the set would then shed from blocks that are air.
  VortexMarks marks=new VortexMarks();
  for(int x=0;x<5;x++)for(int z=0;z<3;z++)marks.add(x,0,z);
  assertTrue(marks.remove(4,0,2));
  assertTrue(marks.remove(4,0,1));
  assertTrue(marks.remove(3,0,2));
  assertTrue(marks.remove(3,0,1));
  assertEquals(11,marks.count());
  assertFalse(marks.marked(4,0,2),"the bite is really gone");
  assertFalse(marks.edge(1,0,1),"the middle of what is left is still not an edge");
  assertTrue(marks.edge(2,0,1),"the block beside the bite became an edge");
  assertTrue(marks.edge(2,0,2),"and so did its neighbour along the bite");
 }
 @Test void aSelectionTooSpreadOutIsRefused(){
  VortexMarks marks=new VortexMarks();
  assertTrue(marks.add(0,0,0));
  int far=VortexMarks.MAX_VOLUME;
  assertFalse(marks.accepts(far,0,0));
  assertFalse(marks.add(far,0,0),"a box that wide is refused rather than grown into");
  assertEquals(1,marks.count());
 }
 @Test void saveAndLoadRoundTrips(){
  VortexMarks marks=new VortexMarks();
  marks.add(-2,1,3);
  marks.add(0,1,3);
  marks.add(5,-4,7);
  CompoundTag tag=marks.save();
  VortexMarks read=new VortexMarks();
  read.load(tag);
  assertEquals(3,read.count());
  assertTrue(read.marked(-2,1,3));
  assertTrue(read.marked(5,-4,7));
  assertFalse(read.marked(1,1,3));
  assertEquals(marks.edgeCount(),read.edgeCount());
  // Saving again has to produce the same thing: the layout is rebuilt from the offsets, not kept.
  assertEquals(tag.getLongArray("bits").length,read.save().getLongArray("bits").length);
 }
 @Test void theWireFormSurvivesTheArrays(){
  VortexMarks marks=new VortexMarks();
  marks.add(-1,2,-3);
  marks.add(4,2,-3);
  VortexMarks read=VortexMarks.fromArrays(marks.min(),marks.size(),marks.bits());
  assertEquals(marks.count(),read.count());
  assertTrue(read.marked(-1,2,-3));
  assertTrue(read.marked(4,2,-3));
  assertFalse(read.marked(0,2,-3));
  assertEquals(0,VortexMarks.fromArrays(new int[]{0,0},new int[]{1,1,1},new long[]{1L}).count(),
    "a corner that is not three numbers is not a box");
  assertEquals(0,VortexMarks.fromArrays(new int[]{0,0,0},new int[]{5,5,5},new long[]{1L}).count(),
    "bits that do not match the box are dropped, so a short packet cannot read as a full wing");
 }
 @Test void rubbishIsDroppedRatherThanTrusted(){
  VortexMarks marks=new VortexMarks();
  marks.add(0,0,0);
  marks.load(new CompoundTag());
  assertEquals(0,marks.count(),"an empty tag clears the set");
  CompoundTag wrongBits=new CompoundTag();
  wrongBits.putIntArray("min",new int[]{0,0,0});
  wrongBits.putIntArray("size",new int[]{4,4,4});
  wrongBits.putLongArray("bits",new long[]{1L,2L});
  marks.add(1,1,1);
  marks.load(wrongBits);
  assertEquals(0,marks.count(),"bits that do not match the box are dropped");
  CompoundTag tooBig=new CompoundTag();
  tooBig.putIntArray("min",new int[]{0,0,0});
  tooBig.putIntArray("size",new int[]{100,100,100});
  tooBig.putLongArray("bits",new long[]{1L});
  marks.add(2,2,2);
  marks.load(tooBig);
  assertEquals(0,marks.count(),"a box past the ceiling is dropped");
  CompoundTag counted=new CompoundTag();
  counted.putIntArray("min",new int[]{0,0,0});
  counted.putIntArray("size",new int[]{8,1,1});
  counted.putLongArray("bits",new long[]{3L});
  counted.putInt("count",99);
  marks.load(counted);
  assertEquals(2,marks.count(),"the count comes from the bits, not from the file");
 }
}
