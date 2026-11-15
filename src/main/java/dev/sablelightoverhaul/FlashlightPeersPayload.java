package dev.sablelightoverhaul;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The flashlights of the other players near you, so their beams are drawn on your machine too.
 *
 * <p>This is the same split the trail and the shockwave use: the server decides and sends numbers, the
 * client draws. What travels is only what a client cannot work out for itself. Everything else is already
 * there — another player's position and look direction arrive with the entity, and whether they are
 * holding the lamp arrives with their equipment. What is <em>not</em> derivable is the settings, because
 * those are operator-gated and stored on the server, and the on/off switch, because it is the server's
 * record.
 *
 * <p>Only lights that are actually on, held, and inside the configured range are listed, and the list
 * replaces the previous one wholesale, so a peer who switches off or walks away simply stops being in it.
 * A peer the client is not tracking cannot be placed, which is why the range defaults to 48: that is the
 * vanilla player tracking range, and asking for more would send lights nobody could position.
 *
 * <p>A peer carries the numbers its beam needs, not the page's distance step: like the player's own state,
 * the length in blocks has to be the server's.
 */
public record FlashlightPeersPayload(List<Peer> peers) implements CustomPacketPayload {
 /** One other player's lamp. {@code id} is their UUID, the only handle both sides share. */
 public record Peer(UUID id,int color,int rangeBlocks,int width,int decay,int falloff,
                    int intensity,int pointRadius,int pointIntensity,int volumeMode) {
 public Peer(UUID id,int color,int rangeBlocks,int width,int decay,int falloff,
                    int intensity,int pointRadius,int pointIntensity){this(id,color,rangeBlocks,width,decay,falloff,intensity,pointRadius,pointIntensity,1);}
 }

 public static final Type<FlashlightPeersPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID,"flashlight_peers"));
 public static final StreamCodec<RegistryFriendlyByteBuf,FlashlightPeersPayload> CODEC=StreamCodec.of((b,value)->{
  b.writeVarInt(value.peers.size());
  for(Peer p:value.peers){
   b.writeUUID(p.id());
   b.writeInt(p.color());b.writeShort(p.rangeBlocks());b.writeByte(p.width());
   b.writeShort(p.decay());b.writeByte(p.falloff());b.writeByte(p.intensity());b.writeByte(p.pointRadius());b.writeByte(p.pointIntensity());b.writeByte(p.volumeMode());
  }
 },b->{
  int count=b.readVarInt();if(count<0||count>64)throw new IllegalArgumentException("peer count");
  List<Peer> peers=new ArrayList<>(count);
  for(int i=0;i<count;i++){
   peers.add(new Peer(b.readUUID(),b.readInt(),b.readUnsignedShort(),b.readUnsignedByte(),
     b.readUnsignedShort(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte()));
  }
  return new FlashlightPeersPayload(List.copyOf(peers));
 });
 @Override public Type<FlashlightPeersPayload> type(){return TYPE;}
}
