package dev.sablelightoverhaul.lighting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A disco ball. Easter egg, and deliberately dumb: the block itself only marks a spot.
 *
 * <p>Its beams are lights, and they are decided on the client, so no server work happens per frame: the
 * client finds the balls near the player, spins their cones and hands them to the same light buffer the
 * muzzle flashes use. Nothing about it is expensive enough to care about, which is why it can afford to
 * cycle colours and spin without any of the budgeting the rest of the mod does. The settings are kept
 * in the ball's own block entity so that everyone sees the same show.
 */
public class DiscoBallBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
 public static final MapCodec<DiscoBallBlock> CODEC=MapCodec.unit(DiscoBallBlock::new);
 public DiscoBallBlock(){
  super(Properties.ofFullCopy(Blocks.BEACON).lightLevel(state->0));
 }
 @Override protected MapCodec<? extends Block> codec(){return CODEC;}
 /** Right-click opens its page; the settings live on the ball and are shared with everyone. */
 @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state,net.minecraft.world.level.Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.phys.BlockHitResult hit){
  if(LightingCommon.on(LightingCommon.GEN_DEBUG))com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul disco: right click at {} (clientSide={})",pos,level.isClientSide);
  if(!level.isClientSide)DiscoBallUi.open(player,pos);
  return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
 }
 @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new DiscoBallBlockEntity(pos,state);}
 /**
  * The ball only needs ticking on the server, and only while a channel board is installed: everything
  * else about the show is decided on the clients from the ball's own synced values.
  */
 @Override public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
   net.minecraft.world.level.Level level,BlockState state,net.minecraft.world.level.block.entity.BlockEntityType<T> type){
  return level.isClientSide?null:(lvl,pos,st,be)->{
   if(be instanceof DiscoBallBlockEntity ball)ball.tick(lvl,pos);
  };
 }
 /** A new ball takes the server's configured show, which is then sent to every client. */
 @Override public void setPlacedBy(net.minecraft.world.level.Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){
  if(!level.isClientSide&&level.getBlockEntity(pos) instanceof DiscoBallBlockEntity settings)settings.useConfiguredDefaults();
  BlockOwners.placed(level,pos,placer);
 }
 /** No collision: it is decoration, and a solid ball on a deck is just something to trip over. */
 @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.empty();}
 @Override protected boolean propagatesSkylightDown(BlockState state,BlockGetter level,BlockPos pos){return true;}
 /**
  * Placeable anywhere. On a physics body the ball is found in that body's plot, on the ground it is
  * found in the ordinary chunks; refusing a ground placement only made the block look broken, since a
  * ball that cannot be placed emits nothing and gives no reason.
  */
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState();}
}
