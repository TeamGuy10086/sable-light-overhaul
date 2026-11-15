package dev.sablelightoverhaul.lighting.channel;

import dev.sablelightoverhaul.SableLightOverhaul;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Installing and removing a block's channel boards.
 *
 * <p>The gesture is the same one the channel mod's own devices use -- right-click a block with a
 * frequency board -- and sneak-right-click clears what is installed. An empty hand still opens the
 * block's own page, which is what that gesture already meant, so nothing here takes a gesture away.
 *
 * <p>One handler serves every device in the mod that can be driven by a channel: the spotlight, the
 * disco ball and a registered light source. The devices differ in what they do with the channel's
 * value, not in how the channel is installed.
 */
@EventBusSubscriber(modid=SableLightOverhaul.ID)
public final class ChannelInteractions {
 private ChannelInteractions(){}
 @SubscribeEvent(priority=EventPriority.HIGH)
 public static void use(PlayerInteractEvent.RightClickBlock event){
  if(!(event.getLevel() instanceof ServerLevel level))return;
  Player player=event.getEntity();
  ItemStack held=event.getItemStack();
  BlockPos pos=event.getPos();
  ChannelBinding binding=bindingAt(level,pos);
  if(binding==null)return;
  // Without a board in hand this is not our gesture: an empty hand belongs to the block's own page.
  if(!ChannelBindings.isBoard(held))return;
  event.setUseBlock(TriState.FALSE);
  event.setUseItem(TriState.FALSE);
  if(player.isShiftKeyDown()){
   binding.clear();
   say(player,Component.translatable("sablelightoverhaul.channel.cleared").withStyle(ChatFormatting.GRAY));
   play(player,level,pos,1.4F);
   return;
  }
  ChannelBinding.BindResult result=binding.bind(held,player.getUUID());
  switch(result){
   case NOT_A_BOARD->say(player,Component.translatable("sablelightoverhaul.channel.not_a_board").withStyle(ChatFormatting.RED));
   case NO_READ_ACCESS->say(player,Component.translatable("sablelightoverhaul.channel.no_read").withStyle(ChatFormatting.RED));
   case FIRST_SLOT,SECOND_SLOT->{
    say(player,Component.translatable("sablelightoverhaul.channel.bound",binding.describe()).withStyle(ChatFormatting.AQUA));
    play(player,level,pos,1.0F);
   }
  }
 }
 /** The channel link of whatever block is there, or null when it is not one of ours. */
 public static ChannelBinding bindingAt(ServerLevel level,BlockPos pos){
  BlockEntity entity=level.getBlockEntity(pos);
  return entity instanceof ChannelDevice device?device.channel():null;
 }
 private static void say(Player player,Component message){
  player.displayClientMessage(message,true);
 }
 private static void play(Player player,ServerLevel level,BlockPos pos,float pitch){
  level.playSound(null,pos,SoundEvents.ITEM_FRAME_ADD_ITEM,SoundSource.BLOCKS,0.5F,pitch);
 }
 /** Every device in this mod that a channel can drive. */
 public interface ChannelDevice {
  ChannelBinding channel();
 }
}
