package dev.sablelightoverhaul.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;

/**
 * Holds one generator's edited values.
 *
 * <p>The values themselves live in {@link GeneratorValues}; this class connects them to the world.
 * They are pushed to nearby clients with the vanilla block-entity data packet, so a configuration
 * page can display what the server actually stored without any custom sync system.
 */
public final class GeneratorBlockEntity extends BlockEntity implements BlockOwners.Owned {
 /** The player who placed this device, or null for a legacy block (and for one placed by a machine). */
 private java.util.UUID owner;
 @Override public java.util.UUID owner(){return owner;}
 @Override public void setOwner(java.util.UUID id){owner=id;setChanged();}

 private final GeneratorValues values=new GeneratorValues();
 /** The blocks registered to this generator, as offsets in the body's plot space; see {@link VortexMarks}. */
 private final VortexMarks marks=new VortexMarks();
 public GeneratorBlockEntity(BlockPos pos,BlockState state){super(LightingBlocks.GENERATOR.get(),pos,state);}
 /** Which emitter this block is. */
 public GeneratorKind kind(){
  return getBlockState().getBlock() instanceof GeneratorBlock block?block.kind():GeneratorKind.DUST;
 }
 public GeneratorValues values(){return values;}
 public double number(String id){return values.number(kind(),id);}
 public boolean flag(String id){return values.flag(kind(),id);}
 public String text(String id){return values.text(kind(),id);}
 public void setNumber(String id,double value){if(values.setNumber(kind(),id,value))markDirty();}
 public void setFlag(String id,boolean value){if(values.setFlag(kind(),id,value))markDirty();}
 public void setText(String id,String value){if(values.setText(kind(),id,value))markDirty();}
 public net.minecraft.world.phys.Vec3 offset(){return values.offset(kind());}
 public void nudge(int dx,int dz,double step){values.nudge(kind(),dx,dz,step);markDirty();}
 public VortexMarks marks(){return marks;}
 /** The registration changed: keep it on disk, and tell whoever is holding the wand. */
 public void marksChanged(){
  setChanged();
  if(level!=null&&!level.isClientSide)level.sendBlockUpdated(getBlockPos(),getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
 }
 /** Persist and tell nearby clients, so an open page can show the stored values. */
 private void markDirty(){
  setChanged();
  if(level!=null&&!level.isClientSide)level.sendBlockUpdated(getBlockPos(),getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
 }
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
  CompoundTag tag=new CompoundTag();
  values.save(tag);
  BlockOwners.save(tag,owner);
  return tag;
 }
 @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
 @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){
  super.onDataPacket(connection,packet,registries);
  values.load(packet.getTag());
 }
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
  super.saveAdditional(tag,registries);
  values.save(tag);
  // Marks are saved but deliberately left out of getUpdateTag: the client that draws the highlight
  // gets them from the marking payload instead, so a thousand-block wing is never in a block packet.
  tag.put("VortexMarks",marks.save());
 }
 @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
  super.loadAdditional(tag,registries);
  values.load(tag);
  owner=BlockOwners.load(tag);
  marks.load(tag.getCompound("VortexMarks"));
 }
}
