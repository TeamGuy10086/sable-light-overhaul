package dev.sablelightoverhaul.lighting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The dust marker, a plain block: it carries no state of its own beyond being present.
 *
 * <p>It says which body the dust coming off the vehicle belongs to, so that dust can be placed on the
 * surfaces a ray actually finds instead of somewhere inside the hull.
 *
 * <p>It has no collision box: it sits inside a hull, and a solid box there would both block the body
 * and be the first thing a downward search hits. Placement follows the generators: only inside a
 * physics body, and hidden or shown with a wrench.
 */
public abstract class MarkerBlock extends Block {
 /** The model can be hidden without losing the marking, so it can sit inside a hull. */
 public static final BooleanProperty VISIBLE=BooleanProperty.create("visible");
 protected MarkerBlock(){
  super(Properties.ofFullCopy(Blocks.BEACON).lightLevel(state->0));
  registerDefaultState(stateDefinition.any().setValue(VISIBLE,true));
 }
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>builder){builder.add(VISIBLE);}
 @Override protected RenderShape getRenderShape(BlockState state){return state.getValue(VISIBLE)?RenderShape.MODEL:RenderShape.INVISIBLE;}
 /** No collision at all; see the class note. */
 @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.empty();}
 @Override protected boolean propagatesSkylightDown(BlockState state,BlockGetter level,BlockPos pos){return true;}
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){
  return SourceAnchor.capture(context.getLevel(),context.getClickedPos()).ship()==null
    ? null : defaultBlockState();
 }
 /** Marks the body whose surfaces the dust coming off the vehicle is placed on. */
 public static final class Dust extends MarkerBlock {
  public static final MapCodec<Dust> CODEC=MapCodec.unit(Dust::new);
  public Dust(){super();}
  @Override protected MapCodec<? extends Block> codec(){return CODEC;}
 }
}
