package dev.cbcmuzzlelight;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A flashlight: held in the hand, it throws a small point light at the player's waist and a cone down
 * the way they are looking.
 *
 * <p>Nothing about the light is decided here. The client draws it from the settings the server hands
 * over (see {@code FlashlightStatePayload}), because those settings are operator-gated, and this item's
 * only job is to be the thing that is held, to switch it on and off, and to open the page that edits it.
 *
 * <p><b>Right-click switches it. Shift-right-click opens the page.</b> The switch comes first because it
 * is the thing you do while walking around, and it is deliberately not operator-gated: it is your own
 * lamp. Opening the page still is, because that is where the numbers that decide what everyone else sees
 * are changed, and a player without the rights is refused politely rather than shown a page whose every
 * edit the server would silently throw away.
 */
public class FlashlightItem extends Item {
 public FlashlightItem(Properties properties){super(properties);}
 @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
  ItemStack stack=player.getItemInHand(hand);
  if(player.isShiftKeyDown()){
   if(!level.isClientSide){
    if(!player.hasPermissions(2)){
     player.displayClientMessage(Component.translatable("cbcmuzzlelight.flashlight.needsOp"),true);
     return InteractionResultHolder.fail(stack);
    }
    dev.cbcmuzzlelight.lighting.FlashlightUi.open(player);
   }
   return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
  }
  if(!level.isClientSide){
   boolean on=dev.cbcmuzzlelight.lighting.FlashlightSettings.toggle(player);
   player.displayClientMessage(Component.translatable(
     on?"cbcmuzzlelight.flashlight.switchedOn":"cbcmuzzlelight.flashlight.switchedOff"),true);
  }
  return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
 }
}
