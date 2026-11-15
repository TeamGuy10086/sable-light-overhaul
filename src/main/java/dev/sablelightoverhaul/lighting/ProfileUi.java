package dev.sablelightoverhaul.lighting;

import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.profiles.ProfileData;
import dev.sablelightoverhaul.profiles.ProfileTargeting;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Opening a cannon's form, and saying what the cannon is set to when the toolkit is missing.
 *
 * <p>Every trigger comes through here, because the interesting part is not the click: it is turning a click
 * into a cannon. That is a frame question -- is the aim in the world or on a body -- followed by a search
 * within that frame, and both of those already exist and are tested. This class only decides which of them a
 * click is asking for.
 *
 * <p>Two clicks, two questions, as the owner specified. A plain right-click asks about the block that was
 * clicked and nothing else, so pointing at a wall is not a request to configure the cannon behind it. A shift
 * right-click asks for the nearest mount within ten steps. Entities are deliberately not handled: there is no
 * block to search from, so there is nothing to ask.
 *
 * <p>When the toolkit is absent the page cannot be drawn, but the settings can still be read out in chat.
 * That fallback is the house rule for every page in this mod: a missing toolkit should cost a player the
 * widgets, not the information.
 */
public final class ProfileUi {
    /** The item that opens the form. Matched by name so the trigger works the moment the item is registered. */
    private static final String WRENCH = "profile_wrench";

    private static boolean installed;

    private ProfileUi() {}

    /** Registers the click handler. Idempotent, and harmless when the item is not in the pack. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        NeoForge.EVENT_BUS.addListener(ProfileUi::onRightClickBlock);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (player == null || player.level().isClientSide()) return;
        var held = player.getItemInHand(event.getHand() == null ? InteractionHand.MAIN_HAND : event.getHand());
        var id = BuiltInRegistries.ITEM.getKey(held.getItem());
        if (id == null || !WRENCH.equals(id.getPath())) return;
        // Shift asks for the nearest mount; a plain click asks only about the block itself.
        if (!open(player, event.getPos(), player.isShiftKeyDown())) return;
        // The click was ours, so the block's own use does not also happen.
        event.setCanceled(true);
    }

    /**
     * Opens the form for the cannon a click means.
     *
     * @param search true for a shift click, which looks up to ten steps for the nearest mount
     * @returns true when a page was actually opened
     */
    public static boolean open(Player player, BlockPos clicked, boolean search) {
        if (!(player instanceof ServerPlayer server) || clicked == null) return false;
        try {
            var level = server.serverLevel();
            var frame = CannonMountFrames.frameAt(level, clicked);
            var mount = ProfileTargeting.locate(frame, clicked, search);
            if (mount.isEmpty()) {
                // Nothing to configure is an answer, not a failure: say so and leave the world alone.
                server.displayClientMessage(Component.translatable(search
                        ? "sablelightoverhaul.profile.noCannonNear"
                        : "sablelightoverhaul.profile.noCannonHere"), false);
                return false;
            }
            var where = mount.get();
            var state = level.getBlockState(where);
            var typeId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (typeId == null) return false;
            // The mount's own block, not a capture of where it sits inside a moving body. The capture is the
            // better identity in principle -- it survives a ship moving -- but a shot looks its cannon up by
            // the mount block (see mountKeyFor), so a page that stored values under the captured identity was
            // storing them where the shot would never look: the values saved, appeared in the table, and
            // changed nothing. One identity, the one that always exists.
            // The captured anchor again: a sub-level's world position is a projection, so only the body plus the local
// position is fixed, and both the page and a shot have to agree on that.
                        var key = new EffectApi.SourceKey(level.dimension(), null, where, typeId, null);
            // One line per open, next to the shot's own "mount key probe". A per-cannon value is only found by
            // a shot when these two strings are equal, so both are printed with the block they were built from:
            // the click lands on the cannon the player sees, and the shot uses the contraption's controller,
            // and those are two different blocks unless something makes them the same.
            com.mojang.logging.LogUtils.getLogger().info(
                    "Sable Light Overhaul: form opened for key {} | mount {} | block {}",
                    key.stableId(), where, typeId);
            return openFor(server, key, typeId);
        } catch (RuntimeException failure) {
            // Whatever went wrong, it is not worth a crash on a click.
            com.mojang.logging.LogUtils.getLogger().warn(
                    "Sable Light Overhaul: could not open a cannon form ({})", failure.toString());
            return false;
        }
    }

    /**
     * The one place that decides between the page and the chat line.
     *
     * <p>The reference to the page class sits inside the loaded check, so a pack without the toolkit never
     * loads it -- the same arrangement every other page here uses.
     */
    public static boolean openFor(ServerPlayer player, EffectApi.SourceKey key, ResourceLocation type) {
        if (!LightingCommon.PROFILES_ENABLED.get()) {
            // Opening a page that could not store anything would be worse than saying why it is closed.
            player.displayClientMessage(Component.translatable("sablelightoverhaul.profile.disabled"), false);
            return false;
        }
        if (net.neoforged.fml.ModList.get().isLoaded("ldlib2")) {
            try {
                if (ProfileLdlib.open(player, key, type)) return true;
            } catch (Throwable problem) {
                com.mojang.logging.LogUtils.getLogger().error("Sable Light Overhaul cannon form failed to open", problem);
            }
        }
        player.displayClientMessage(summary(player, key), false);
        return false;
    }

    /** What the cannon is set to, in one line, which is what a player gets instead of a page. */
    private static Component summary(ServerPlayer player, EffectApi.SourceKey key) {
        try {
            Map<ResourceLocation, Map<String, Double>> all =
                    ProfileData.get(player.serverLevel()).store().allFor(key);
            var name = key.typeId() == null ? "?" : key.typeId().getPath();
            if (all.isEmpty()) {
                return Component.translatable("sablelightoverhaul.profile.summary.none", name);
            }
            var text = net.minecraft.network.chat.Component.empty();
            for (var entry : all.entrySet()) {
                var known = dev.sablelightoverhaul.profiles.ProfileKinds.of(entry.getKey()).orElse(null);
                var label = known == null
                        ? net.minecraft.network.chat.Component.literal(entry.getKey().getPath())
                        : net.minecraft.network.chat.Component.translatable(
                                "sablelightoverhaul.profile.kind." + known.name());
                if (!text.getString().isEmpty()) text.append(net.minecraft.network.chat.Component.literal("、"));
                text.append(label).append(net.minecraft.network.chat.Component.literal("(" + entry.getValue().size() + ")"));
            }
            return Component.translatable("sablelightoverhaul.profile.summary", all.size(), text);
        } catch (RuntimeException failure) {
            return Component.translatable("sablelightoverhaul.profile.summary.none", "?");
        }
    }
}
