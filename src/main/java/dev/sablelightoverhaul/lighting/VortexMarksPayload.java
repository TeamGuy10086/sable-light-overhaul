package dev.sablelightoverhaul.lighting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Which blocks each vortex generator is registered to, and which corner the marking player has dropped.
 *
 * <p>Sent to a player who is holding the marking wand, because the highlight is the whole point of
 * holding it: the marks are the server's, the drawing is the client's, and neither side has to guess
 * what the other knows. The generator's position travels in the body's plot space, and the client
 * puts it (and every offset) through the same pose it already uses to draw the vessel, so the marks
 * stay on the wing while the hull moves and turns.
 *
 * <p>The marks ride as the {@link VortexMarks} bit layout -- a corner, a size and the bits -- rather
 * than as a position list: a thousand-block wing is a couple of hundred bytes this way, and the shape
 * is arbitrary either way. The three arrays are written by hand rather than as an NBT compound, so
 * the wire format is in one readable place and needs no tag reader to be trusted.
 */
public record VortexMarksPayload(ResourceLocation dimension,List<Mark> marks,boolean hasCorner,
                                 UUID cornerShip,BlockPos cornerPos) implements CustomPacketPayload {
 /** @param generator the generator's block position in the body's plot space (what the pose takes) */
 public record Mark(UUID ship,BlockPos generator,int[] min,int[] size,long[] bits){}
 public static final Type<VortexMarksPayload> TYPE=
   new Type<>(ResourceLocation.fromNamespaceAndPath("sablelightoverhaul","vortex_marks"));
 private static final int MAX_MARKS=4096;
 private static final int MAX_WORDS=2048;
 public static final StreamCodec<RegistryFriendlyByteBuf,VortexMarksPayload> CODEC=StreamCodec.of((b,p)->{
  b.writeResourceLocation(p.dimension);
  b.writeVarInt(p.marks.size());
  for(Mark mark:p.marks){
   b.writeUUID(mark.ship());
   b.writeBlockPos(mark.generator());
   writeInts(b,mark.min());
   writeInts(b,mark.size());
   writeLongs(b,mark.bits());
  }
  b.writeBoolean(p.hasCorner);
  if(p.hasCorner){b.writeUUID(p.cornerShip);b.writeBlockPos(p.cornerPos);}
 },b->{
  ResourceLocation dimension=b.readResourceLocation();
  int count=b.readVarInt();
  if(count<0||count>MAX_MARKS)throw new IllegalArgumentException("vortex mark count "+count);
  List<Mark> marks=new ArrayList<>(count);
  for(int i=0;i<count;i++){
   UUID ship=b.readUUID();
   BlockPos generator=b.readBlockPos();
   marks.add(new Mark(ship,generator,readInts(b),readInts(b),readLongs(b)));
  }
  boolean corner=b.readBoolean();
  UUID cornerShip=corner?b.readUUID():null;
  BlockPos cornerPos=corner?b.readBlockPos():null;
  return new VortexMarksPayload(dimension,List.copyOf(marks),corner,cornerShip,cornerPos);
 });
 private static void writeInts(RegistryFriendlyByteBuf buffer,int[] values){
  buffer.writeVarInt(values.length);
  for(int value:values)buffer.writeVarInt(value);
 }
 private static int[] readInts(RegistryFriendlyByteBuf buffer){
  int length=buffer.readVarInt();
  if(length<0||length>4)throw new IllegalArgumentException("vortex int count "+length);
  int[] values=new int[length];
  for(int i=0;i<length;i++)values[i]=buffer.readVarInt();
  return values;
 }
 private static void writeLongs(RegistryFriendlyByteBuf buffer,long[] values){
  buffer.writeVarInt(values.length);
  for(long value:values)buffer.writeLong(value);
 }
 private static long[] readLongs(RegistryFriendlyByteBuf buffer){
  int length=buffer.readVarInt();
  if(length<0||length>MAX_WORDS)throw new IllegalArgumentException("vortex bit count "+length);
  long[] values=new long[length];
  for(int i=0;i<length;i++)values[i]=buffer.readLong();
  return values;
 }
 @Override public Type<VortexMarksPayload> type(){return TYPE;}
}
