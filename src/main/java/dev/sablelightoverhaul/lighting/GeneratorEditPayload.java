package dev.sablelightoverhaul.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One edited value, sent from a configuration page to the server.
 *
 * <p>The page is a plain view over the block entity, so edits travel as our own payload rather than
 * through the page toolkit's binding layer. That keeps the page independent of a third-party sync
 * system (whose value ids are positional and can silently desync when the two sides build a
 * different widget tree) and makes an edit a single validated write.
 *
 * @param kind which value is being written: {@link #NUMBER}, {@link #TOGGLE} or {@link #TEXT}
 */
public record GeneratorEditPayload(BlockPos pos,String field,byte kind,double number,String text) implements CustomPacketPayload {
 public static final byte NUMBER=0,TOGGLE=1,TEXT=2;
 public static final Type<GeneratorEditPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("sablelightoverhaul","generator_edit"));
 public static final StreamCodec<RegistryFriendlyByteBuf,GeneratorEditPayload> CODEC=StreamCodec.of((buf,payload)->{
  buf.writeBlockPos(payload.pos);
  buf.writeUtf(payload.field,64);
  buf.writeByte(payload.kind);
  buf.writeDouble(payload.number);
  buf.writeUtf(payload.text,512);
 },buf->new GeneratorEditPayload(buf.readBlockPos(),buf.readUtf(64),buf.readByte(),buf.readDouble(),buf.readUtf(512)));
 public static GeneratorEditPayload of(BlockPos pos,String field,double number){
  return new GeneratorEditPayload(pos,field,NUMBER,number,"");
 }
 public static GeneratorEditPayload of(BlockPos pos,String field,boolean flag){
  return new GeneratorEditPayload(pos,field,TOGGLE,flag?1:0,"");
 }
 public static GeneratorEditPayload of(BlockPos pos,String field,String text){
  return new GeneratorEditPayload(pos,field,TEXT,0,text);
 }
 @Override public Type<GeneratorEditPayload> type(){return TYPE;}
}
