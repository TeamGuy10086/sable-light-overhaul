package dev.sablelightoverhaul;

import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.profiles.ProfileWire;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One cannon's value changed, sent to everyone who is online.
 *
 * <p>Sent by the server when it stores a value, when it removes one, and when the maintenance pass drops a
 * cannon whose block is no longer a cannon. Receivers that have never asked for the table simply have no
 * cache to update and ignore it, which is why this carries the cannon's whole identity rather than an index.
 *
 * <p>{@code removed} exists because a deletion has no value to carry: sending a zero would make the client
 * believe the cannon is configured to zero, which is a different thing from not being configured at all.
 */
public record ProfileChangedPayload(EffectApi.SourceKey key, ResourceLocation kind, String name, double value,
                                    boolean removed) implements CustomPacketPayload {
    public static final Type<ProfileChangedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "profile_changed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileChangedPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                ProfileWire.SOURCE_KEY.encode(buf, payload.key());
                buf.writeResourceLocation(payload.kind());
                buf.writeUtf(payload.name());
                buf.writeDouble(payload.value());
                buf.writeBoolean(payload.removed());
            },
            buf -> new ProfileChangedPayload(ProfileWire.SOURCE_KEY.decode(buf), buf.readResourceLocation(),
                    buf.readUtf(), buf.readDouble(), buf.readBoolean()));

    @Override public Type<ProfileChangedPayload> type() { return TYPE; }
}
