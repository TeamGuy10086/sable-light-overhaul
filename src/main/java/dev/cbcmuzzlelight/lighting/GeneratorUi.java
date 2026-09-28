package dev.cbcmuzzlelight.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Opens a generator's configuration page. */
public final class GeneratorUi {
 private GeneratorUi(){}
 /**
  * Show the page for one generator.
  * @param player - the player that interacted.
  * @param pos - the generator's position in the host level.
  * @returns whether a page was opened; when false the values are printed instead, so a pack
  *          without a page backend can still be configured and verified.
  */
 public static boolean open(Player player,BlockPos pos){
  if(!(player.level().getBlockEntity(pos) instanceof GeneratorBlockEntity generator))return false;
  // The page toolkit stays optional: the reference below is only resolved when it is present.
  if(net.neoforged.fml.ModList.get().isLoaded("ldlib2")&&GeneratorLdlib.open(player,pos))return true;
  print(player,generator);
  return false;
 }
 /** Chat readout of every editable value, in field order. */
 private static void print(Player player,GeneratorBlockEntity generator){
  player.displayClientMessage(Component.translatable(generator.kind().labelKey()),false);
  for(var field:GeneratorFields.of(generator.kind())){
   String value=switch(field.type()){
    case TEXT->generator.text(field.id());
    case TOGGLE->Component.translatable(generator.flag(field.id())?"cbcmuzzlelight.value.on":"cbcmuzzlelight.value.off").getString();
    case NUMBER->String.format(java.util.Locale.ROOT,"%.2f",generator.number(field.id()));
   };
   player.displayClientMessage(Component.literal(field.id()+" = "+value),false);
  }
 }
}
