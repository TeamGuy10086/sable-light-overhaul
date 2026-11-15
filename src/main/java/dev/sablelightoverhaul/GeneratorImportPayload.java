package dev.sablelightoverhaul;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A pasted generator form, going from its page to the server.
 *
 * <p>The same shape as the cannon's import, and for the same reasons: the clipboard is parsed on the client
 * and the result is sent as values rather than as text, so the server never has to read arbitrary text from a
 * client, and every value is checked individually before it is stored.
 *
 * <p>A generator is a block, so the target is a position rather than an identity: unlike a cannon on a
 * ship, there is only ever one place it can be. The field names are the page own field ids, and the server
 * checks each one against the kind schema, so a paste that names a field this generator does not have is
 * refused one value at a time rather than refusing the whole paste.
 *
 * <p>Values travel as text rather than as numbers, because a generator has text fields too -- the particle a
 * smoke vent emits is one -- and a paste has to be able to carry them. The server routes each value by what it
 * looks like, which is what the three edit overloads already distinguish.
 */
public record GeneratorImportPayload(BlockPos pos, Map<String, String> values) implements CustomPacketPayload {
    public static final Type<GeneratorImportPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "generator_import"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GeneratorImportPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeVarInt(payload.values().size());
                payload.values().forEach((name, value) -> {
                    buf.writeUtf(name);
                    buf.writeUtf(value == null ? "" : value);
                });
            },
            buf -> {
                var pos = buf.readBlockPos();
                int count = buf.readVarInt();
                var values = new java.util.LinkedHashMap<String, String>();
                for (int value = 0; value < count; value++) values.put(buf.readUtf(), buf.readUtf());
                return new GeneratorImportPayload(pos, Map.copyOf(values));
            });

    @Override public Type<GeneratorImportPayload> type() { return TYPE; }
}
