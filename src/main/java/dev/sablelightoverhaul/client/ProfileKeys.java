package dev.sablelightoverhaul.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import dev.sablelightoverhaul.profiles.ProfileCatalogue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The keys a page shows, and where their preset value comes from.
 *
 * <p>One rule, applied in one place: a system the server owns takes its keys and its preset from the table the
 * server sent, and a system the client owns takes both from this client's own configuration files. That is the
 * same split the fourteen light slots have always used -- the server holds what it can see, the client holds
 * what only it can see -- written down once instead of assumed at every row.
 *
 * <p>The client configuration is read here rather than asked of the server, and that is deliberate: a player's
 * light profile and particle shapes are theirs, and a server must not be able to overwrite them by sending a
 * table.
 */
public final class ProfileKeys {
    private ProfileKeys() {}

    /** The client's own configuration as dotted paths. Rebuilt on each call: these files change on reload. */
    public static Map<String, Object> clientValues() {
        var out = new LinkedHashMap<String, Object>();
        for (var spec : specs()) {
            try {
                if (spec.isLoaded()) flatten(spec.getValues(), "", out);
            } catch (RuntimeException failure) {
                // A spec that cannot answer contributes nothing rather than breaking the page.
            }
        }
        return out;
    }

    /**
     * This mod's client configuration.
     *
     * <p>One spec, not two: {@code ExtraConfig} contributes its keys through {@code define(builder)} and does
     * not build a spec of its own, so everything the client owns arrives in this one map.
     */
    private static List<net.neoforged.neoforge.common.ModConfigSpec> specs() {
        return List.of(MuzzleClientConfig.SPEC);
    }

    /**
     * The keys of one system, in a stable order.
     *
     * <p>Server side the keys are the ones the server actually sent presets for, which is the set it decided
     * was worth sending; client side they are whatever this client's own files have under the system's paths.
     */
    public static List<String> keysFor(ProfileCatalogue.System system) {
        var keys = new ArrayList<String>();
        if (system.side() == ProfileCatalogue.Side.CLIENT) {
            for (var path : clientValues().keySet()) {
                if (system.covers(path) && ProfileCatalogue.asNumber(clientValues().get(path)) != null) keys.add(path);
            }
        } else {
            // The cache holds the server's presets, which is exactly the key set the server sent.
            for (var path : ProfileCache.presetPaths()) {
                if (system.covers(path)) keys.add(path);
            }
        }
        // One line whenever a system comes up empty: that is the only case a reader cannot see for themselves,
        // and it separates "the table has not arrived" from "the paths do not match".
        String sample = clientValues().keySet().stream().sorted().findFirst().orElse("(none)");
        if (keys.isEmpty()) {
            com.mojang.logging.LogUtils.getLogger().info(
                    "Sable Light Overhaul: page system {} resolved no keys (side {}, client config known {} paths, server presets {})",
                    system.label(), system.side(), clientValues().size(), ProfileCache.presetPaths().size());
            com.mojang.logging.LogUtils.getLogger().info(
                    "Sable Light Overhaul: page sample path is {} (prefix {} )", sample, system.prefixes());
        }
keys.sort(String::compareTo);
        return keys;
    }

    /** The value in force when a cannon has none of its own: the server's preset, or this client's own. */
    public static double preset(ProfileCatalogue.System system, String path) {
        if (system.side() == ProfileCatalogue.Side.SERVER) {
            return ProfileCache.preset(path).orElse(0.0);
        }
        Double own = ProfileCatalogue.asNumber(clientValues().get(path));
        return own == null ? 0.0 : own;
    }

    /** NightConfig keeps sections nested; a page wants dotted paths. */
    private static void flatten(UnmodifiableConfig config, String prefix, Map<String, Object> out) {
        for (var entry : config.valueMap().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof UnmodifiableConfig nested) flatten(nested, prefix + entry.getKey() + ".", out);
            else out.put(prefix + entry.getKey(), value);
        }
    }
}
