package dev.sablelightoverhaul.lighting;

import dev.sablelightoverhaul.profiles.ProfileAudit;
import dev.sablelightoverhaul.profiles.ProfileData;
import dev.sablelightoverhaul.profiles.ProfileOrphans;
import dev.sablelightoverhaul.profiles.ProfileStore;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The two things that maintain the per-cannon values: the periodic check, and the command that cleans up.
 *
 * <h2>What the periodic check looks at</h2>
 *
 * <p>An entry is read when the chunks holding its block are loaded, wherever that is and whoever is nearby.
 * This is a server behaviour: a player's position says nothing about whether a cannon exists, so no distance
 * is measured and nothing is skipped for being out of sight. The list holds one entry per configured cannon,
 * so this is a handful of block reads every few seconds.
 *
 * <p>Two answers do <em>not</em> remove anything. A chunk that is not loaded may well hold the cannon still.
 * A body the container cannot account for may be a world that is still loading. Settings cost a few numbers;
 * deleting them wrongly costs a player's work, so the check only removes on evidence.
 *
 * <h2>What the command is for</h2>
 *
 * <p>Entries whose ship is no longer in Sable's list are orphans, and cleaning those is left to a player
 * asking for it: the command walks the live bodies and reports how many entries it removed. Nothing else in
 * this mod deletes a mounted cannon's values.
 */
public final class ServerProfiles {
    /** Every few seconds, which is often enough for a cannon that has just been taken apart. */
    private static final int SWEEP_INTERVAL_TICKS = 100;

    private static int ticks;

    private ServerProfiles() {}

    /** Which types count as a cannon. The name list is the far entity one, so both agree on what a cannon is. */
    private static boolean isCannon(ResourceLocation typeId) {
        if (typeId == null) return false;
        return FarEntities.matchesName(typeId.getPath(), LightingCommon.FAR_ENTITY_NAMES.get());
    }

    public static void install() {
        NeoForge.EVENT_BUS.addListener(ServerProfiles::onTick);
        NeoForge.EVENT_BUS.addListener(ServerProfiles::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(ServerProfiles::onMenuClosed);
NeoForge.EVENT_BUS.addListener(ServerProfiles::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(ServerProfiles::onJoined);
    }

    /**
     * When a player closes the cannon form, everyone is told what they changed.
     *
     * <p>On close rather than on every keystroke: the values are stored as they are typed, so nothing depends on
     * this running, and one message per cannon per page visit is the difference between a feature that talks
     * constantly and one that speaks when it has something to say.
     */
    private static void onMenuClosed(net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Close event) {
        try {
            var player = event.getEntity();
            if (player == null || player.level().isClientSide()) return;
            var menu = event.getContainer();
            if (menu == null || menu.getType() != ProfileLdlib.MENU.get()) return;
            if (!(player.level() instanceof ServerLevel level)) return;
            for (var touched : ProfileSessions.drain(player.getUUID()).values()) {
                ProfileSync.announce(level, touched.key(), touched.kind());
            }
        } catch (RuntimeException failure) {
            // Closing a window is never worth an exception.
        }
    }

    /**
     * Hands a player the whole table the moment they arrive.
     *
     * <p>The client also asks for it, and that request was what this was designed around -- but it is a hop that
     * can fail silently, and when it did, the page had nothing to show and a shot could not be traced back to its
     * cannon, with nothing in the log to say so. A player joining is a fact the server knows by itself, so the
     * table is sent from here, and the request stays only as a way to ask again later.
     */
    private static void onJoined(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer player) ProfileSync.send(player);
        } catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "Sable Light Overhaul: could not send the table on join ({})", failure.toString());
        }
    }
    /** A player who leaves takes their pending announcements with them. */
    private static void onLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        try {
            ProfileSessions.forget(event.getEntity().getUUID());
        } catch (RuntimeException ignored) {
            // Nothing to do.
        }
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (++ticks < SWEEP_INTERVAL_TICKS) return;
        ticks = 0;
        try {
            var knownCannons = new java.util.LinkedHashSet<String>();
            for (ServerLevel level : event.getServer().getAllLevels()) {
                ProfileData data = ProfileData.get(level);
                ProfileStore store = data.store();
                // The mount table follows the save rather than a clock: a cannon that is merely idle keeps its
                // mapping, and one that has been dismantled loses it. Every level adds what it knows here, and
                // the sweep runs once afterwards.
                for (var cannonId : store.snapshot().keySet()) knownCannons.add(cannonId.stableId());
                if (store.size() == 0) continue;
                var removed = ProfileAudit.sweep(store, new ServerProfileView(level), ServerProfiles::isCannon);
                if (!removed.isEmpty()) {
                    // A removal that is not marked dirty lives only in memory, which is the same mistake that
                    // made the first round of testing look like it worked.
                    data.setDirty();
                    // A sweep drops a cannon's whole entry, not one value, so a single change message cannot
                    // describe it. The table is small and this happens when a cannon is dismantled, which is
                    // rare, so everyone is simply given the new one.
                    for (var player : level.players()) ProfileSync.send(player);
                    if (LightingCommon.SPEC.isLoaded() && LightingCommon.on(LightingCommon.EFFECTS_DEBUG)) {
                        com.mojang.logging.LogUtils.getLogger().info(
                                "Sable Light Overhaul: dropped {} cannon profile(s) in {} whose block is no longer a cannon",
                                removed.size(), level.dimension().location());
                    }
                }
            }
            if (dev.sablelightoverhaul.lighting.MountIndex.sweep(event.getServer(), knownCannons) > 0) {
                // A dismantled cannon's mapping is gone, and a stale one would point its successor's shots at
                // settings that were never its own, so the clients are told at once. Rare enough to be simple.
                for (ServerLevel level : event.getServer().getAllLevels()) {
                    for (var player : level.players()) ProfileSync.send(player);
                }
            }
        } catch (RuntimeException failure) {
            // A maintenance pass is never worth taking a server tick down with it.
            com.mojang.logging.LogUtils.getLogger().warn(
                    "Sable Light Overhaul: the profile check failed and will try again ({})", failure.toString());
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sablelightoverhaul")
                .then(Commands.literal("profiles")
                        .then(Commands.literal("purge")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    int total = 0;
                                    for (ServerLevel level : context.getSource().getServer().getAllLevels()) {
                                        total += purge(level);
                                    }
                                    final int removed = total;
                                    context.getSource().sendSystemMessage(Component.literal(
                                            "Sable Light Overhaul: removed " + removed
                                                    + " profile(s) whose ship is no longer in Sable's list"));
                                    return removed;
                                }))));
    }

    /** Walks the level's live bodies and forgets every entry filed under one that is not there. */
    private static int purge(ServerLevel level) {
        var store = ProfileData.get(level).store();
        if (store.size() == 0) return 0;
        var live = new HashSet<UUID>();
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container != null) {
            for (var body : container.getAllSubLevels()) {
                if (body != null && !body.isRemoved()) live.add(body.getUniqueId());
            }
        } else if (store.size() > 0) {
            // No Sable in this level at all, so every mounted entry is an orphan by definition. Refusing to act
            // is the safer reading: a container that is absent is not evidence that ships were taken apart.
            return 0;
        }
        int dropped = ProfileOrphans.purge(store, live).size();
        if (dropped > 0) dev.sablelightoverhaul.profiles.ProfileData.get(level).setDirty();
        return dropped;
    }
}
