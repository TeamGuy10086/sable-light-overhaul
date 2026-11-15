package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.ProfileChangedPayload;
import dev.sablelightoverhaul.ProfileTablePayload;
import dev.sablelightoverhaul.api.EffectApi;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * What the server last said, kept on the client so nothing has to be asked for per shot.
 *
 * <p>Three things live here: the presets the server owns, the values it holds for each cannon, and which mount
 * is carrying which cannon. A page reads the first two and shows a stored value where there is one and the
 * preset where there is not, which is why the two are kept apart rather than merged on arrival.
 *
 * <p>The mount table is what lets a shot be per-cannon. A flush names the mount that fired and a mount is a
 * definite coordinate, so this turns that name into the cannon a page filed its values under -- the thing a
 * client could not have worked out for itself, because a cannon's identity is decided on the server.
 *
 * <p>Presets for the systems the client owns are not here and must not be: those are this client's own
 * configuration, and a server has no business filling them in.
 */
public final class ProfileCache {
    /** Set once: whether a table ever arrived at all. */
    private static boolean LOGGED_ACCEPT;
    private static volatile Map<String, Double> presets = Map.of();
    private static final Map<String, Map<ResourceLocation, Map<String, Double>>> CANNONS = new LinkedHashMap<>();
    /** Which cannon each mount is carrying, as the server last said. */
    private static volatile Map<String, String> MOUNTS = Map.of();

    private ProfileCache() {}

    private static String idOf(EffectApi.SourceKey key) {
        // One rule, one place: the server writes its mount table with the same call.
        return key.stableId();
    }

    /**
     * Takes a table the server sent: the whole thing replaces what is held, an announcement only updates the
     * cannons it names.
     *
     * <p>The distinction matters in one direction only, and it is the direction that would be silent: merging a
     * full table would keep cannons the server has since forgotten, which then look configured to a client that
     * will never be told otherwise.
     */
    public static synchronized void accept(ProfileTablePayload table) {
        presets = table.presets();
        MOUNTS = table.mounts();
        if (!LOGGED_ACCEPT) {
            LOGGED_ACCEPT = true;
            com.mojang.logging.LogUtils.getLogger().info(
                    "Sable Light Overhaul: table received ({} preset(s), {} cannon(s), {} mount(s), full={})",
                    table.presets().size(), table.cannons().size(), table.mounts().size(), table.full());
        }
        if (table.full()) CANNONS.clear();
        for (var cannon : table.cannons()) {
            var id = idOf(cannon.key());
            if (cannon.values().isEmpty()) {
                // Named with nothing left in it: the server has dropped this cannon, and so do we.
                var byKind = CANNONS.get(id);
                if (byKind != null) {
                    byKind.remove(cannon.kind());
                    if (byKind.isEmpty()) CANNONS.remove(id);
                }
                continue;
            }
            CANNONS.computeIfAbsent(id, key -> new LinkedHashMap<>()).put(cannon.kind(), cannon.values());
        }
    }

    /**
     * Which cannon a mount is carrying, or empty when the server has not said.
     *
     * <p>This is how a shot finds its cannon: the flush names the mount that fired, and the mount is a definite
     * coordinate whether it stands in the world or inside a body, so the cannon is a lookup and not a guess.
     */
    /** How many mounts the server has told this client about. */
    public static int mountCount() { return MOUNTS.size(); }
    public static Optional<String> cannonAt(ResourceLocation dimension, long packedMountPos) {
        if (dimension == null) return Optional.empty();
        return Optional.ofNullable(MOUNTS.get(dimension + "|" + packedMountPos));
    }

    /** Every value the server holds for a cannon named by its stable id, for the callers that only get an id. */
    public static synchronized Map<String, Double> storedAllById(String cannonId, ResourceLocation kind) {
        if (cannonId == null) return Map.of();
        var byKind = CANNONS.get(cannonId);
        return byKind == null ? Map.of() : byKind.getOrDefault(kind, Map.of());
    }

    /** One value, if the server holds one for this cannon and kind. */
    public static synchronized Optional<Double> stored(EffectApi.SourceKey key, ResourceLocation kind, String name) {
        var byKind = CANNONS.get(idOf(key));
        if (byKind == null) return Optional.empty();
        var values = byKind.get(kind);
        return values == null ? Optional.empty() : Optional.ofNullable(values.get(name));
    }

    /** Every value the server holds for this cannon and kind. */
    public static synchronized Map<String, Double> storedAll(EffectApi.SourceKey key, ResourceLocation kind) {
        var byKind = CANNONS.get(idOf(key));
        if (byKind == null) return Map.of();
        return byKind.getOrDefault(kind, Map.of());
    }

    /** Which paths the server sent presets for. This is the key set the server decided was worth sending. */
    public static java.util.Set<String> presetPaths() {
        return presets.keySet();
    }

    /** The server's preset for a path, or empty when the client owns that system and has its own value. */
    public static Optional<Double> preset(String path) {
        return Optional.ofNullable(presets.get(path));
    }

    /** One change, as it arrives. A removal drops the entry rather than storing a zero. */
    public static synchronized void apply(ProfileChangedPayload change) {
        var byKind = CANNONS.computeIfAbsent(idOf(change.key()), id -> new LinkedHashMap<>());
        var values = byKind.computeIfAbsent(change.kind(), kind -> new LinkedHashMap<>());
        if (change.removed()) values.remove(change.name());
        else values.put(change.name(), change.value());
        if (values.isEmpty()) byKind.remove(change.kind());
    }

    /** Forget everything: a disconnect, a reload, or a client that wants to ask again. */
    public static synchronized void clear() {
        presets = Map.of();
        MOUNTS = Map.of();
        CANNONS.clear();
    }
}
