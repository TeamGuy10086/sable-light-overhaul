package dev.cbcmuzzlelight.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Opens the disco ball's page, and says why it cannot when the page toolkit is missing.
 *
 * <p>The toolkit is optional, so the reference to the page class is kept behind a mod-loaded check:
 * without it a player still gets the current settings in chat instead of a dead click.
 */
public final class DiscoBallUi {
 private DiscoBallUi(){}
 public static boolean open(Player player,BlockPos pos){
  boolean ldlib=net.neoforged.fml.ModList.get().isLoaded("ldlib2");
  boolean opened=false;
  if(ldlib){
   try{
    opened=DiscoBallLdlib.open(player,pos);
   }catch(Throwable problem){
    com.mojang.logging.LogUtils.getLogger().error("CBC Muzzle Light disco page failed to open",problem);
   }
  }
  boolean hasBall=player.level().getBlockEntity(pos) instanceof DiscoBallBlockEntity;
  if(LightingCommon.GEN_DEBUG.get())com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light disco ui: ldlib2={} opened={} blockEntity={}",ldlib,opened,hasBall);
  if(opened)return true;
  if(!(player.level().getBlockEntity(pos) instanceof DiscoBallBlockEntity ball))return false;
  if(!player.level().isClientSide){
   player.displayClientMessage(Component.literal("迪斯科灯球："+ball.beams()+" 束 / 颜色 "+ball.text("colors")
     +" / 转速 "+String.format(java.util.Locale.ROOT,"%.2f",ball.number("spin"))
     +" / 范围 "+ball.range()),false);
  }
  return true;
 }
}
