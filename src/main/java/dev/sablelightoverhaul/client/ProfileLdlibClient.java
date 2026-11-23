package dev.sablelightoverhaul.client;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import dev.sablelightoverhaul.SableLightOverhaul;
import dev.sablelightoverhaul.lighting.ProfileLdlib;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import dev.sablelightoverhaul.ProfileRequestPayload;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Binds the cannon form page's menu to its screen.
 *
 * <p>Client-only, and the reference to the page toolkit sits behind the loaded check, so this class is
 * harmless in a pack that has neither the toolkit nor its screen -- which is the same arrangement every
 * other page in this mod uses.
 */
@EventBusSubscriber(modid = SableLightOverhaul.ID, value = Dist.CLIENT)
public final class ProfileLdlibClient {
    private ProfileLdlibClient() {}

    /**
     * Asks for the whole per-cannon table, once, when this client joins.
     *
     * <p>Once is the point: the table is what a page and the drawing code read, so a client that has it does
     * not need to be told anything per shot. Changes arrive on their own afterwards.
     */
    @SubscribeEvent public static void joined(ClientPlayerNetworkEvent.LoggingIn event) {
        try {
            if (ClientChannels.has(dev.sablelightoverhaul.ProfileTablePayload.TYPE)) ClientChannels.sendToServer(ProfileRequestPayload.INSTANCE);
        } catch (RuntimeException failure) {
            // A table that cannot be asked for is not worth a crash on join; the page can ask again later.
        }
    }
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        if (ModList.get().isLoaded("ldlib2")) event.register(ProfileLdlib.MENU.get(), dev.sablelightoverhaul.client.ui.DeviceConfigWindow::new);
    }
}
