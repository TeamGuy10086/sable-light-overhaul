package dev.sablelightoverhaul.lighting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shared body of the two emitters.
 *
 * <p>Everything positional resolves through the physics body: the offset is stored in body space,
 * the ground is found by a world-space ray below it, and the exhaust rises in world space. A
 * generator in the world could not do any of that, so placement is refused off a Sable sub-level.
 */
public abstract class GeneratorBlock extends Block implements EntityBlock {
 @Override public void setPlacedBy(net.minecraft.world.level.Level level,BlockPos pos,
   net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.entity.LivingEntity placer,
   net.minecraft.world.item.ItemStack stack){
  super.setPlacedBy(level,pos,state,placer,stack);
  BlockOwners.placed(level,pos,placer);
 }

 /** The model can be hidden without stopping the emitter, so it can sit inside a hull. */
 public static final BooleanProperty VISIBLE=BooleanProperty.create("visible");
 /** One wrench click moves the emission point this far. */
 public static final double STEP=0.1;
 protected GeneratorBlock(){
  super(Properties.ofFullCopy(Blocks.BEACON).lightLevel(state->0));
  registerDefaultState(stateDefinition.any().setValue(VISIBLE,true));
 }
 public abstract GeneratorKind kind();
 @Override protected abstract MapCodec<? extends Block> codec();
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>builder){builder.add(VISIBLE);}
 @Override protected RenderShape getRenderShape(BlockState state){return state.getValue(VISIBLE)?RenderShape.MODEL:RenderShape.INVISIBLE;}
 /** Only a physics body can host one; see the class note. */
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){
  return SourceAnchor.capture(context.getLevel(),context.getClickedPos()).ship()==null?null:defaultBlockState();
 }
 /** No collision at all: the emitter sits inside a hull, and a solid box would both block the body
  * and be the first thing the ground search hits. */
 @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.empty();}
 @Override protected boolean propagatesSkylightDown(BlockState state,BlockGetter level,BlockPos pos){return true;}
 @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
  if(!level.isClientSide)GeneratorUi.open(player,pos);
  return InteractionResult.sidedSuccess(level.isClientSide);
 }
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new GeneratorBlockEntity(pos,state);}
 /** Dust: kicked off the ground under the body while it moves. */
 public static final class Dust extends GeneratorBlock {
  public static final MapCodec<Dust> CODEC=MapCodec.unit(Dust::new);
  public Dust(){super();}
  @Override public GeneratorKind kind(){return GeneratorKind.DUST;}
  @Override protected MapCodec<? extends Block> codec(){return CODEC;}
 }
 /** Smoke: vented at the block and left behind in world space. */
 public static final class Smoke extends GeneratorBlock {
  public static final MapCodec<Smoke> CODEC=MapCodec.unit(Smoke::new);
  public Smoke(){super();}
  @Override public GeneratorKind kind(){return GeneratorKind.SMOKE;}
  @Override protected MapCodec<? extends Block> codec(){return CODEC;}
 }
 /** Vortex: the blocks the wand registered to it shed while the body moves. */
 public static final class Vortex extends GeneratorBlock {
  public static final MapCodec<Vortex> CODEC=MapCodec.unit(Vortex::new);
  public Vortex(){super();}
  @Override public GeneratorKind kind(){return GeneratorKind.VORTEX;}
  @Override protected MapCodec<? extends Block> codec(){return CODEC;}
 }
}
