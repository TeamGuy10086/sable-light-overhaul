package dev.sablelightoverhaul;

import java.util.HashSet;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@EventBusSubscriber(modid = SableLightOverhaul.ID)
public final class SloNetwork {
    private SloNetwork() {}

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload.type())) PacketDistributor.sendToPlayer(player, payload);
    }

    @SubscribeEvent
    public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var channels = new HashSet<>(NetworkRegistry.getCommonPlayChannels(PacketFlow.CLIENTBOUND));
        channels.addAll(NetworkRegistry.getCommonPlayChannels(PacketFlow.SERVERBOUND));
        channels.removeIf(id -> !SableLightOverhaul.ID.equals(id.getNamespace()));
        var missing = channels.stream().filter(id -> !player.connection.hasChannel(id)).sorted().toList();
        if (missing.isEmpty()) return;
        boolean chinese = player.clientInformation().language().startsWith("zh");
        // Vanilla system chat also works for a client without SLO or its language resources.
        String text = chinese ? "[SLO] 此连接有 %s/%s 个模组通道不可用。依赖它们的功能将停用或使用兼容效果。"
            : "[SLO] %s/%s mod channels are unavailable on this connection. Dependent features are disabled or use a fallback.";
        player.sendSystemMessage(Component.literal(text.formatted(missing.size(), channels.size())));
        com.mojang.logging.LogUtils.getLogger().info("SLO channels unavailable for {}: {}", player.getGameProfile().getName(), missing);
    }
}
