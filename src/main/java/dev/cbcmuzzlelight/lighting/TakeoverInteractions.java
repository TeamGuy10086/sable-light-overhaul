package dev.cbcmuzzlelight.lighting;

import dev.cbcmuzzlelight.MuzzleLight;
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
@EventBusSubscriber(modid=MuzzleLight.ID)
public final class TakeoverInteractions {
 private TakeoverInteractions(){}
 @SubscribeEvent(priority=EventPriority.HIGHEST)
 public static void shiftUse(PlayerInteractEvent.RightClickBlock event){
  if(!(event.getLevel() instanceof ServerLevel level))return;
  if(!(event.getItemStack().getItem() instanceof RegistrarItem))return;
  if(!event.getEntity().isShiftKeyDown())return;
  BlockPos pos=event.getPos();
  BlockState state=level.getBlockState(pos);
  var player=event.getEntity();
  event.setUseBlock(TriState.FALSE);
  event.setUseItem(TriState.FALSE);
  if(state.getBlock() instanceof LightSourceBlock){
   if(level.getBlockEntity(pos) instanceof LightSourceBlockEntity standIn&&standIn.originalState()!=null){
    level.setBlock(pos,standIn.originalState(),3);
    say(player,"已还原为原方块");
   }else{
    // A stand-in placed by hand has nothing to restore, so shift here means the same as it does for
    // the tool everywhere else: open the source's own page, which holds the mode, the colour and the
    // brightness this used to step one level at a time.
    LightSourceUi.openOrReport(player,pos);
   }
   return;
  }
  if(state.hasBlockEntity()){say(player,"这个方块带方块实体，暂不支持接管");return;}
  int emission=state.getLightEmission(level,pos);
  if(emission<=0){say(player,"这个方块当前不发光");return;}
  level.setBlock(pos,LightingBlocks.LIGHT_SOURCE.get().defaultBlockState().setValue(LightSourceBlock.EMISSION,emission),3);
  if(level.getBlockEntity(pos) instanceof LightSourceBlockEntity standIn)standIn.setOriginal(state);
  // The colour is resolved from the block that was replaced, exactly the way the light pass resolves it
  // later, so the message names the tint the player is about to see instead of always saying 透明.
  int color=LightingCommon.colorOf(state.getBlock());
  say(player,"已接管：原版发光关闭，亮度 "+emission+"、颜色 "+SourceColor.name(color)+" 交给本模组");
 }
 private static void say(net.minecraft.world.entity.player.Player player,String text){
  player.displayClientMessage(Component.literal(text),true);
 }
}
