package dev.sablelightoverhaul;

import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.profiles.ProfileWire;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One edited value, going from a cannon's page to the server.
 *
 * <p>One value per message, which is the same shape every other page in this mod uses: the page is a view,
 * each edit is sent as it happens, and the server is the only place the value is actually stored. Nothing
 * here is trusted -- the server checks the cannon's type against the type the edit names before writing, so a
 * page that claimed the wrong type would have its edit refused rather than misfiled.
 *
 * <p>The cannon travels as a whole identity rather than as a position, because a cannon on a ship has no
 * world position of its own: its position is in its body's frame, and the body is what makes it that cannon
 * and not another one of the same kind.
 */
public record ProfileEditPayload(EffectApi.SourceKey key, ResourceLocation typeId, String name, double value)
        implements CustomPacketPayload {
    public static final Type<ProfileEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "profile_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileEditPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                ProfileWire.SOURCE_KEY.encode(buf, payload.key());
                buf.writeResourceLocation(payload.typeId());
                buf.writeUtf(payload.name());
                buf.writeDouble(payload.value());
            },
            buf -> new ProfileEditPayload(ProfileWire.SOURCE_KEY.decode(buf),
                    buf.readResourceLocation(), buf.readUtf(), buf.readDouble()));

    @Override public Type<ProfileEditPayload> type() { return TYPE; }
}
