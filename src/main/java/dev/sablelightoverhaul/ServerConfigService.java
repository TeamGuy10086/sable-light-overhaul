package dev.sablelightoverhaul;

import dev.sablelightoverhaul.client.ui.FieldSpec;
import dev.sablelightoverhaul.client.ui.FieldValue;
import dev.sablelightoverhaul.client.ui.TomlSource;
import dev.sablelightoverhaul.lighting.LightingCommon;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The server half of the configuration page.
 *
 * <p>It uses the same {@link TomlSource} the client uses, on the server's own spec. That keeps one description of
 * a key (range, kind, comment) instead of a second hand-written table; only the transport is different.
 *
 * <p>Every entry point checks the operator bit again. The client hides the button from non-ops, but a client is
 * not a trust boundary.
 */
public final class ServerConfigService {
    private static final Object LOCK = new Object();
    private static TomlSource catalog;

    private ServerConfigService() {}

    private static TomlSource catalog() {
        synchronized (LOCK) {
            if (catalog == null) catalog = new TomlSource(LightingCommon.SPEC, Set.of());
            return catalog;
        }
    }

    /** The catalogue as wire entries, in the order the source grouped them. */
    public static List<ServerConfigCatalogPayload.Entry> snapshot() {
        TomlSource source = catalog();
        Map<String, FieldValue> values = source.read();
        return source.groups().stream()
                .flatMap(group -> group.fields().stream()
                        .map(spec -> new ServerConfigCatalogPayload.Entry(
                                group.category(), group.section(), group.topic(), spec, values.get(spec.key()))))
                .toList();
    }

    /** Applies one edit, if the key exists in the server spec. Returns false for an unknown key. */
    public static boolean apply(String key, FieldValue value) {
        TomlSource source = catalog();
        FieldSpec spec = source.spec(key);
        if (spec == null || value == null) return false;
        synchronized (LOCK) {
            source.write(spec, value);
            source.flush();
        }
        return true;
    }

    /** A value snapshot for one key, used by the edit reply if the UI ever needs to correct itself. */
    public static FieldValue value(String key) {
        return catalog().read().get(key);
    }
}
