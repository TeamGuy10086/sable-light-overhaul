package dev.sablelightoverhaul.munitions;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import rbasamoyai.createbigcannons.munitions.big_cannon.SimpleShellBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;
import rbasamoyai.createbigcannons.index.CBCMunitionPropertiesHandlers;
public class IlluminationShellBlock extends SimpleShellBlock<IlluminationShellProjectile> {
 private static final MapCodec<IlluminationShellBlock> CODEC=simpleCodec(IlluminationShellBlock::new);
 public IlluminationShellBlock(BlockBehaviour.Properties properties){super(properties);}
 @Override protected MapCodec<? extends DirectionalBlock> codec(){return CODEC;}
 @Override public EntityType<? extends IlluminationShellProjectile> getAssociatedEntityType(){return FlareMunitions.SHELL_ENTITY.get();}
 @Override public BlockEntityType<? extends FuzedBlockEntity> getBlockEntityType(){return FlareMunitions.SHELL_BLOCK_ENTITY.get();}
 @Override public boolean isBaseFuze(){return CBCMunitionPropertiesHandlers.COMMON_SHELL_BIG_CANNON_PROJECTILE.getPropertiesOf(getAssociatedEntityType()).fuze().baseFuze();}

 @Override public IlluminationShellProjectile getProjectile(Level level, List<StructureBlockInfo> blocks){
  var projectile=(IlluminationShellProjectile)super.getProjectile(level,blocks);
  projectile.setSettings(FlareSettings.read(blocks.isEmpty()?null:blocks.getFirst().nbt()));
  return projectile;
 }
 @Override public IlluminationShellProjectile getProjectile(Level level, ItemStack stack){
  var projectile=(IlluminationShellProjectile)super.getProjectile(level,stack);
  projectile.setSettings(stack.getOrDefault(FlareMunitions.SETTINGS.get(),FlareSettings.DEFAULT));
  return projectile;
 }
 @Override public IlluminationShellProjectile getProjectile(Level level, BlockPos pos, BlockState state){
  var projectile=(IlluminationShellProjectile)super.getProjectile(level,pos,state);
  if(level.getBlockEntity(pos) instanceof IlluminationShellBlockEntity be)projectile.setSettings(be.settings());
  return projectile;
 }
 @Override public StructureBlockInfo getHandloadingInfo(ItemStack stack, BlockPos pos, Direction direction, HolderLookup.Provider registries){
  var info=super.getHandloadingInfo(stack,pos,direction,registries);
  var tag=info.nbt()==null?new net.minecraft.nbt.CompoundTag():info.nbt();
  stack.getOrDefault(FlareMunitions.SETTINGS.get(),FlareSettings.DEFAULT).write(tag);
  return new StructureBlockInfo(info.pos(),info.state(),tag);
 }
 @Override public ItemStack getExtractedItem(StructureBlockInfo info, HolderLookup.Provider registries){
  var stack=super.getExtractedItem(info,registries);
  stack.set(FlareMunitions.SETTINGS.get(),FlareSettings.read(info.nbt()));
  return stack;
 }
 @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state){
  var stack=super.getCloneItemStack(level,pos,state);
  if(level.getBlockEntity(pos) instanceof IlluminationShellBlockEntity be)stack.set(FlareMunitions.SETTINGS.get(),be.settings());
  return stack;
 }
 // CBC's base implementation compares against its own BE type; this shell has a separate validity set.
 @Override public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state, BlockEntityType<S> type){
  return createTickerHelper(type,FlareMunitions.SHELL_BLOCK_ENTITY.get(),(l,p,s,be)->be.tick());
 }
}
