package dev.sablelightoverhaul.lighting;
import dev.sablelightoverhaul.LocalizedText;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
/**
 * A spotlight: an aimed cone whose colour, width, brightness and aim can be driven by redstone, by
 * redstone as a mere switch, or not at all.
 *
 * <p>Empty hand opens its page, which owns every setting; the management tool stays a shortcut for the
 * two that get changed while building (falloff profile and hiding the model). The range is on the page.
 *
 * <p>Range is stored in the block entity, avoiding 1024 copies of every block state.
 * Falloff and visibility remain state properties. Old serialized ranges are migrated before decoding.
 *
 * <p>The block state's FACING is what the block was placed against and is not used: it is kept so old
 * worlds keep loading, and deliberately not given a meaning now, because every spotlight already placed
 * has one saved and reading it as an aim would swing all of them at once.
 */
public final class SpotlightBlock extends BaseEntityBlock {
 public static final MapCodec<SpotlightBlock> CODEC=MapCodec.unit(SpotlightBlock::new);
 /** Legacy placement direction. The beam always follows the block's local +Y, so this is inert. */
 public static final DirectionProperty FACING=BlockStateProperties.FACING;
 // Five is the ceiling because MODES below has six entries, the last being the physical inverse square
 // at index 5. A property that stopped at four made the page offer a profile the block could not store.
 public static final IntegerProperty MODE=IntegerProperty.create("mode",0,5);
 public static final BooleanProperty VISIBLE=BooleanProperty.create("visible");
 /** Falloff profiles; index matches the MODE blockstate. The last one is disabled -- see {@link Falloff}. */
 public static final String[] MODES=dev.sablelightoverhaul.Falloff.NAMES;
 public SpotlightBlock(){super(Properties.ofFullCopy(Blocks.BEACON).lightLevel(s->0));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.UP).setValue(MODE,0).setValue(VISIBLE,true));}
 @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(FACING,MODE,VISIBLE);}
 @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getClickedFace());}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new SpotlightBlockEntity(pos,state);}
 /** Only the server needs to tick, and only the latch needs that. */
 @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
  return level.isClientSide?null:createTickerHelper(type,LightingBlocks.SPOTLIGHT_ENTITY.get(),SpotlightBlockEntity.ticker());
 }
 /** The management tool can hide the model while keeping the light running. */
 @Override protected RenderShape getRenderShape(BlockState s){return s.getValue(VISIBLE)?RenderShape.MODEL:RenderShape.INVISIBLE;}
 /** Empty hand opens the lamp's page: the page is where every setting lives now. */
 @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
  if(!l.isClientSide)SpotlightUi.open(player,p);
  return InteractionResult.sidedSuccess(l.isClientSide);
 }
 /** Light management tool: right-click cycles the falloff profile, shift-right-click toggles the model. */
 public InteractionResult toolUse(BlockState s,Level l,BlockPos p,Player player){
  if(!player.mayBuild()) return InteractionResult.FAIL;
  if(!l.isClientSide){
   if(player.isShiftKeyDown()){s=s.setValue(VISIBLE,!s.getValue(VISIBLE));l.setBlock(p,s,3);player.displayClientMessage(LocalizedText.component("射灯模型：%s",LocalizedText.component(s.getValue(VISIBLE)?"显示":"隐藏")),true);}
   else{s=s.setValue(MODE,dev.sablelightoverhaul.Falloff.next(s.getValue(MODE)));l.setBlock(p,s,3);player.displayClientMessage(LocalizedText.component("射灯衰减：%s",LocalizedText.component(MODES[s.getValue(MODE)])),true);}
  }
  return InteractionResult.sidedSuccess(l.isClientSide);
 }
 /** A fresh lamp starts leaning the way its placer was looking, which is nearly always what was meant. */
 @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
  if(level.isClientSide)return;
  BlockOwners.placed(level,pos,placer);
  if(level.getBlockEntity(pos) instanceof SpotlightBlockEntity lamp){
   // Old pick-block/item stacks may still carry range in minecraft:block_state.
   var properties=stack.get(net.minecraft.core.component.DataComponents.BLOCK_STATE);
   var entityData=stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
   var saved=entityData==null?new net.minecraft.nbt.CompoundTag():entityData.copyTag();
   if(properties!=null&&!saved.contains("range")&&!saved.contains("slo_we_range")){
    String legacy=properties.properties().get("range");
    if(legacy!=null)try{lamp.setNumber("range",Integer.parseInt(legacy));}catch(NumberFormatException ignored){}
   }
   if(placer!=null)lamp.setNumber("azimuth",Mth.wrapDegrees(90.0-placer.getYRot()));
  }
 }
 @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moving){super.onPlace(s,l,p,old,moving);if(l instanceof ServerLevel server)RegisteredSources.get(server).spotlight(SourceAnchor.capture(l,p),true);}
 @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){if(!next.is(this)&&l instanceof ServerLevel server)RegisteredSources.get(server).spotlight(SourceAnchor.capture(l,p),false);super.onRemove(s,l,p,next,moving);}
}
