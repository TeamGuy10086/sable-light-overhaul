package dev.sablelightoverhaul;

import dev.sablelightoverhaul.client.ui.FieldValue;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One field of the server configuration, on its way back to the server that owns it. */
public record ServerConfigEditPayload(String key, FieldValue value) implements CustomPacketPayload {
    public static final Type<ServerConfigEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "server_config_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigEditPayload> CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeUtf(payload.key, 512);
                ServerConfigCatalogPayload.writeValue(buf, payload.value);
            }, buf -> new ServerConfigEditPayload(buf.readUtf(512), ServerConfigCatalogPayload.readValue(buf)));

    @Override
    public Type<ServerConfigEditPayload> type() {
        return TYPE;
    }
}
