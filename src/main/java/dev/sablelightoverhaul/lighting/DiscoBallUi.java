package dev.sablelightoverhaul.lighting;

import dev.sablelightoverhaul.LocalizedText;
import net.minecraft.core.BlockPos;
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
    com.mojang.logging.LogUtils.getLogger().error("Sable Light Overhaul disco page failed to open",problem);
   }
  }
  boolean hasBall=player.level().getBlockEntity(pos) instanceof DiscoBallBlockEntity;
  if(LightingCommon.on(LightingCommon.GEN_DEBUG))com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul disco ui: ldlib2={} opened={} blockEntity={}",ldlib,opened,hasBall);
  if(opened)return true;
  if(!(player.level().getBlockEntity(pos) instanceof DiscoBallBlockEntity ball))return false;
  if(!player.level().isClientSide){
   player.displayClientMessage(LocalizedText.component("迪斯科灯球：%s 束 / 颜色 %s / 转速 %s / 范围 %s",
     ball.beams(),ball.text("colors"),String.format(java.util.Locale.ROOT,"%.2f",ball.number("spin")),ball.range()),false);
  }
  return true;
 }
}
