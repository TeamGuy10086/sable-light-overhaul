package dev.cbcmuzzlelight.lighting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
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
 * The two markers, both plain blocks: they carry no state of their own beyond being present.
 *
 * <p>A hull marker says which body is the vehicle, so the recoil does not have to be guessed from
 * bounding boxes. A dust marker says which body the dust that comes off the vehicle belongs to, so
 * that dust can be placed on the surfaces a ray actually finds instead of somewhere inside the hull.
 *
 * <p>Neither has a collision box: they sit inside a hull, and a solid box there would both block the
 * body and be the first thing a downward search hits. Placement follows the generators: only inside a
 * physics body, and hidden or shown with a wrench.
 */
public abstract class MarkerBlock extends Block {
 /** The model can be hidden without losing the marking, so it can sit inside a hull. */
 public static final BooleanProperty VISIBLE=BooleanProperty.create("visible");
 protected MarkerBlock(){
  super(Properties.ofFullCopy(Blocks.BEACON).lightLevel(state->0));
  registerDefaultState(stateDefinition.any().setValue(VISIBLE,true));
 }
 /** Whether only one of these is allowed per body cluster. */
 protected boolean single(){return false;}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>builder){builder.add(VISIBLE);}
 @Override protected RenderShape getRenderShape(BlockState state){return state.getValue(VISIBLE)?RenderShape.MODEL:RenderShape.INVISIBLE;}
 /** No collision at all; see the class note. */
 @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.empty();}
 @Override protected boolean propagatesSkylightDown(BlockState state,BlockGetter level,BlockPos pos){return true;}
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){
  if(SourceAnchor.capture(context.getLevel(),context.getClickedPos()).ship()==null)return null;
  if(!single()||context.getLevel().isClientSide)return defaultBlockState();
  Player player=context.getPlayer();
  if(SableMarkers.hullMarkerInCluster(context.getLevel(),context.getClickedPos())){
   if(player!=null)player.displayClientMessage(Component.translatable("cbcmuzzlelight.marker.hull.exists"),false);
   return null;
  }
  return defaultBlockState();
 }
 /** Marks the body the recoil is applied to. Only one per cluster. */
 public static final class Hull extends HullMarkerBlock {
  public static final MapCodec<Hull> CODEC=MapCodec.unit(Hull::new);
  public Hull(){super();}
  @Override protected boolean single(){return true;}
  @Override protected MapCodec<? extends Block> codec(){return CODEC;}
 }
 /** Marks the body whose surfaces the dust coming off the vehicle is placed on. */
 public static final class Dust extends MarkerBlock {
  public static final MapCodec<Dust> CODEC=MapCodec.unit(Dust::new);
  public Dust(){super();}
  @Override protected MapCodec<? extends Block> codec(){return CODEC;}
 }
}
