package dev.sablelightoverhaul;

import dev.sablelightoverhaul.client.ui.FieldSpec;
import dev.sablelightoverhaul.client.ui.FieldValue;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The server's configuration catalogue, plus its values, on its way to the operator's client.
 *
 * <p>The client cannot read the server's toml file, so the catalogue is built on the server with the same
 * {@code TomlSource} the client uses and sent as data. Each entry carries its own three-level place because
 * the grouping is a decision made on the server; the client only renders it.
 *
 * <p>Strings are capped per field rather than one big blob: a malformed packet then fails at the field that is
 * too long instead of taking the connection with it.
 */
public record ServerConfigCatalogPayload(List<Entry> entries, boolean writable) implements CustomPacketPayload {
    public record Entry(String category, String section, String topic, FieldSpec spec, FieldValue value) {}

    public static final Type<ServerConfigCatalogPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "server_config_catalog"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerConfigCatalogPayload> CODEC =
            StreamCodec.of(ServerConfigCatalogPayload::write, ServerConfigCatalogPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ServerConfigCatalogPayload payload) {
        buf.writeVarInt(payload.entries().size());
        for (Entry entry : payload.entries) {
            buf.writeUtf(entry.category, 128);
            buf.writeUtf(entry.section, 128);
            buf.writeUtf(entry.topic, 128);
            FieldSpec spec = entry.spec();
            buf.writeUtf(spec.key(), 512);
            buf.writeUtf(spec.labelKey() == null ? "" : spec.labelKey(), 512);
            buf.writeUtf(spec.label(), 512);
            buf.writeUtf(spec.tooltip() == null ? "" : spec.tooltip(), 16384);
            buf.writeByte(spec.kind().ordinal());
            buf.writeDouble(spec.min());
            buf.writeDouble(spec.max());
            buf.writeDouble(spec.step());
            buf.writeVarInt(spec.options().size());
            for (String option : spec.options()) buf.writeUtf(option, 128);
            buf.writeBoolean(spec.needsRestart());
            writeValue(buf, entry.value);
        }
        buf.writeBoolean(payload.writable());
    }

    private static ServerConfigCatalogPayload read(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String category = buf.readUtf(128);
            String section = buf.readUtf(128);
            String topic = buf.readUtf(128);
            String key = buf.readUtf(512);
            String labelKey = buf.readUtf(512);
            String label = buf.readUtf(512);
            String tooltip = buf.readUtf(16384);
            FieldSpec.Kind kind = FieldSpec.Kind.values()[buf.readByte()];
            double min = buf.readDouble();
            double max = buf.readDouble();
            double step = buf.readDouble();
            int optionCount = buf.readVarInt();
            List<String> options = new ArrayList<>(optionCount);
            for (int j = 0; j < optionCount; j++) options.add(buf.readUtf(128));
            boolean restart = buf.readBoolean();
            FieldValue value = readValue(buf);
            entries.add(new Entry(category, section, topic,
                    new FieldSpec(key, section, label, tooltip, kind, min, max, step,
                            List.copyOf(options), restart, labelKey.isBlank() ? null : labelKey),
                    value));
        }
        return new ServerConfigCatalogPayload(List.copyOf(entries), buf.readBoolean());
    }

    public static void writeValue(RegistryFriendlyByteBuf buf, FieldValue value) {
        if (value instanceof FieldValue.Num num) {
            buf.writeByte(0);
            buf.writeDouble(num.value());
        } else if (value instanceof FieldValue.Bool bool) {
            buf.writeByte(1);
            buf.writeBoolean(bool.on());
        } else if (value instanceof FieldValue.Text text) {
            buf.writeByte(2);
            buf.writeUtf(text.text(), 16384);
        } else if (value instanceof FieldValue.Choice choice) {
            buf.writeByte(3);
            buf.writeVarInt(choice.index());
            buf.writeUtf(choice.name(), 128);
        } else {
            throw new IllegalArgumentException("unknown FieldValue " + value);
        }
    }

    public static FieldValue readValue(RegistryFriendlyByteBuf buf) {
        return switch (buf.readByte()) {
            case 0 -> new FieldValue.Num(buf.readDouble());
            case 1 -> new FieldValue.Bool(buf.readBoolean());
            case 2 -> new FieldValue.Text(buf.readUtf(16384));
            case 3 -> new FieldValue.Choice(buf.readVarInt(), buf.readUtf(128));
            default -> throw new IllegalArgumentException("unknown FieldValue tag");
        };
    }

    @Override
    public Type<ServerConfigCatalogPayload> type() {
        return TYPE;
    }
}
