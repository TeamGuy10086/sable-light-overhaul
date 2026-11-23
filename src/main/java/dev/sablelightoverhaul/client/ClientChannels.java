package dev.sablelightoverhaul.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientChannels {
    private ClientChannels() {}

    public static boolean has(CustomPacketPayload.Type<?> type) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        if (has(payload.type())) PacketDistributor.sendToServer(payload);
    }
}
