package dev.sablelightoverhaul.profiles;

import dev.sablelightoverhaul.api.EffectApi;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * The cannon identity, on the wire.
 *
 * <p>Deliberately a plain {@link ByteBuf} codec rather than a registry-aware one. Everything in the identity
 * is already primitive -- a dimension name, a body id, a position, a type name and an optional custom name --
 * and writing strings by hand costs a couple of lines in exchange for a codec that can be round-tripped in a
 * unit test. A registry-aware buffer cannot be built outside a running game, which would have left the only
 * test of this being the first time somebody opened a page.
 *
 * <p>Strings carry their own length as an int, and the three optional things carry a flag first, because the
 * absence of a body is meaningful rather than a failure: no body means the position is world space.
 */
public final class ProfileWire {
    private ProfileWire() {}

    /** Writes everything {@link EffectApi.SourceKey} carries, and nothing it does not. */
    public static final StreamCodec<ByteBuf, EffectApi.SourceKey> SOURCE_KEY = StreamCodec.of(
            (buf, key) -> {
                writeString(buf, key.dimension().location().toString());
                buf.writeBoolean(key.ship() != null);
                if (key.ship() != null) {
                    buf.writeLong(key.ship().getMostSignificantBits());
                    buf.writeLong(key.ship().getLeastSignificantBits());
                }
                buf.writeLong(key.localPos().asLong());
                writeString(buf, key.typeId().toString());
                buf.writeBoolean(key.customName() != null);
                if (key.customName() != null) writeString(buf, key.customName());
            },
            buf -> {
                var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(readString(buf)));
                UUID ship = buf.readBoolean() ? new UUID(buf.readLong(), buf.readLong()) : null;
                var pos = BlockPos.of(buf.readLong());
                var typeId = ResourceLocation.parse(readString(buf));
                String customName = buf.readBoolean() ? readString(buf) : null;
                return new EffectApi.SourceKey(dimension, ship, pos, typeId, customName);
            });

    /** A length-prefixed UTF-8 string, which is what a plain buffer has instead of writeUtf. */
    static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    static String readString(ByteBuf buf) {
        byte[] bytes = new byte[buf.readInt()];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
