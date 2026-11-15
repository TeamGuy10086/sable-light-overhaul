package dev.sablelightoverhaul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A client asking for the whole table, once.
 *
 * <p>Sent when the client joins and when a page is opened after a reload, never per shot and never on a
 * timer. An empty marker rather than a request for a subset: the table is small, the interesting part is that
 * it is asked for once, and a client that asked for pieces could miss one.
 */
public record ProfileRequestPayload() implements CustomPacketPayload {
    public static final ProfileRequestPayload INSTANCE = new ProfileRequestPayload();

    public static final Type<ProfileRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "profile_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileRequestPayload> CODEC = StreamCodec.of(
            (buf, payload) -> { },
            buf -> INSTANCE);

    @Override public Type<ProfileRequestPayload> type() { return TYPE; }
}
