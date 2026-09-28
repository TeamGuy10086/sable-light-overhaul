package dev.cbcmuzzlelight.client;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import dev.cbcmuzzlelight.MuzzleLight;
import dev.cbcmuzzlelight.lighting.DiscoBallLdlib;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Binds the disco ball page's menu to its screen.
 *
 * <p>Client-only, and the reference to the page toolkit sits behind the loaded check, so the class is
 * harmless in a pack that has neither the toolkit nor its screen. Without this the server opens the
 * menu and the client logs "Failed to create screen for menu type", which looks like the click doing
 * nothing at all.
 */
@EventBusSubscriber(modid=MuzzleLight.ID,value=Dist.CLIENT)
public final class DiscoBallLdlibClient {
 private DiscoBallLdlibClient(){}
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){
  if(ModList.get().isLoaded("ldlib2"))event.register(DiscoBallLdlib.MENU.get(),ModularUIContainerScreen::new);
 }
}
