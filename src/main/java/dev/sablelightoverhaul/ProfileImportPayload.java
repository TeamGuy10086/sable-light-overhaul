package dev.sablelightoverhaul;

import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.profiles.ProfileWire;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A pasted form, going from a cannon's page to the server.
 *
 * <p>The string is parsed on the client and the result is sent as values, not as text. Two reasons, and the
 * second is the one that matters: parsing a player's clipboard on the server would mean accepting arbitrary
 * text from a client, and sending values means the server can check each one without ever seeing the text.
 * The type gate is then applied on the server as well, so a page that sent something for the wrong kind of
 * cannon has it refused rather than misfiled -- the client's own gate is a convenience, not the authority.
 *
 * <p>Every type in the paste travels, not just the one being shown. The selector is a view, so a paste that
 * only honoured the visible type would silently drop the rest of what the player copied.
 */
public record ProfileImportPayload(EffectApi.SourceKey key, Map<String, Map<String, Double>> byType)
        implements CustomPacketPayload {
    public static final Type<ProfileImportPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "profile_import"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileImportPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                ProfileWire.SOURCE_KEY.encode(buf, payload.key());
                buf.writeVarInt(payload.byType().size());
                payload.byType().forEach((type, values) -> {
                    buf.writeUtf(type);
                    buf.writeVarInt(values.size());
                    values.forEach((name, value) -> {
                        buf.writeUtf(name);
                        buf.writeDouble(value);
                    });
                });
            },
            buf -> {
                var key = ProfileWire.SOURCE_KEY.decode(buf);
                int types = buf.readVarInt();
                var byType = new LinkedHashMap<String, Map<String, Double>>();
                for (int type = 0; type < types; type++) {
                    String typeId = buf.readUtf();
                    int count = buf.readVarInt();
                    var values = new LinkedHashMap<String, Double>();
                    for (int value = 0; value < count; value++) values.put(buf.readUtf(), buf.readDouble());
                    byType.put(typeId, values);
                }
                return new ProfileImportPayload(key, Map.copyOf(byType));
            });

    @Override public Type<ProfileImportPayload> type() { return TYPE; }
}
