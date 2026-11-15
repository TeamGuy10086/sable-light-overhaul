package dev.sablelightoverhaul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent instead of the catalogue when the player is not an operator.
 *
 * <p>A dedicated payload rather than a flag on the catalogue: the client must close the window and say why,
 * and a denied request has no catalogue to decode.
 */
public record ServerConfigDeniedPayload() implements CustomPacketPayload {
    public static final Type<ServerConfigDeniedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "server_config_denied"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigDeniedPayload> CODEC =
            StreamCodec.of((buf, payload) -> {}, buf -> new ServerConfigDeniedPayload());

    @Override
    public Type<ServerConfigDeniedPayload> type() {
        return TYPE;
    }
}
