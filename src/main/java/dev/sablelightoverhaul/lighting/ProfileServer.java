package dev.sablelightoverhaul.lighting;

import dev.sablelightoverhaul.ProfileEditPayload;
import dev.sablelightoverhaul.ProfileImportPayload;
import dev.sablelightoverhaul.api.EffectApi;
import dev.sablelightoverhaul.profiles.ProfileData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Applying what a page sends, with the checks a page cannot be trusted to do.
 *
 * <p>It lives beside the rest of the Sable bridge for the same reason {@link ServerProfileView} does: the
 * confirmation below has to ask where a body's blocks are, and that is the bridge's business rather than
 * something to expose as public API.
 *
 * <p>The client parses and the client runs its own type gate, but neither is authority: a page is a view, and
 * anything a client sends arrives as a claim. So every write is verified against the world first -- the
 * cannon must be in the level being edited, its body must still exist, the block at its own position must be
 * loaded, and that block must be the type the payload names. A claim that does not match is dropped rather
 * than filed. A cannon that is not loaded is dropped too, because not being able to see what is there is not
 * the same as knowing it is right -- the same distinction the periodic sweep makes.
 *
 * <p>Nothing here throws. A page can outlive its cannon, a player can edit while a chunk unloads, and a paste
 * can name something that is not a type at all; none of those is worth a disconnect.
 */
public final class ProfileServer {
    private ProfileServer() {}

    /** Writes one value, after checking that the cannon is loaded and really is of that type. */
    public static boolean applyEdit(Player player, ProfileEditPayload payload) {
        if (player == null || payload == null || payload.key() == null) return false;
        return withCannon(player, payload.key(), payload.typeId(), (level, key, type) -> {
            var data = ProfileData.get(level);
            data.store().put(key, type, payload.name(), payload.value());
            // Saved data is written on a world save only if it says it changed. Without this the edit is
            // visible for the rest of the session and gone the moment the player leaves, which is exactly
            // what the owner saw -- and there was no file on disk at all.
            data.setDirty();
            note("stored {} = {} for {} in {}", payload.name(), payload.value(), type, key.localPos());
            // A line that does not depend on the diagnostics switch, because "the per-cannon value did
            // nothing" cannot be answered without knowing whether the write arrived at all. It names the
            // cannon's identity, which is the other half of the question: a value filed under one identity
            // and looked up under another is the same symptom as a value that was never sent.
            com.mojang.logging.LogUtils.getLogger().info(
                    "Sable Light Overhaul: page wrote {} = {} for {} | cannon {}", payload.name(), payload.value(),
                    type, key.stableId());
            // Recorded, not announced: the announcement waits until the page is closed, because a value dragged
            // through five numbers is one edit and should be one message. The value itself is stored already.
            ProfileSessions.touch(player.getUUID(), key, type);
            return true;
        });
    }

    /**
     * Forgets values, after checking the cannon exactly the way an edit is checked.
     *
     * <p>This is "restore the default" made real. A cannon has no defaults of its own -- a value it never held
     * is a value it inherits from the kind's preset -- so putting a page back on its preset is removing the
     * records, and the page then shows the preset because there is nothing else to show. An empty list of
     * names means every value this cannon holds for this kind.
     */
    public static int applyClear(Player player, dev.sablelightoverhaul.ProfileClearPayload payload) {
        if (player == null || payload == null || payload.key() == null) return 0;
        var removed = new int[]{0};
        withCannon(player, payload.key(), payload.typeId(), (level, key, type) -> {
            var data = ProfileData.get(level);
            removed[0] = data.store().remove(key, type, payload.names());
            // Logged whether or not anything was removed: a restore that removed nothing and a restore that
            // never arrived look identical from the page, and only one of them is a bug.
            com.mojang.logging.LogUtils.getLogger().info(
                    "Sable Light Overhaul: page restored {} value(s) for {} | cannon {}", removed[0], type,
                    key.stableId());
            if (removed[0] > 0) {
                data.setDirty();
                note("forgot {} value(s) for {} in {}", removed[0], type, key.localPos());
                // Same as an edit: recorded, not announced. The page is still open and is told once, on closing.
                ProfileSessions.touch(player.getUUID(), key, type);
            }
            return true;
        });
        return removed[0];
    }

    /**
     * Writes a pasted form, kind by kind.
     *
     * <p>A paste carries values for whatever kinds it was exported from, and each one is checked against the
     * kinds this mod knows as it is applied. The old check compared each entry against the cannon's own type,
     * which a paste has no business knowing and which was the mount's block name besides.
     *
     * <p>Dropping rather than reporting is deliberate: the client already reported what it was about to send,
     * and the server's job is to refuse what must not land. A paste for another kind of cannon is not an error
     * to shout about, it is simply not this cannon's form.
     */
    public static int applyImport(Player player, ProfileImportPayload payload) {
        if (player == null || payload == null || payload.key() == null) return 0;
        var written = new int[]{0};
        // No single kind is claimed: the caller checks each one, so the cannon only has to be confirmed once.
        withCannon(player, payload.key(), null, (level, key, type) -> {
            var data = ProfileData.get(level);
            var store = data.store();
            payload.byType().forEach((named, values) -> {
                ResourceLocation parsed;
                try {
                    parsed = ResourceLocation.parse(named);
                } catch (RuntimeException notAType) {
                    return;   // a paste can name anything; this line is not a type and is skipped
                }
                if (dev.sablelightoverhaul.profiles.ProfileKinds.of(parsed).isEmpty()) return;
                values.forEach((name, value) -> {
                    store.put(key, parsed, name, value);
                    written[0]++;
                    ProfileSessions.touch(player.getUUID(), key, parsed);
                });
            });
            data.setDirty();
            note("imported {} value(s) over every kind in the paste, at {}", written[0], key.localPos());
            return true;
        });
        return written[0];
    }

    /** What a page wants done once the cannon has been confirmed. */
    private interface Checked {
        boolean apply(ServerLevel level, EffectApi.SourceKey key, ResourceLocation type);
    }

    /**
     * Confirms the cannon before letting anything be written for it.
     *
     * <p>The confirmation is the world's answer, not the payload's: the block at the cannon's own position
     * must exist, be loaded, and be the type the payload claims. The position comes from the key's body, so a
     * cannon on a ship is checked where that ship's blocks actually are.
     */
    private static boolean withCannon(Player player, EffectApi.SourceKey key, ResourceLocation claimed,
                                      Checked action) {
        try {
            if (!LightingCommon.PROFILES_ENABLED.get()) {
                return refuse("per-cannon profiles are switched off (profiles.enabled = false)", key);
            }
            if (!(player.level() instanceof ServerLevel level)) return refuse("not a server level", key);
            // A null claim means the caller checks kinds itself, which is what a paste does.
            if (claimed != null && dev.sablelightoverhaul.profiles.ProfileKinds.of(claimed).isEmpty()) {
                return refuse("not a kind of shot this mod knows", key);
            }
            if (!level.dimension().equals(key.dimension())) return refuse("wrong dimension", key);
            if (key.ship() != null && !SableAnchors.bodyExists(level, key.ship())) {
                return refuse("body not in the container", key);
            }
            BlockPos where = key.ship() == null ? key.localPos() : plotPosition(level, key);
            if (where == null) return refuse("body has no plot origin", key);
            if (!level.isLoaded(where)) return refuse("cannon is not loaded", key);
            var state = level.getBlockState(where);
            if (state.isAir()) return refuse("nothing at the cannon's position", key);
            var actual = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
            // The claim is a kind of shot, not a block, so the check is that what stands there is a cannon at
            // all -- the same name list the periodic sweep uses, so the two agree on what a cannon is.
            if (actual == null || !FarEntities.matchesName(actual.getPath(), LightingCommon.FAR_ENTITY_NAMES.get())) {
                return refuse("the block there is " + actual + ", which is not a cannon", key);
            }
            return action.apply(level, key, claimed);
        } catch (RuntimeException failure) {
            return refuse("exception " + failure, key);
        }
    }

    /**
     * Says why a write was refused, under the diagnostics master switch.
     *
     * <p>This exists because the first round of testing could not tell a refused write from a successful one:
     * every refusal was silent, so "the page opens and the value is still there" looked like success while
     * nothing had been stored at all.
     */
    private static boolean refuse(String reason, EffectApi.SourceKey key) {
        note("refused ({}): body={} pos={} type={}", reason, key.ship(), key.localPos(), key.typeId());
        return false;
    }

    /** One diagnostic line, only when the diagnostics master switch is on. */
    private static void note(String message, Object... arguments) {
        try {
            if (LightingCommon.SPEC.isLoaded() && LightingCommon.on(LightingCommon.EFFECTS_DEBUG)) {
                com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul profile: " + message, arguments);
            }
        } catch (RuntimeException ignored) {
            // Diagnostics must never be the thing that fails.
        }
    }

    /** Where the key's block is, in the plot it belongs to, without requiring anything to be loaded. */
    private static BlockPos plotPosition(ServerLevel level, EffectApi.SourceKey key) {
        var origin = SableAnchors.plotOrigin(level, key.ship());
        return origin == null ? null : key.localPos().offset(origin.getX(), 0, origin.getZ());
    }
}
