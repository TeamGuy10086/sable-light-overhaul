package dev.sablelightoverhaul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One field of one player's flashlight, on its way to the server from its page. */
public record FlashlightEditPayload(String field,double number) implements CustomPacketPayload {
 public static final Type<FlashlightEditPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID,"flashlight_edit"));
 public static final StreamCodec<RegistryFriendlyByteBuf,FlashlightEditPayload> CODEC=StreamCodec.of((b,p)->{
  b.writeUtf(p.field,32);
  b.writeDouble(p.number);
 },b->new FlashlightEditPayload(b.readUtf(32),b.readDouble()));
 @Override public Type<FlashlightEditPayload> type(){return TYPE;}
}
