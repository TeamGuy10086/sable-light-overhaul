package dev.sablelightoverhaul.client.ui;

import java.util.*;
import java.util.function.*;

/** Client adapter for an existing, server-validated device menu. */
public class DeviceSource implements Source {
    private final String title;
    private final List<Group> groups = new ArrayList<>();
    private final Map<String, Supplier<FieldValue>> readers = new LinkedHashMap<>();
    private final Map<String, Consumer<FieldValue>> writers = new HashMap<>();
    private final List<Action> actions = new ArrayList<>();
    public DeviceSource(String title) { this.title = title; }
    public DeviceSource group(String category, String section, String topic, List<FieldSpec> fields) {
        groups.add(new Group(category, section, topic, List.copyOf(fields))); return this;
    }
    public DeviceSource bind(FieldSpec spec, Supplier<FieldValue> read, Consumer<FieldValue> write) {
        if (readers.containsKey(spec.key())) throw new IllegalArgumentException("Duplicate device key: " + spec.key());
        readers.put(spec.key(), read); if (write != null) writers.put(spec.key(), write); return this;
    }
    public DeviceSource action(String label, Runnable run) { actions.add(new Action(label, run)); return this; }
    @Override public String title() { return title; }
    @Override public boolean serverNavigation() { return false; }
    @Override public List<Action> actions() { return List.copyOf(actions); }
    @Override public List<Group> groups() { return List.copyOf(groups); }
    @Override public Map<String, FieldValue> read() {
        var result = new LinkedHashMap<String, FieldValue>();
        readers.forEach((key, reader) -> result.put(key, reader.get())); return result;
    }
    @Override public boolean canWrite(FieldSpec spec) { return writers.containsKey(spec.key()); }
    @Override public void write(FieldSpec spec, FieldValue value) {
        if (value instanceof FieldValue.Num n && (!Double.isFinite(n.value()) || n.value() < spec.min() || n.value() > spec.max())) return;
        if (value instanceof FieldValue.Choice c && (c.index() < 0 || c.index() >= spec.options().size())) return;
        if (value instanceof FieldValue.Text t && (t.text() == null || t.text().length() > 512)) return;
        var writer = writers.get(spec.key()); if (writer != null && value != null) writer.accept(value);
    }
}
