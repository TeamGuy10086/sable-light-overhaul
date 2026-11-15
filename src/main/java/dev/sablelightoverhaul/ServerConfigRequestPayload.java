package dev.sablelightoverhaul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The client asks for the server configuration; the server decides whether the player may see it. */
public record ServerConfigRequestPayload() implements CustomPacketPayload {
    public static final Type<ServerConfigRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "server_config_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigRequestPayload> CODEC =
            StreamCodec.of((buf, payload) -> {}, buf -> new ServerConfigRequestPayload());

    @Override
    public Type<ServerConfigRequestPayload> type() {
        return TYPE;
    }
}
