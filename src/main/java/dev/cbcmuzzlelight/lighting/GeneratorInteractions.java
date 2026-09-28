package dev.cbcmuzzlelight.lighting;

import dev.cbcmuzzlelight.MuzzleLight;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/** Wrench editing and placement feedback for the generators. */
@EventBusSubscriber(modid=MuzzleLight.ID)
public final class GeneratorInteractions {
 private static final ResourceLocation WRENCH=ResourceLocation.fromNamespaceAndPath("create","wrench");
 private static Item wrench;
 private static boolean wrenchResolved;
 private GeneratorInteractions(){}
 /**
  * The wrench owns every click on a generator.
  *
  * <p>Denying both halves of the interaction is what keeps Create's own wrench behaviour away:
  * its right-click rotates a block and its shift-right-click dismantles one, and neither may
  * happen to an emitter whose whole state is the offset this edit changes.
  */
 @SubscribeEvent(priority=EventPriority.HIGHEST)
 public static void wrench(PlayerInteractEvent.RightClickBlock event){
  if(!(event.getLevel() instanceof ServerLevel level))return;
  if(!isWrench(event.getItemStack()))return;
  BlockState state=level.getBlockState(event.getPos());
  var player=event.getEntity();
  // Markers carry no block entity: a wrench click only hides or shows the model, and Create's own
  // right-click (rotate) and shift-right-click (dismantle) must not touch them either.
  if(state.getBlock() instanceof MarkerBlock marker){
   event.setUseBlock(TriState.FALSE);
   event.setUseItem(TriState.FALSE);
   if(player.isShiftKeyDown()){
    BlockState next=state.cycle(MarkerBlock.VISIBLE);
    level.setBlock(event.getPos(),next,3);
    say(player,Component.translatable(next.getValue(MarkerBlock.VISIBLE)?"cbcmuzzlelight.generator.shown":"cbcmuzzlelight.generator.hidden").getString());
   }
   return;
  }
  if(!(level.getBlockEntity(event.getPos()) instanceof GeneratorBlockEntity generator))return;
  if(!(state.getBlock() instanceof GeneratorBlock block))return;
  event.setUseBlock(TriState.FALSE);
  event.setUseItem(TriState.FALSE);
  if(player.isShiftKeyDown()){
   BlockState next=state.cycle(GeneratorBlock.VISIBLE);
   level.setBlock(event.getPos(),next,3);
   say(player,Component.translatable(next.getValue(GeneratorBlock.VISIBLE)?"cbcmuzzlelight.generator.shown":"cbcmuzzlelight.generator.hidden").getString());
   return;
  }
  Direction face=event.getHitVec().getDirection();
  if(face.getAxis().isVertical()){
   say(player,Component.translatable("cbcmuzzlelight.generator.horizontalOnly").getString());
   return;
  }
  generator.nudge(face.getStepX(),face.getStepZ(),GeneratorBlock.STEP);
  say(player,Component.translatable("cbcmuzzlelight.generator.offset",
    String.format(Locale.ROOT,"%.1f",generator.number(GeneratorFields.OFFSET_X)),
    String.format(Locale.ROOT,"%.1f",generator.number(GeneratorFields.OFFSET_Z))).getString());
  // Both kinds are only meaningful on a body, so say why a world placement was refused.
 }
 /** Explain the refusal instead of leaving a click that silently does nothing. */
 @SubscribeEvent(priority=EventPriority.LOWEST)
 public static void placeHint(PlayerInteractEvent.RightClickBlock event){
  if(!(event.getLevel() instanceof ServerLevel level))return;
  if(!(event.getItemStack().getItem() instanceof BlockItem item))return;
  if(!(item.getBlock() instanceof GeneratorBlock)&&!(item.getBlock() instanceof MarkerBlock))return;
  if(SourceAnchor.capture(level,event.getPos()).ship()!=null)return;
  say(event.getEntity(),Component.translatable("cbcmuzzlelight.generator.sableOnly").getString());
 }
 private static void say(net.minecraft.world.entity.player.Player player,String text){
  player.displayClientMessage(Component.literal(text),true);
 }
 /** Create's wrench, matched by id so this needs no compile-time Create dependency. */
 private static boolean isWrench(@Nullable ItemStack stack){
  if(stack==null||stack.isEmpty())return false;
  if(!wrenchResolved){
   wrenchResolved=true;
   wrench=BuiltInRegistries.ITEM.get(WRENCH);
  }
  if(wrench!=null&&stack.is(wrench))return true;
  // Addons subclass the wrench, so the class name is the second and last fallback.
  return stack.getItem().getClass().getName().endsWith("WrenchItem");
 }
}
