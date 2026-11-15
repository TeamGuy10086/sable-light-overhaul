package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.api.EffectApi;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Which kind of shot a cannon's page was last looking at.
 *
 * <p>A view preference, not a value. Switching the kind must never write anything -- that rule is the whole
 * reason a form keeps one set of values per kind -- so remembering the choice cannot be stored with them. It
 * lives here instead, for the length of the session, keyed by the cannon's identity.
 *
 * <p>Per cannon rather than global, because the answer belongs to the cannon: a player who configured a tank
 * cannon and then a giant cannon expects each page to open where they left it.
 */
public final class ProfileViewMemory {
    /** Enough for a large fleet, small enough that a session cannot grow it without bound. */
    private static final int LIMIT = 256;

    private static final Map<String, EffectApi.WeaponKind> LAST = new LinkedHashMap<>();

    private ProfileViewMemory() {}

    private static String idOf(EffectApi.SourceKey key) {
        return key.dimension().location() + "|" + key.ship() + "|" + key.localPos().asLong();
    }

    public static Optional<EffectApi.WeaponKind> lastFor(EffectApi.SourceKey key) {
        if (key == null) return Optional.empty();
        synchronized (LAST) {
            return Optional.ofNullable(LAST.get(idOf(key)));
        }
    }

    public static void remember(EffectApi.SourceKey key, EffectApi.WeaponKind kind) {
        if (key == null || kind == null) return;
        synchronized (LAST) {
            if (LAST.size() >= LIMIT) LAST.clear();
            LAST.put(idOf(key), kind);
        }
    }
}
