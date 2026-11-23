package dev.sablelightoverhaul.client.ui;

import dev.sablelightoverhaul.client.ClientChannels;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Shared configuration manager, also used by the mod-list button and device menus. */
public final class ConfigKey {
    public static final KeyMapping OPEN = new KeyMapping(
            "key.sablelightoverhaul.config", GLFW.GLFW_KEY_F9, "key.categories.sablelightoverhaul");

    private ConfigKey() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    /** The server answered with its catalogue: replace the client window with the server one. */
    public static void receiveServerCatalog(dev.sablelightoverhaul.ServerConfigCatalogPayload payload) {
        Minecraft.getInstance().setScreen(new ConfigWindow(
                new RemoteServerSource(payload.entries(), payload.writable())));
    }

    /** The server refused (or revoked) operator rights: close whatever config window is open and say why. */
    public static void denyServerConfig() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ConfigWindow) minecraft.setScreen(null);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("sablelightoverhaul.config.op_required"), false);
        }
    }

    public static boolean canRequestServerConfig() {
        return ClientChannels.has(dev.sablelightoverhaul.ServerConfigRequestPayload.TYPE)
                && ClientChannels.has(dev.sablelightoverhaul.ServerConfigCatalogPayload.TYPE)
                && ClientChannels.has(dev.sablelightoverhaul.ServerConfigDeniedPayload.TYPE);
    }

    public static void requestServerConfig() {
        if (!canRequestServerConfig()) return;
        ClientChannels.sendToServer(
                new dev.sablelightoverhaul.ServerConfigRequestPayload());
    }

    public static void tick(ClientTickEvent.Post event) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (minecraft.screen == null && minecraft.level != null) {
                minecraft.setScreen(new ConfigWindow(new TomlSource(
                        dev.sablelightoverhaul.client.MuzzleClientConfig.SPEC, java.util.Set.of())));
            }
        }
    }
}
