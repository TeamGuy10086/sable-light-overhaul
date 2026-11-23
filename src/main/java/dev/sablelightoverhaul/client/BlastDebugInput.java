package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.BlastDebugItem;
import dev.sablelightoverhaul.BlastPreviewPayload;
import dev.sablelightoverhaul.SableLightOverhaul;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = SableLightOverhaul.ID, value = Dist.CLIENT)
public final class BlastDebugInput {
    private BlastDebugInput() {}

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var connection = mc.getConnection();
        double delta = event.getScrollDeltaY();
        if (mc.screen != null || player == null || !player.isCreative() || !player.hasPermissions(2)
            || !(player.getMainHandItem().getItem() instanceof BlastDebugItem)
            || connection == null || !connection.hasChannel(BlastPreviewPayload.Adjust.TYPE) || delta == 0) return;
        event.setCanceled(true);
        PacketDistributor.sendToServer(new BlastPreviewPayload.Adjust(mc.options.keyShift.isDown(), delta > 0));
    }
}
