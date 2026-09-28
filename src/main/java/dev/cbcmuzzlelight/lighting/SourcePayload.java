package dev.cbcmuzzlelight.lighting;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record SourcePayload(ResourceLocation dimension,List<State> states) implements CustomPacketPayload {
 /**
  * @param emission    block light level of a registered static source
  * @param intensity   spotlight brightness, already resolved from redstone or the page
  * @param widthSignal spotlight beam width, already resolved from redstone or the page
  * @param colorSignal spotlight colour input (side redstone) or the page's palette index, or the
  *                    static source's palette index
  * @param beamMode    spotlight falloff profile, 0..4
  * @param tilt        spotlight aim in degrees away from the block's own up; negative leans below it
  * @param azimuth     direction in the block's own horizontal plane that the tilt leans towards
  * @param radius      a static source's own light radius in blocks, or {@link RegisteredSources#RADIUS_AUTO}
  *                    when its radius follows its brightness. A spotlight's reach is its own
  *                    {@code distance}, so its radius is always 0 and never read.
  * @param shadowMode  a static source's own shadow answer: 0 none, 1 a hard edge, 2 a soft one. A wish,
  *                    applied only as far as the receiving client's own budget allows.
  * @param shadowSteps that source's own tap ceiling, or 0 for the client's. Only ever lowers it.
  */
 public record State(int id,SourceAnchor anchor,int mode,boolean spotlight,int emission,
                     int intensity,int widthSignal,int colorSignal,int distance,int beamMode,
                     float tilt,float azimuth,int radius,int shadowMode,int shadowSteps) {}
 public static final Type<SourcePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("cbcmuzzlelight","sources"));
 public static final StreamCodec<RegistryFriendlyByteBuf,SourcePayload> CODEC=StreamCodec.of((b,p)->{
  b.writeResourceLocation(p.dimension);b.writeVarInt(p.states.size());for(var s:p.states){b.writeVarInt(s.id);b.writeBoolean(s.anchor.ship()!=null);if(s.anchor.ship()!=null)b.writeUUID(s.anchor.ship());b.writeBlockPos(s.anchor.pos());b.writeByte(s.mode);b.writeBoolean(s.spotlight);b.writeByte(s.emission);b.writeByte(s.intensity);b.writeByte(s.widthSignal);b.writeByte(s.colorSignal);b.writeVarInt(s.distance);b.writeByte(s.beamMode);b.writeFloat(s.tilt);b.writeFloat(s.azimuth);b.writeByte(s.radius);b.writeByte(s.shadowMode);b.writeByte(s.shadowSteps);}
 },b->{var dim=b.readResourceLocation();int count=b.readVarInt();if(count<0||count>8192)throw new IllegalArgumentException("source count");var list=new ArrayList<State>();for(int i=0;i<count;i++){int id=b.readVarInt();var ship=b.readBoolean()?b.readUUID():null;var a=new SourceAnchor(ship,b.readBlockPos());list.add(new State(id,a,b.readUnsignedByte(),b.readBoolean(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte(),b.readVarInt(),b.readUnsignedByte(),b.readFloat(),b.readFloat(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte()));}return new SourcePayload(dim,List.copyOf(list));});
 @Override public Type<SourcePayload> type(){return TYPE;}
}
