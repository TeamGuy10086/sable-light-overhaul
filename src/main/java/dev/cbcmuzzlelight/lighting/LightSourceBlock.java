package dev.cbcmuzzlelight.lighting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Stands in for a light block that was taken over: it looks exactly like the block it replaced — the
 * block entity renderer replays that block's own model at full brightness — but it emits nothing.
 *
 * <p>That is the whole point. A white pool around a lamp is the lamp's own block light, sitting in
 * whichever light engine owns it (the world's, or a physics body's), and a shader can only ever add
 * to it. Turning the source off at the block is the one place that works everywhere: no light engine
 * anywhere sees an emission to propagate, so this mod's own coloured light is all that is left.
 *
 * <p>The level the original block emitted is kept in the block state, because vanilla must see zero
 * ({@link #EMISSION} is not wired to {@code lightLevel}) while this mod must still know how bright
 * the source is: {@link #emissionOf} is the single place that answers that question.
 */
public class LightSourceBlock extends BaseEntityBlock {
 public static final MapCodec<LightSourceBlock> CODEC=MapCodec.unit(LightSourceBlock::new);
 /** The level the replaced block emitted. Vanilla is told zero; this mod reads this. */
 public static final IntegerProperty EMISSION=IntegerProperty.create("emission",0,15);
 public LightSourceBlock(){
  super(Properties.of().strength(0.3F).lightLevel(state->0).noOcclusion().isViewBlocking((state,level,pos)->false));
  registerDefaultState(stateDefinition.any().setValue(EMISSION,15));
 }
 @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>builder){builder.add(EMISSION);}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new LightSourceBlockEntity(pos,state);}
 /** The model is drawn by the block entity renderer, which replays the original block's own model. */
 @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.ENTITYBLOCK_ANIMATED;}
 /** Shape and collision are the replaced block's own, so a hull or a floor keeps its collision. */
 @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shapeOf(level,pos,true);}
 @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shapeOf(level,pos,false);}
 /** A stand-in that replaced a block wears that block's shapes; one placed by hand has only a small target. */
 private static final VoxelShape HAND_PLACED=Block.box(3.0,3.0,3.0,13.0,13.0,13.0);
 private VoxelShape shapeOf(BlockGetter level,BlockPos pos,boolean collision){
  if(level.getBlockEntity(pos) instanceof LightSourceBlockEntity standIn){
   BlockState original=standIn.originalState();
   if(original!=null&&!original.isAir())return collision?original.getCollisionShape(level,pos):original.getShape(level,pos);
  }
  // A hidden light placed by hand is invisible and has no collision, but it must still be *targetable*:
  // with an empty shape the client's raycast finds nothing, so the block could not be broken or
  // right-clicked at all and the only way out was a command. A small central box fixes the targeting
  // without making it visible or solid.
  return collision?Shapes.empty():HAND_PLACED;
 }
 @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){
  super.onPlace(state,level,pos,old,moving);
  // Placed straight from the item the stand-in has no original to speak for, and since its own
  // lightLevel is zero it would otherwise be an invisible block that emits nothing at all. It is a
  // light in its own right, so it is registered like any other static source. A lamp that was already
  // registered keeps the mode and colour it had, because ensure() only fills a gap.
  if(level instanceof ServerLevel server&&state.getValue(EMISSION)>0)
   RegisteredSources.get(server).ensure(SourceAnchor.capture(level,pos),2);
 }
 @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
  // A stand-in placed by hand is the light; when it goes, its registration goes with it. A stand-in
  // that replaced a lamp keeps its registration, because the lamp comes back and is still a lamp.
  if(!next.is(this)&&level instanceof ServerLevel server&&placedByHand(level,pos))
   RegisteredSources.get(server).removeAnchor(SourceAnchor.capture(level,pos));
  super.onRemove(state,level,pos,next,moving);
 }
 /** True when this stand-in stands for nothing: it was placed from the item rather than taking a block over. */
 public static boolean placedByHand(Level level,BlockPos pos){
  return !(level.getBlockEntity(pos) instanceof LightSourceBlockEntity standIn)||standIn.originalState()==null;
 }
 /**
  * The block state that should decide a stand-in's colour: the block it replaced, or itself.
  *
  * <p>Registered sources with {@link SourceColor#AUTO} resolve their palette index from a block id, and
  * for a taken-over lamp that id must be the original lamp's: the stand-in is a {@code light_source}
  * block, whose own id carries no colour word, so reading it would silently drop the colour the lamp
  * had. A stand-in placed by hand has nothing to inherit and answers with itself.
  */
 public static BlockState colorHost(Level level,BlockPos pos,BlockState state){
  if(level.getBlockEntity(pos) instanceof LightSourceBlockEntity standIn){
   BlockState original=standIn.originalState();
   if(original!=null&&!original.isAir())return original;
  }
  return state;
 }
 /**
  * Empty hand: the stand-in's own page, which is where every setting of it lives.
  *
  * <p>It used to step the brightness, one level per click, because brightness was the only setting a
  * stand-in had. It has five now — the registration mode, the colour, the brightness, the radius and
  * whether it stands for another block — and a click that changed one of them could not say which, so
  * the click opens the page and the page decides.
  */
 @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,net.minecraft.world.phys.BlockHitResult hit){
  // The page is opened by the server, which is the side that holds the registration it edits.
  if(!level.isClientSide)LightSourceUi.openOrReport(player,pos);
  return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
 }
 /**
  * The level this mod should treat a block as emitting.
  *
  * <p>A stand-in answers with the level of the block it replaced, every other block with its own
  * vanilla emission. Callers that used to read {@code state.getLightEmission} must come through here,
  * or taking a lamp over would make it dark instead of coloured.
  */
 public static int emissionOf(Level level,BlockPos pos,BlockState state){
  if(state.getBlock() instanceof LightSourceBlock)return state.getValue(EMISSION);
  return state.getLightEmission(level,pos);
 }
 /**
  * Breaking a stand-in gives back what was taken over; a stand-in that was placed by hand gives back
  * itself, or the block would simply disappear.
  */
 @Override public void playerDestroy(Level level,Player player,BlockPos pos,BlockState state,BlockEntity blockEntity,ItemStack tool){
  giveBack(level,pos,blockEntity);
  super.playerDestroy(level,player,pos,state,blockEntity,tool);
 }
 @Override public void wasExploded(Level level,BlockPos pos,Explosion explosion){
  giveBack(level,pos,level.getBlockEntity(pos));
  super.wasExploded(level,pos,explosion);
 }
 /** Puts the replaced block back, or drops the stand-in itself when it stood for nothing. */
 private static void giveBack(Level level,BlockPos pos,BlockEntity blockEntity){
  if(blockEntity instanceof LightSourceBlockEntity standIn&&standIn.originalState()!=null){
   Block.popResource(level,pos,new ItemStack(standIn.originalState().getBlock()));
   return;
  }
  Block.popResource(level,pos,new ItemStack(LightingBlocks.LIGHT_SOURCE_ITEM.get()));
 }
}
