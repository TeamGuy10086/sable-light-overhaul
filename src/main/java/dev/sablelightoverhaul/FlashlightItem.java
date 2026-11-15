package dev.sablelightoverhaul;

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
     player.displayClientMessage(Component.translatable("sablelightoverhaul.flashlight.needsOp"),true);
     return InteractionResultHolder.fail(stack);
    }
    dev.sablelightoverhaul.lighting.FlashlightUi.open(player);
   }
   return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
  }
  if(!level.isClientSide){
   boolean on=dev.sablelightoverhaul.lighting.FlashlightSettings.toggle(player);
   // The switch has to sound like a switch: the vanilla stone button is what an ear already reads as "that
   // did something", and on and off are given different sounds so the state is audible without looking at
   // the lamp. Played at the holder and broadcast from the server, so the people standing next to them hear
   // the click too -- except is null, which includes the player who pressed it.
   level.playSound(null,player.getX(),player.getY(),player.getZ(),
     on?net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON:net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_OFF,
     net.minecraft.sounds.SoundSource.PLAYERS,0.8F,on?1.0F:0.85F);
   player.displayClientMessage(Component.translatable(
     on?"sablelightoverhaul.flashlight.switchedOn":"sablelightoverhaul.flashlight.switchedOff"),true);
  }
  return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
 }
}
