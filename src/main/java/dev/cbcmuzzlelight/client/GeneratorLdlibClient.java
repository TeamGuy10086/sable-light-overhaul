package dev.cbcmuzzlelight.client;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import dev.cbcmuzzlelight.MuzzleLight;
import dev.cbcmuzzlelight.lighting.GeneratorLdlib;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Binds the generator page's menu to its screen.
 *
 * <p>Client-only, and every reference to the page toolkit sits behind the loaded check, so the
 * class is harmless in a pack that has neither the toolkit nor its screen.
 */
@EventBusSubscriber(modid=MuzzleLight.ID,value=Dist.CLIENT)
public final class GeneratorLdlibClient {
 private GeneratorLdlibClient(){}
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){
  if(ModList.get().isLoaded("ldlib2"))event.register(GeneratorLdlib.MENU.get(),ModularUIContainerScreen::new);
 }
}
