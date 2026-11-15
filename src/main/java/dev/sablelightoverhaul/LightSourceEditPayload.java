package dev.sablelightoverhaul;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One field of one registered light source, on its way to the server from its page.
 *
 * <p>The same shape the flashlight's page uses, with a block position added: a flashlight's settings
 * belong to a player, a lamp's belong to a place. The position is the only handle the page and the server
 * share, and the server resolves it against the level the editing player is standing in, so a lamp on a
 * physics body is reached the same way a lamp on the ground is.
 *
 * <p>Nothing here is a decision. The server clamps, stores and re-syncs, exactly as it does for the
 * lamps' own block pages.
 * <p>Most fields are a number. The shadow cones are not -- they are a small text a player writes -- so the
 * packet carries a string as well. Encoding that as a number would only move the parsing into the page,
 * where nobody can be told what was wrong with what they typed.
 */
public record LightSourceEditPayload(BlockPos pos,int id,String field,double number,String text) implements CustomPacketPayload {
 public static final Type<LightSourceEditPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID,"light_source_edit"));
 public static final StreamCodec<RegistryFriendlyByteBuf,LightSourceEditPayload> CODEC=StreamCodec.of((b,p)->{
  b.writeBlockPos(p.pos);
b.writeVarInt(p.id);
  b.writeUtf(p.field,32);
  b.writeDouble(p.number);
  b.writeUtf(p.text,256);
 },b->new LightSourceEditPayload(b.readBlockPos(),b.readVarInt(),b.readUtf(32),b.readDouble(),b.readUtf(256)));
 @Override public Type<LightSourceEditPayload> type(){return TYPE;}
}
