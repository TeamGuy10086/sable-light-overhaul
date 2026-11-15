package dev.sablelightoverhaul;

import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.profiles.ProfileWire;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The whole per-cannon table, sent to one client on request.
 *
 * <p>One message rather than a stream of them, because the point is to stop sending: a client asks once when
 * it joins and then hears only about changes. The catalogue itself is not carried -- both sides derive it from
 * the same code -- so this is the presets the server owns plus every cannon's stored values.
 *
 * <p>Presets for the systems the client owns are deliberately absent: those are configured on the client, so
 * the client already has them and sending them would let a server overwrite a player's own settings.
 */
public record ProfileTablePayload(List<Cannon> cannons, Map<String, Double> presets, boolean full, Map<String, String> mounts) implements CustomPacketPayload {
    /**
     * @param full true for the answer to a request, which replaces what the client holds; false for the
     *             announcement after a page closes, which only updates the cannons it names. Merging a full
     *             table would keep cannons the server has since forgotten, and keep them silently.
     */
    /** One cannon's stored values, for one kind of shot. */
    public record Cannon(EffectApi.SourceKey key, ResourceLocation kind, Map<String, Double> values) {}

    public static final Type<ProfileTablePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "profile_table"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileTablePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.full());
                // Which mount carries which cannon: one entry per cannon that has fired, sent with every table so
                // that it heals itself rather than needing invalidation rules of its own.
                buf.writeVarInt(payload.mounts().size());
                payload.mounts().forEach((mount, cannon) -> { buf.writeUtf(mount); buf.writeUtf(cannon); });
                buf.writeVarInt(payload.presets().size());
                payload.presets().forEach((name, value) -> { buf.writeUtf(name); buf.writeDouble(value); });
                buf.writeVarInt(payload.cannons().size());
                for (var cannon : payload.cannons()) {
                    ProfileWire.SOURCE_KEY.encode(buf, cannon.key());
                    buf.writeResourceLocation(cannon.kind());
                    buf.writeVarInt(cannon.values().size());
                    cannon.values().forEach((name, value) -> { buf.writeUtf(name); buf.writeDouble(value); });
                }
            },
            buf -> {
                boolean full = buf.readBoolean();
                int mountCount = buf.readVarInt();
                var mounts = new LinkedHashMap<String, String>();
                for (int i = 0; i < mountCount; i++) mounts.put(buf.readUtf(), buf.readUtf());
                int presetCount = buf.readVarInt();
                var presets = new LinkedHashMap<String, Double>();
                for (int i = 0; i < presetCount; i++) presets.put(buf.readUtf(), buf.readDouble());
                int cannonCount = buf.readVarInt();
                var cannons = new ArrayList<Cannon>(Math.min(cannonCount, 4096));
                for (int i = 0; i < cannonCount; i++) {
                    var key = ProfileWire.SOURCE_KEY.decode(buf);
                    var kind = buf.readResourceLocation();
                    int valueCount = buf.readVarInt();
                    var values = new LinkedHashMap<String, Double>();
                    for (int v = 0; v < valueCount; v++) values.put(buf.readUtf(), buf.readDouble());
                    cannons.add(new Cannon(key, kind, Map.copyOf(values)));
                }
                return new ProfileTablePayload(List.copyOf(cannons), Map.copyOf(presets), full, Map.copyOf(mounts));
            });

    @Override public Type<ProfileTablePayload> type() { return TYPE; }
}
