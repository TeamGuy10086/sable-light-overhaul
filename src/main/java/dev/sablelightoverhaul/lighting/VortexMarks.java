package dev.sablelightoverhaul.lighting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * The blocks one vortex generator is registered to, held as offsets from the generator's own block.
 *
 * <p><b>The shape is not assumed.</b> A wing is not a quadrilateral, and it is not a box: the marked
 * set is an arbitrary subset of the offsets, so a slab selection, a handful of single clicks and a
 * few erasures add up to whatever the wing actually is. Everything that reads this class has to cope
 * with that -- the highlight draws one box per marked block rather than one box around them all, and
 * the emitter picks its blocks from the set's own boundary instead of from a bounding rectangle.
 *
 * <p>Offsets are in the body's plot space and relative to the generator, which is what makes the
 * marks follow the vessel: the generator resolves to a world position through its body's pose, and
 * every mark is that position plus its own offset. Storing world positions instead would need the
 * whole set re-sent every time the hull moved.
 *
 * <p>Stored as a bit per offset over the set's bounding box, so a thousand-block selection is a
 * hundred-odd bytes on the wire and on disk. Marks are contiguous in practice (a wing is one piece),
 * which is what keeps that box small; a selection spread wider than {@link #MAX_VOLUME} is refused
 * with a message rather than silently growing into a wasteful box.
 */
public final class VortexMarks {
 /** Bit ceiling: a 64x64x32 box. A wing is thousands of blocks, so this is far above any real use. */
 public static final int MAX_VOLUME=131072;
 private static final String MIN="min",SIZE="size",BITS="bits",COUNT="count";
 private int minX,minY,minZ;
 private int sizeX,sizeY,sizeZ;
 private long[] bits=new long[0];
 private int count;
 /** How many blocks are registered. */
 public int count(){return count;}
 public boolean empty(){return count==0;}
 /** Size of the bounding box the bits are laid out over, in blocks. */
 public int volume(){return sizeX*sizeY*sizeZ;}
 /** Whether this offset is registered. */
 public boolean marked(int x,int y,int z){
  if(!inside(x,y,z))return false;
  int index=index(x,y,z);
  return (bits[index>>>6]&(1L<<(index&63)))!=0;
 }
 public boolean marked(BlockPos offset){return marked(offset.getX(),offset.getY(),offset.getZ());}
 /**
   * A block on the edge of the marked set, which is where a vortex comes off.
   *
   * <p>Only the four horizontal neighbours are asked. A wing is a slab, so a top or bottom neighbour
   * is unmarked almost everywhere and testing those would call the whole wing an edge; the wing's own
   * outline is what is left when the search is kept to the plane the wing lies in.
   */
 public boolean edge(int x,int y,int z){
  if(!marked(x,y,z))return false;
  return !marked(x-1,y,z)||!marked(x+1,y,z)||!marked(x,y,z-1)||!marked(x,y,z+1);
 }
 /** @returns whether this offset was newly registered; false when it was already there or refused. */
 public boolean add(int x,int y,int z){
  if(marked(x,y,z))return false;
  if(!inside(x,y,z)&&!grow(x,y,z))return false;
  set(x,y,z);
  count++;
  return true;
 }
 /** Take one block back out. Removing something that was never there is not an error. */
 public boolean remove(int x,int y,int z){
  if(!marked(x,y,z))return false;
  int index=index(x,y,z);
  bits[index>>>6]&=~(1L<<(index&63));
  count--;
  return true;
 }
 public void clear(){
  minX=minY=minZ=sizeX=sizeY=sizeZ=count=0;
  bits=new long[0];
 }
 /** Every registered offset, in no particular order. A fresh list, safe to keep. */
 public List<BlockPos> offsets(){
  List<BlockPos> result=new ArrayList<>(count);
  forEach((x,y,z)->result.add(new BlockPos(x,y,z)));
  return result;
 }
 /** How many of the offsets are on the set's edge, without listing them. */
 public int edgeCount(){
  int[] found=new int[1];
  forEachEdge((x,y,z)->found[0]++);
  return found[0];
 }
 /** The edge offsets only, which is what the emitter draws from. */
 public List<BlockPos> edgeOffsets(){
  List<BlockPos> result=new ArrayList<>();
  forEachEdge((x,y,z)->result.add(new BlockPos(x,y,z)));
  return result;
 }
 /** Called once per registered offset. Allocating nothing, so a per-frame reader can use it. */
 public interface Visitor{void visit(int x,int y,int z);}
 public void forEach(Visitor visitor){
  for(int z=0;z<sizeZ;z++)for(int y=0;y<sizeY;y++){
   for(int x=0;x<sizeX;x++){
    int index=x+sizeX*(y+sizeY*z);
    if((bits[index>>>6]&(1L<<(index&63)))==0)continue;
    visitor.visit(minX+x,minY+y,minZ+z);
   }
  }
 }
 /** Called once per offset on the set's edge. */
 public void forEachEdge(Visitor visitor){
  forEach((x,y,z)->{if(edge(x,y,z))visitor.visit(x,y,z);});
 }
 private boolean inside(int x,int y,int z){
  return sizeX>0&&sizeY>0&&sizeZ>0
    &&x>=minX&&x<minX+sizeX&&y>=minY&&y<minY+sizeY&&z>=minZ&&z<minZ+sizeZ;
 }
 private int index(int x,int y,int z){return (x-minX)+sizeX*((y-minY)+sizeY*(z-minZ));}
 private void set(int x,int y,int z){
  int index=index(x,y,z);
  bits[index>>>6]|=1L<<(index&63);
 }
 /** Widen the bounding box to take in this offset; false when that would pass the ceiling. */
 private boolean grow(int x,int y,int z){
  boolean first=count==0&&bits.length==0;
  int x0=first?x:Math.min(minX,x),y0=first?y:Math.min(minY,y),z0=first?z:Math.min(minZ,z);
  int x1=first?x:Math.max(minX+sizeX-1,x),y1=first?y:Math.max(minY+sizeY-1,y),z1=first?z:Math.max(minZ+sizeZ-1,z);
  long sx=(long)x1-x0+1,sy=(long)y1-y0+1,sz=(long)z1-z0+1;
  if(sx*sy*sz>MAX_VOLUME)return false;
  long[] next=new long[(int)((sx*sy*sz+63L)/64L)];
  if(!first){
   // Copy the old bits into the new layout. Bit by bit is enough: this runs when the box grows,
   // which is a handful of times per selection, not once per block.
   for(int zz=0;zz<sizeZ;zz++)for(int yy=0;yy<sizeY;yy++)for(int xx=0;xx<sizeX;xx++){
    if(!marked(minX+xx,minY+yy,minZ+zz))continue;
    int nx=(minX+xx)-x0,ny=(minY+yy)-y0,nz=(minZ+zz)-z0;
    int index=nx+(int)sx*(ny+(int)sy*nz);
    next[index>>>6]|=1L<<(index&63);
   }
  }
  minX=x0;minY=y0;minZ=z0;
  sizeX=(int)sx;sizeY=(int)sy;sizeZ=(int)sz;
  bits=next;
  return true;
 }
 /** Whether adding this offset would be accepted, without changing anything. */
 public boolean accepts(int x,int y,int z){
  if(marked(x,y,z))return true;
  if(inside(x,y,z))return true;
  boolean first=count==0&&bits.length==0;
  int x0=first?x:Math.min(minX,x),y0=first?y:Math.min(minY,y),z0=first?z:Math.min(minZ,z);
  int x1=first?x:Math.max(minX+sizeX-1,x),y1=first?y:Math.max(minY+sizeY-1,y),z1=first?z:Math.max(minZ+sizeZ-1,z);
  return (long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)<=MAX_VOLUME;
 }
 public CompoundTag save(){
  CompoundTag tag=new CompoundTag();
  tag.putIntArray(MIN,new int[]{minX,minY,minZ});
  tag.putIntArray(SIZE,new int[]{sizeX,sizeY,sizeZ});
  tag.putLongArray(BITS,bits);
  tag.putInt(COUNT,count);
  return tag;
 }
 /** Reads back what {@link #save} wrote. Anything inconsistent is dropped, not trusted. */
 public void load(CompoundTag tag){
  accept(tag.getIntArray(MIN),tag.getIntArray(SIZE),tag.getLongArray(BITS));
 }
 /** The box's own corner, as {@code {x,y,z}}; this is what travels on the wire. */
 public int[] min(){return new int[]{minX,minY,minZ};}
 /** The box's size, as {@code {x,y,z}}. */
 public int[] size(){return new int[]{sizeX,sizeY,sizeZ};}
 /** The bits, copied so a caller cannot write into the set through them. */
 public long[] bits(){return bits.clone();}
 /** Rebuild from the three arrays; anything inconsistent yields an empty set, never a wrong one. */
 public static VortexMarks fromArrays(int[] min,int[] size,long[] bits){
  VortexMarks marks=new VortexMarks();
  marks.accept(min,size,bits);
  return marks;
 }
 /** The one place the three arrays are believed, used by both the file and the wire. */
 private void accept(int[] min,int[] size,long[] stored){
  clear();
  if(min==null||size==null||stored==null||min.length!=3||size.length!=3)return;
  long sx=size[0],sy=size[1],sz=size[2];
  if(sx<0||sy<0||sz<0||sx*sy*sz>MAX_VOLUME)return;
  int volume=(int)(sx*sy*sz);
  if(stored.length!=(volume+63)/64)return;
  minX=min[0];minY=min[1];minZ=min[2];
  sizeX=(int)sx;sizeY=(int)sy;sizeZ=(int)sz;
  bits=stored.clone();
  // The count is recomputed rather than trusted: a wrong count would show up as a wrong total in
  // every message and as a wrong budget in the emitter, and the bits are the truth.
  count=0;
  for(long word:bits)count+=Long.bitCount(word);
 }
}
