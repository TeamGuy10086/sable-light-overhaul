package dev.sablelightoverhaul;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** A separate channel preserves the existing big-shell protocol and identifies far lights as autocannon. */
public record AutocannonTrackPayload(ShellTrackPayload data,Set<Integer> flakIds) implements CustomPacketPayload {
 public static final Type<AutocannonTrackPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID,"autocannon_track"));
 public AutocannonTrackPayload(ShellTrackPayload data){this(data,Set.of());}
 public static final StreamCodec<RegistryFriendlyByteBuf,AutocannonTrackPayload> CODEC=StreamCodec.of(
  (buf,payload)->{
   ShellTrackPayload.CODEC.encode(buf,payload.data);
   for(var shell:payload.data.shells())buf.writeBoolean(payload.flakIds.contains(shell.id()));
  },
  buf->{
   var data=ShellTrackPayload.CODEC.decode(buf);
   var flakIds=new HashSet<Integer>();
   for(var shell:data.shells())if(buf.readBoolean())flakIds.add(shell.id());
   return new AutocannonTrackPayload(data,Set.copyOf(flakIds));
  });
 @Override public Type<AutocannonTrackPayload> type(){return TYPE;}
}
