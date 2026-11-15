package dev.sablelightoverhaul.lighting;

import dev.sablelightoverhaul.LocalizedText;
import dev.sablelightoverhaul.SableLightOverhaul;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The light-source manager: shift-right-click a light block with the registrar tool and it becomes a
 * stand-in that emits nothing while still looking like the block it replaced.
 *
 * <p>This is how a lamp's own white light is removed at the root. A shader can only add to block
 * light, and the light lives in whichever engine owns the block — the world's, or a physics body's —
 * so switching the source itself off is the one approach that works no matter where the lamp is, and
 * it needs no shader patch at all. The level the block emitted is carried over so this mod's own
 * light can take its place.
 *
 * <p>Shift-right-clicking the stand-in again puts the original block back. A block that carries a
 * block entity is refused: swapping it would lose whatever that entity holds.
 */
@EventBusSubscriber(modid=SableLightOverhaul.ID)
public final class TakeoverInteractions {
 private TakeoverInteractions(){}
 @SubscribeEvent(priority=EventPriority.HIGHEST)
 public static void shiftUse(PlayerInteractEvent.RightClickBlock event){
  if(!(event.getLevel() instanceof ServerLevel level))return;
  // Two gestures land here. The registrar tool takes over a lamp that is already lighting the place; the
  // light source *item* does the opposite -- it dresses a light source in an arbitrary block's model, which
  // is the same disguise the takeover uses, aimed the other way. Everything below is shared.
  boolean tool=event.getItemStack().getItem() instanceof RegistrarItem;
  boolean item=event.getItemStack().is(LightingBlocks.LIGHT_SOURCE_ITEM.get());
  if(!tool&&!item){
   // An empty sneaking hand opens the page of a source that is registered but whose block we never
   // replaced. The tool's two clicks are already spoken for (cycle a registration, take a block over),
   // so before this there was no way at all to reach the settings of a lamp we do not own the block of.
   // Only a registered anchor is claimed here, so every other block keeps its own gestures.
   if(!event.getItemStack().isEmpty()||!event.getEntity().isShiftKeyDown())return;
   BlockPos at=event.getPos();
   if(RegisteredSources.get(level).find(SourceAnchor.capture(level,at),false)==null)return;
   event.setUseBlock(TriState.FALSE);
   event.setUseItem(TriState.FALSE);
   LightSourceUi.openOrReport(event.getEntity(),at);
   return;
  }
  if(!event.getEntity().isShiftKeyDown())return;
  BlockPos pos=event.getPos();
  BlockState state=level.getBlockState(pos);
  var player=event.getEntity();
  event.setUseBlock(TriState.FALSE);
  event.setUseItem(TriState.FALSE);
  if(state.getBlock() instanceof LightSourceBlock){
   if(level.getBlockEntity(pos) instanceof LightSourceBlockEntity standIn&&standIn.originalState()!=null){
    level.setBlock(pos,standIn.originalState(),3);
    say(player,LocalizedText.component("已还原为原方块"));
   }else{
    // A stand-in placed by hand has nothing to restore, so shift here means the same as it does for
    // the tool everywhere else: open the source's own page, which holds the mode, the colour and the
    // brightness this used to step one level at a time.
    LightSourceUi.openOrReport(player,pos);
   }
   return;
  }
  if(state.hasBlockEntity()){say(player,LocalizedText.component("这个方块带方块实体，暂不支持接管"));return;}
  int emission=state.getLightEmission(level,pos);
  if(emission<=0){
   // The tool is for a lamp that already lights the place, so a block that emits nothing is a mistake and
   // saying so is the useful answer. The item's gesture is the opposite one -- "make this block a lamp
   // without changing how it looks" -- so a block that emits nothing is the normal case, and the default
   // emission is what it will light with. Its own emission is kept when it had one.
   if(tool){say(player,LocalizedText.component("这个方块当前不发光"));return;}
   emission=15;
  }
  level.setBlock(pos,LightingBlocks.LIGHT_SOURCE.get().defaultBlockState().setValue(LightSourceBlock.EMISSION,emission),3);
  if(level.getBlockEntity(pos) instanceof LightSourceBlockEntity standIn)standIn.setOriginal(state);
  // The colour is resolved from the block that was replaced, exactly the way the light pass resolves it
  // later, so the message names the tint the player is about to see instead of always saying 透明.
  int color=LightingCommon.colorOf(state.getBlock());
  Component message=tool
    ?LocalizedText.component("已接管：原版发光关闭，亮度 %s、颜色 %s 交给本模组",emission,LightSourceServer.colorName(color))
    :LocalizedText.component("已伪装为「%s」并点亮：亮度 %s、颜色 %s",state.getBlock().getName(),emission,LightSourceServer.colorName(color));
  say(player,message);
 }
 private static void say(net.minecraft.world.entity.player.Player player,Component message){
  player.displayClientMessage(message,true);
 }
}
