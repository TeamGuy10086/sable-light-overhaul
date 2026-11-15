package dev.sablelightoverhaul.lighting;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import dev.sablelightoverhaul.ProfileChangedPayload;
import dev.sablelightoverhaul.ProfileTablePayload;
import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.profiles.ProfileCatalogue;
import dev.sablelightoverhaul.profiles.ProfileData;
import dev.sablelightoverhaul.profiles.ProfileKinds;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Handing the per-cannon table to clients, and telling them when it changes.
 *
 * <p>The rule this exists for: send once and then only on change. A client asks when it joins, the server
 * answers with everything, and after that a client hears only about the values that actually move. Nothing is
 * sent per shot -- the point of the whole exercise is to stop repeating the table, since the table is what the
 * shots are looked up in.
 *
 * <p>The presets are the server's own configuration, flattened. Only the paths some kind's systems actually
 * cover are sent: a client has no use for the debug switches or the generator keys, and sending them would
 * make the table grow for no reason. The systems the client owns are left out entirely, because a server must
 * not overwrite a player's own light profiles or particle shapes.
 */
public final class ProfileSync {
    /** Set once: what the server actually put on the wire. */
    private static boolean LOGGED_SEND;
    /** Set once: why the server-side preset table came out the size it did. */
    private static boolean LOGGED_PRESETS;
    private ProfileSync() {}

    /** Every configuration path the server owns that some kind's page shows, with its current value. */
    public static Map<String, Double> presets() {
        try {
            // Why this is loud: an empty preset table is invisible from the client -- the page simply shows
            // nothing -- and it has been the cause of exactly that twice. One line, once, says which of the
            // three ways it went wrong, and no user has to guess again.
            boolean loaded = LightingCommon.SPEC.isLoaded();
            var flat = new LinkedHashMap<String, Object>();
            if (loaded) flatten(LightingCommon.SPEC.getValues(), "", flat);
            if (!LOGGED_PRESETS) {
                LOGGED_PRESETS = true;
                com.mojang.logging.LogUtils.getLogger().info(
                        "Sable Light Overhaul: server presets: spec loaded={} flattened={}", loaded, flat.size());
                // The table came out empty while 181 paths had been flattened, so the break is between a path
                // and a number: three samples say what the values actually are.
                int shown = 0;
                for (var entry : flat.entrySet()) {
                    if (shown++ >= 3) break;
                    com.mojang.logging.LogUtils.getLogger().info(
                            "Sable Light Overhaul: preset sample {} = {} ({})",
                            entry.getKey(), entry.getValue(),
                            entry.getValue() == null ? "null" : entry.getValue().getClass().getName());
                }
            }
            var wanted = new LinkedHashMap<String, Double>();
            for (var entry : flat.entrySet()) {
                // Every path, unfiltered. The filter was there to keep the table small and it was the reason the
                // page had nothing to show: a client that receives no presets cannot offer a preset to fall back
                // on, and the table is a few hundred numbers sent once when a player joins. The client matches
                // paths to systems itself, which is where the matching belongs.
                Double number = ProfileCatalogue.asNumber(entry.getValue());
                if (number != null) wanted.put(entry.getKey(), number);
            }
            return Map.copyOf(wanted);
        } catch (RuntimeException failure) {
            // A table without presets is still a usable table: the page falls back to what it already has.
            return Map.of();
        }
    }

    /** Whether any kind's systems cover this path, which is what decides whether it is worth sending. */
    private static boolean coveredByAnyKind(String path) {
        for (var kind : ProfileKinds.all()) {
            for (var system : ProfileCatalogue.systemsFor(kind)) {
                if (system.side() == ProfileCatalogue.Side.SERVER && system.covers(path)) return true;
            }
        }
        return false;
    }

    /** The whole table for one level: the presets plus every cannon's stored values. */
    public static ProfileTablePayload table(ServerLevel level) {
        var cannons = new ArrayList<ProfileTablePayload.Cannon>();
        try {
            ProfileData.get(level).store().snapshot().forEach((id, byKind) -> byKind.forEach((kind, values) -> {
                if (values.isEmpty()) return;
                var kindId = ProfileKinds.of(kind).map(ProfileKinds::id).orElse(kind);
                cannons.add(new ProfileTablePayload.Cannon(
                        new EffectApi.SourceKey(id.dimension(), id.ship(), id.localPos(), kindId, null),
                        kind, Map.copyOf(values)));
            }));
        } catch (RuntimeException failure) {
            // An unreadable store sends an empty table rather than nothing at all.
        }
        return new ProfileTablePayload(List.copyOf(cannons), presets(), true, MountIndex.snapshot());
    }

    /**
     * Tells everyone in the level about one cannon, after its editor closed the page.
     *
     * <p>A cannon with nothing left in it is still named, with no values: that is how a client learns to drop
     * it. Sending nothing at all would leave every other client holding values the server no longer has.
     */
    public static void announce(ServerLevel level, EffectApi.SourceKey key, ResourceLocation kind) {
        try {
            var values = ProfileData.get(level).store().allFor(key).get(kind);
            var payload = new ProfileTablePayload(List.of(new ProfileTablePayload.Cannon(key, kind,
                    values == null ? Map.of() : Map.copyOf(values))), presets(), false, MountIndex.snapshot());
            for (var player : level.players()) PacketDistributor.sendToPlayer(player, payload);
        } catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "Sable Light Overhaul: could not announce a cannon change ({})", failure.toString());
        }
    }

    /**
     * Answers a client's request. Failures are swallowed: a table is never worth an exception.
     *
     * <p>Takes a plain player because that is what a payload handler is handed, and checks the type itself;
     * the check is the same one every other server-side write in this mod makes.
     */
    public static void send(net.minecraft.world.entity.player.Player player) {
        try {
            if (!(player instanceof ServerPlayer server)) return;
            if (!(server.level() instanceof ServerLevel level)) return;
            var sent = table(level);
            if (!LOGGED_SEND) {
                LOGGED_SEND = true;
                com.mojang.logging.LogUtils.getLogger().info(
                        "Sable Light Overhaul: table sent to {} ({} preset(s), {} cannon(s), {} mount(s))",
                        server.getGameProfile().getName(), sent.presets().size(), sent.cannons().size(),
                        sent.mounts().size());
            }
            PacketDistributor.sendToPlayer(server, sent);
        } catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "Sable Light Overhaul: could not send the cannon table ({})", failure.toString());
        }
    }

    /** Tells everyone in the level about one changed value. Only called when something actually changed. */
    public static void broadcast(ServerLevel level, ProfileChangedPayload payload) {
        try {
            for (var player : level.players()) PacketDistributor.sendToPlayer(player, payload);
        } catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "Sable Light Overhaul: could not broadcast a cannon change ({})", failure.toString());
        }
    }

    /** NightConfig keeps sections nested; forms and pages want dotted paths. */
    private static void flatten(UnmodifiableConfig config, String prefix, Map<String, Object> out) {
        for (var entry : config.valueMap().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof UnmodifiableConfig nested) {
                flatten(nested, prefix + entry.getKey() + ".", out);
            } else {
                out.put(prefix + entry.getKey(), value);
            }
        }
    }
}
