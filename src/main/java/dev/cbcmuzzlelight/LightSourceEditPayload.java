package dev.cbcmuzzlelight;

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
 */
public record LightSourceEditPayload(BlockPos pos,String field,double number) implements CustomPacketPayload {
 public static final Type<LightSourceEditPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(MuzzleLight.ID,"light_source_edit"));
 public static final StreamCodec<RegistryFriendlyByteBuf,LightSourceEditPayload> CODEC=StreamCodec.of((b,p)->{
  b.writeBlockPos(p.pos);
  b.writeUtf(p.field,32);
  b.writeDouble(p.number);
 },b->new LightSourceEditPayload(b.readBlockPos(),b.readUtf(32),b.readDouble()));
 @Override public Type<LightSourceEditPayload> type(){return TYPE;}
}
