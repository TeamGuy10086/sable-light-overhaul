package dev.cbcmuzzlelight.lighting;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/**
 * Opens the static light source's page, and says what it is set to when the page toolkit is missing.
 *
 * <p>The same shape as the lamps' opener, and the same reason for it: the toolkit is optional, so the
 * reference to the page class stays behind a mod-loaded check, and a player without it still gets the
 * current values in chat instead of a dead click.
 *
 * <p>The page is opened from the server, which is the side that holds the registration it edits, and
 * the position travels with it. Nothing here checks whether the player may build: reading the values is
 * not a write, and the page's own writes are gated where they happen.
 */
public final class LightSourceUi {
 private LightSourceUi(){}
 /**
  * Show the page for one stand-in.
  * @returns whether the page really opened; when it did not, every value was printed in chat instead.
  */
 public static boolean open(Player player,BlockPos pos){
  // Nothing to open from a client: the page is built by the client, but only the server can open it.
  if(player.level().isClientSide)return false;
  boolean ldlib=net.neoforged.fml.ModList.get().isLoaded("ldlib2");
  boolean opened=false;
  if(ldlib){
   try{
    opened=LightSourceLdlib.open(player,pos);
   }catch(Throwable problem){
    LogUtils.getLogger().error("CBC Muzzle Light light source page failed to open",problem);
   }
  }
  if(LightingCommon.GEN_DEBUG.get())LogUtils.getLogger().info("CBC Muzzle Light light source ui: ldlib2={} opened={} block={}",ldlib,opened,pos.toShortString());
  if(opened)return true;
  // Every value comes from the same place the page would read it, so the line reads like the page.
  LightSourceServer.report(player,pos);
  return false;
 }
 /** The gesture's entry point: show the page, or say what the light source is set to. */
 public static void openOrReport(Player player,BlockPos pos){open(player,pos);}
}
