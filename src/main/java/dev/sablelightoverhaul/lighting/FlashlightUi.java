package dev.sablelightoverhaul.lighting;

import dev.sablelightoverhaul.LocalizedText;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Opens the flashlight's page, and says what it is set to when the page toolkit is missing.
 *
 * <p>The same shape as the lamps' opener with one thing less to go wrong: these settings belong to a
 * player rather than to a block, so there is no position to carry and nothing that can be broken
 * between the click and the page appearing. What is left is the toolkit being optional, so the
 * reference to the page class stays behind a mod-loaded check and a player without it still gets the
 * current settings in chat instead of a dead click.
 *
 * <p>The values are operator-gated, so a player without the rights is refused here rather than shown a
 * page whose every edit the server would silently throw away.
 */
public final class FlashlightUi {
 private FlashlightUi(){}
 public static boolean open(Player player){
  if(!player.hasPermissions(2))return false;
  boolean ldlib=net.neoforged.fml.ModList.get().isLoaded("ldlib2");
  boolean opened=false;
  if(ldlib){
   try{
    opened=FlashlightLdlib.open(player);
   }catch(Throwable problem){
    com.mojang.logging.LogUtils.getLogger().error("Sable Light Overhaul flashlight page failed to open",problem);
   }
  }
  if(LightingCommon.on(LightingCommon.GEN_DEBUG))com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul flashlight ui: ldlib2={} opened={}",ldlib,opened);
  if(opened)return true;
  if(!player.level().isClientSide){
   // Every value comes from this player's own settings, so the line reads exactly like the page would.
   // The range is stored as a step, so the blocks it means are printed beside it: "3" alone would not say
   // how long the beam is. Width is already an angle in degrees and decay a percentage, so both are
   // printed as they are stored.
   int step=(int)Math.round(FlashlightSettings.number(player,"range"));
   player.displayClientMessage(LocalizedText.component("手电筒：颜色 %s / 距离 %s 步（%s 格） / 宽度 %s° / 衰减模式 %s / 距离衰减 %s%% / 强度 %s / 点光半径 %s / 点光亮度 %s",
     whole(player,"color"),step,LightingCommon.distance(step),whole(player,"width"),mode(player),
     whole(player,"decay"),whole(player,"intensity"),whole(player,"pointRadius"),whole(player,"pointIntensity")),false);
  }
  return true;
 }
 private static String whole(Player player,String field){return Long.toString(Math.round(FlashlightSettings.number(player,field)));}
 /** The cross-section's profile, by the name the spotlights' page shows for the same index. */
 private static Component mode(Player player){
  int index=(int)Math.round(FlashlightSettings.number(player,"falloff"));
  return LocalizedText.component(SpotlightBlock.MODES[Math.max(0,Math.min(SpotlightBlock.MODES.length-1,index))]);
 }
}
