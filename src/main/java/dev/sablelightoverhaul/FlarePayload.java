package dev.sablelightoverhaul;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** Full snapshot of a dimension's debug flares, server to client. */
public record FlarePayload(ResourceLocation dimension,List<State> states) implements CustomPacketPayload {
 public static final int MAX_FLARES=16;
 private static final double MAX_POS=30000000.0;
 /** @param bornTick server game time the flare was spawned, used by the client to age it. */
 public record State(int id,boolean illumination,double x,double y,double z,long bornTick,int durationTicks,int rgb,float brightness,int angleDegrees) {
  public State(int id,boolean illumination,double x,double y,double z,long bornTick,int durationTicks,int rgb,float brightness){this(id,illumination,x,y,z,bornTick,durationTicks,rgb,brightness,360);}
  public State(int id,boolean illumination,double x,double y,double z,long bornTick,int durationTicks,int rgb){this(id,illumination,x,y,z,bornTick,durationTicks,rgb,illumination?6F:15F);}
  public State{
   if(angleDegrees<1||angleDegrees>360)throw new IllegalArgumentException("flare angle");
   if(!Float.isFinite(brightness)||brightness<0||brightness>15)throw new IllegalArgumentException("flare brightness");
   if(durationTicks<2||durationTicks>72000)throw new IllegalArgumentException("flare duration");
   if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>MAX_POS||Math.abs(y)>MAX_POS||Math.abs(z)>MAX_POS)throw new IllegalArgumentException("flare position");
   if(rgb<0||rgb>0xffffff)throw new IllegalArgumentException("flare rgb");
  }
 }
 public static final Type<FlarePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("sablelightoverhaul","debug_flares"));
 public static final StreamCodec<RegistryFriendlyByteBuf,FlarePayload> CODEC=StreamCodec.of((b,p)->{
  b.writeResourceLocation(p.dimension);b.writeVarInt(p.states.size());for(var s:p.states){b.writeVarInt(s.id);b.writeBoolean(s.illumination);b.writeDouble(s.x);b.writeDouble(s.y);b.writeDouble(s.z);b.writeVarLong(s.bornTick);b.writeVarInt(s.durationTicks);b.writeInt(s.rgb);b.writeFloat(s.brightness);b.writeVarInt(s.angleDegrees);}
 },b->{var dim=b.readResourceLocation();int count=b.readVarInt();if(count<0||count>MAX_FLARES)throw new IllegalArgumentException("flare count");var list=new ArrayList<State>(count);for(int i=0;i<count;i++)list.add(new State(b.readVarInt(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble(),b.readVarLong(),b.readVarInt(),b.readInt(),b.readFloat(),b.readVarInt()));return new FlarePayload(dim,List.copyOf(list));});
 public FlarePayload{dimension=Objects.requireNonNull(dimension,"dimension");states=List.copyOf(Objects.requireNonNull(states,"states"));if(states.size()>MAX_FLARES)throw new IllegalArgumentException("flare count");}
 @Override public Type<FlarePayload> type(){return TYPE;}
}
