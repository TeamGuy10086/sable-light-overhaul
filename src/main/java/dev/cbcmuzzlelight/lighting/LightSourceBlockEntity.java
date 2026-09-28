package dev.cbcmuzzlelight.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Remembers the block a stand-in replaced, so the renderer can draw that block and the block can be
 * put back exactly as it was.
 *
 * <p>The state is sent to the client with the block update, because the client has to draw a model
 * that depends on it.
 */
public class LightSourceBlockEntity extends BlockEntity {
 private BlockState original;
 public LightSourceBlockEntity(BlockPos pos,BlockState state){super(LightingBlocks.LIGHT_SOURCE_ENTITY.get(),pos,state);}
 /** The block this stand-in speaks for, or null when it was never set (nothing is drawn then). */
 public BlockState originalState(){return original;}
 public void setOriginal(BlockState state){
  original=state;
  setChanged();
  if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
 }
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
  super.saveAdditional(tag,registries);
  if(original!=null)tag.put("original",NbtUtils.writeBlockState(original));
 }
 @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
  super.loadAdditional(tag,registries);
  original=tag.contains("original")?NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK),tag.getCompound("original")):null;
 }
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
  CompoundTag tag=new CompoundTag();
  saveAdditional(tag,registries);
  return tag;
 }
 @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
