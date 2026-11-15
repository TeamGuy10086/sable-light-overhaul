package dev.sablelightoverhaul.client.ui;

import dev.sablelightoverhaul.ServerConfigCatalogPayload;
import dev.sablelightoverhaul.ServerConfigEditPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The server configuration, rendered by the same window as the client one.
 *
 * <p>The values are a snapshot from the moment the catalogue arrived; a write is optimistic locally and one
 * packet to the server, which is the only side that can actually change the file. If the server decides the
 * player may not write, it answers with the denied payload and the window closes.
 */
public final class RemoteServerSource implements Source {
    private final List<Group> groups;
    private final Map<String, FieldValue> values;
    private final boolean writable;

    public RemoteServerSource(List<ServerConfigCatalogPayload.Entry> entries, boolean writable) {
        Map<String, FieldValue> read = new LinkedHashMap<>();
        List<Group> built = new ArrayList<>();
        String category = null;
        String section = null;
        String topic = null;
        List<FieldSpec> fields = new ArrayList<>();
        for (ServerConfigCatalogPayload.Entry entry : entries) {
            if (!entry.category().equals(category) || !entry.section().equals(section)
                    || !entry.topic().equals(topic)) {
                if (!fields.isEmpty()) {
                    built.add(new Group(category, section, topic, List.copyOf(fields)));
                    fields.clear();
                }
                category = entry.category();
                section = entry.section();
                topic = entry.topic();
            }
            fields.add(entry.spec());
            read.put(entry.spec().key(), entry.value());
        }
        if (!fields.isEmpty()) built.add(new Group(category, section, topic, List.copyOf(fields)));
        this.groups = List.copyOf(built);
        this.values = read;
        this.writable = writable;
    }

    @Override
    public List<Group> groups() {
        return groups;
    }

    @Override
    public Map<String, FieldValue> read() {
        return new LinkedHashMap<>(values);
    }

    @Override
    public void write(FieldSpec spec, FieldValue value) {
        values.put(spec.key(), value);
        PacketDistributor.sendToServer(new ServerConfigEditPayload(spec.key(), value));
    }

    @Override
    public boolean canWrite(FieldSpec spec) {
        return writable;
    }
}
