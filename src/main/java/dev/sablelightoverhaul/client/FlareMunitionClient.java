package dev.sablelightoverhaul.client;
import dev.sablelightoverhaul.SableLightOverhaul;
import dev.sablelightoverhaul.munitions.FlareMunitions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import rbasamoyai.createbigcannons.munitions.big_cannon.BigCannonProjectileRenderer;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntityRenderer;
@EventBusSubscriber(modid=SableLightOverhaul.ID,value=Dist.CLIENT)
public final class FlareMunitionClient {
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(FlareMunitions.ALTITUDE_MENU.get(),AltitudeFuzeScreen::new);}
 @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){
  event.registerEntityRenderer(FlareMunitions.SHELL_ENTITY.get(),BigCannonProjectileRenderer::new);
  event.registerEntityRenderer(FlareMunitions.SIGNAL_ENTITY.get(),BigCannonProjectileRenderer::new);
  event.registerBlockEntityRenderer(FlareMunitions.SHELL_BLOCK_ENTITY.get(),FuzedBlockEntityRenderer::new);
 }
}
