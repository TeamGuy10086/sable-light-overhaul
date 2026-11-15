package dev.sablelightoverhaul.lighting;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record SourcePayload(ResourceLocation dimension,List<State> states) implements CustomPacketPayload {
 /**
  * @param emission    block light level of a registered static source
  * @param intensity   spotlight brightness, already resolved from redstone or the page
  * @param coneTenths  a spotlight's cone opening in tenths of a degree, already resolved from the lamp's own
 *                    lower and upper limit and its 0..15 driver -- redstone, the page, or a channel. Tenths
 *                    rather than the driver, because the driver is a redstone strength and a player tuning
 *                    one lamp by hand needs finer steps than a fifteenth of the range.
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
  * @param cloudReach  a static source's own cloud reach in blocks, or
  *                    {@link RegisteredSources#CLOUD_REACH_GLOBAL} to leave it to the receiving client's
  *                    global setting. A spotlight's cloud participation is its own switch and its own
  *                    range, so its cloud reach is never read.
  * @param flood       whether this lamp is lit the way vanilla block light is instead of by this mod's
  *                    shaped term, and
  * @param faces       which of its six block faces it emits through, bit i for
  *                    {@link RegisteredSources#FACE_BITS}. Both are the lamp's own page; the shader is
  *                    the side that acts on them.
  */
 public record State(int id,SourceAnchor anchor,int mode,boolean spotlight,int emission,
                     int intensity,int coneTenths,int colorSignal,int distance,int beamMode,
                     float tilt,float azimuth,int radius,int shadowMode,int shadowSteps,int cloudReach,
                     boolean flood,int faces,int coneA,int coneB,int coneC,int coneD,
                     float offX,float offY,float offZ,String blink,int volumeMode,PointLightAppearance appearance) {
 public State(int id,SourceAnchor anchor,int mode,boolean spotlight,int emission,
                     int intensity,int coneTenths,int colorSignal,int distance,int beamMode,
                     float tilt,float azimuth,int radius,int shadowMode,int shadowSteps,int cloudReach,
                     boolean flood,int faces,int coneA,int coneB,int coneC,int coneD,
                     float offX,float offY,float offZ,String blink,int volumeMode){this(id,anchor,mode,spotlight,emission,intensity,coneTenths,colorSignal,distance,beamMode,tilt,azimuth,radius,shadowMode,shadowSteps,cloudReach,flood,faces,coneA,coneB,coneC,coneD,offX,offY,offZ,blink,volumeMode,PointLightAppearance.DEFAULT);}
 public State(int id,SourceAnchor anchor,int mode,boolean spotlight,int emission,
                     int intensity,int coneTenths,int colorSignal,int distance,int beamMode,
                     float tilt,float azimuth,int radius,int shadowMode,int shadowSteps,int cloudReach,
                     boolean flood,int faces,int coneA,int coneB,int coneC,int coneD,
                     float offX,float offY,float offZ,String blink){this(id,anchor,mode,spotlight,emission,intensity,coneTenths,colorSignal,distance,beamMode,tilt,azimuth,radius,shadowMode,shadowSteps,cloudReach,flood,faces,coneA,coneB,coneC,coneD,offX,offY,offZ,blink,spotlight?2:0);}
 }
 public static final Type<SourcePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("sablelightoverhaul","sources"));
 public static final StreamCodec<RegistryFriendlyByteBuf,SourcePayload> CODEC=StreamCodec.of((b,p)->{
  b.writeResourceLocation(p.dimension);b.writeVarInt(p.states.size());for(var s:p.states){b.writeVarInt(s.id);b.writeBoolean(s.anchor.ship()!=null);if(s.anchor.ship()!=null)b.writeUUID(s.anchor.ship());b.writeBlockPos(s.anchor.pos());b.writeByte(s.mode);b.writeBoolean(s.spotlight);b.writeByte(s.emission);b.writeByte(s.intensity);b.writeVarInt(s.coneTenths);b.writeVarInt(s.colorSignal);b.writeVarInt(s.distance);b.writeByte(s.beamMode);b.writeFloat(s.tilt);b.writeFloat(s.azimuth);b.writeByte(s.radius);b.writeByte(s.shadowMode);b.writeByte(s.shadowSteps);b.writeVarInt(s.cloudReach);b.writeBoolean(s.flood);b.writeByte(s.faces);b.writeVarInt(s.coneA);b.writeVarInt(s.coneB);b.writeVarInt(s.coneC);b.writeVarInt(s.coneD);b.writeFloat(s.offX);b.writeFloat(s.offY);b.writeFloat(s.offZ);b.writeUtf(s.blink,64);b.writeByte(s.volumeMode);s.appearance.write(b);}
 },b->{var dim=b.readResourceLocation();int count=b.readVarInt();if(count<0||count>8192)throw new IllegalArgumentException("source count");var list=new ArrayList<State>();for(int i=0;i<count;i++){int id=b.readVarInt();var ship=b.readBoolean()?b.readUUID():null;var a=new SourceAnchor(ship,b.readBlockPos());list.add(new State(id,a,b.readUnsignedByte(),b.readBoolean(),b.readUnsignedByte(),b.readUnsignedByte(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUnsignedByte(),b.readFloat(),b.readFloat(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte(),b.readVarInt(),b.readBoolean(),b.readUnsignedByte(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readFloat(),b.readFloat(),b.readFloat(),b.readUtf(64),b.readUnsignedByte(),PointLightAppearance.read(b)));}return new SourcePayload(dim,List.copyOf(list));});
 @Override public Type<SourcePayload> type(){return TYPE;}
}
