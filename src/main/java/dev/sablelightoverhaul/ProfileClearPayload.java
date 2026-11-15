package dev.sablelightoverhaul;

import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.profiles.ProfileWire;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Values to forget, going from a cannon's page to the server.
 *
 * <p>The page shows a per-cannon value when the cannon has one and the kind's preset when it does not, so
 * "restore the default" is not a value to write -- it is the record to remove. Two buttons need exactly that:
 * the one on a single row, and the one that restores a whole page, which is the same message with every path
 * on the page in it.
 *
 * <p>An empty list means every value this cannon holds for this kind.
 *
 * <p>Nothing here is trusted either: the server checks the cannon the same way an edit does before removing
 * anything, so a page that named the wrong cannon loses its message rather than another cannon's values.
 */
public record ProfileClearPayload(EffectApi.SourceKey key, ResourceLocation typeId, List<String> names)
        implements CustomPacketPayload {
    public static final Type<ProfileClearPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "profile_clear"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileClearPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                ProfileWire.SOURCE_KEY.encode(buf, payload.key());
                buf.writeResourceLocation(payload.typeId());
                buf.writeVarInt(payload.names().size());
                for (var name : payload.names()) buf.writeUtf(name);
            },
            buf -> {
                var key = ProfileWire.SOURCE_KEY.decode(buf);
                var typeId = buf.readResourceLocation();
                int count = buf.readVarInt();
                var names = new ArrayList<String>(count);
                for (int i = 0; i < count; i++) names.add(buf.readUtf());
                return new ProfileClearPayload(key, typeId, List.copyOf(names));
            });

    @Override public Type<ProfileClearPayload> type() { return TYPE; }
}