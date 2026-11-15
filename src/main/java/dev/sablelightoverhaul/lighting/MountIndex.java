package dev.sablelightoverhaul.lighting;

import dev.sablelightoverhaul.api.EffectApi;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Which cannon a mount is carrying, so a shot only has to name its mount.
 *
 * <p><b>This is a routing table, not a store.</b> The values themselves live in the per-cannon save, which is
 * written to disk and is only ever dropped when the block stops being a cannon. Losing an entry here loses a
 * lookup: the next shot from that mount writes it again, because the server knows both halves at the moment it
 * fires. Nothing a player has configured is ever held in this class, so nothing they configured can be lost by
 * anything this class does.
 *
 * <p><b>Why the table exists.</b> A shot's flush reaches the client with the entity that fired it, and a client
 * cannot turn that into the cannon a form page knows: the identity is the mount's own position inside its body,
 * which is a server-side fact. Carrying the whole identity in every shot would work and cost about twenty-four
 * bytes a shot; naming the mount costs eight, and the mount is the thing that assembled the cannon and owns it,
 * so it is the honest name for it.
 *
 * <p><b>Any mount has a definite coordinate.</b> A mount standing in the world has a world position; a mount
 * inside a body has its position in that body's plot, a real and unique place in a real level. Both are a
 * dimension and a block position, so one table covers both and neither needs a special case.
 *
 * <p><b>An entry lives exactly as long as the cannon's saved values do.</b> The maintenance pass hands this class
 * the identities the save still knows about, and anything else is dropped: a mapping for a cannon that has been
 * dismantled would point a new cannon's shots at settings that are no longer its own, which is worse than having
 * no mapping at all. The clock is only a backstop against a leak, not the way entries normally end.
 */
public final class MountIndex {
    private MountIndex() {}

    /** How long an entry may go unheard from before it is dropped regardless. A backstop, not the rule. */
    private static final long BACKSTOP_MILLIS = 30L * 60L * 1000L;

    /** Mount (dimension and position) to the identity a form page files its values under. */
    private static final Map<String, String> BY_MOUNT = new LinkedHashMap<>();
    /** The same table the other way, so a mount can be found from the cannon it carries. */
    private static final Map<String, String> BY_CANNON = new LinkedHashMap<>();
    /** When each mount was last heard from, for the backstop only. */
    private static final Map<String, Long> SEEN = new LinkedHashMap<>();

    /** A mount's name: its dimension and its position, which together are unique. */
    public static String key(ResourceLocation dimension, BlockPos pos) {
        return dimension + "|" + pos.asLong();
    }

    /**
     * Records that this mount is carrying this cannon. Called whenever a cannon fires.
     *
     * @return true when this is news -- a mount this table had not heard of, or one that has changed cannon --
     *         so that the callers can hand the clients the table at once rather than at the next opportunity
     */
    public static synchronized boolean register(ResourceLocation dimension, BlockPos mount,
                                                EffectApi.SourceKey cannon) {
        if (dimension == null || mount == null || cannon == null) return false;
        String mountId = key(dimension, mount);
        String cannonId = cannon.stableId();
        String previous = BY_MOUNT.put(mountId, cannonId);
        if (previous != null && previous.equals(cannonId)) {
            SEEN.put(mountId, System.currentTimeMillis());
            return false;
        }
        if (previous != null) BY_CANNON.remove(previous);
        BY_CANNON.put(cannonId, mountId);
        SEEN.put(mountId, System.currentTimeMillis());
        if (BY_MOUNT.size() > 4096) clear();
        return true;
    }

    /** The whole table, as the clients receive it: mount name to cannon identity. */
    public static synchronized Map<String, String> snapshot() {
        return Map.copyOf(BY_MOUNT);
    }

    /** Forgets a mount: a cannon that no longer fires from there must not keep pointing at it. */
    public static synchronized void forget(String mountId) {
        String cannonId = BY_MOUNT.remove(mountId);
        if (cannonId != null) BY_CANNON.remove(cannonId);
        SEEN.remove(mountId);
    }

    public static synchronized void clear() {
        BY_MOUNT.clear();
        BY_CANNON.clear();
        SEEN.clear();
    }

    /**
     * Drops every entry whose cannon the save no longer knows about, plus anything past the backstop.
     *
     * <p>The identities come from the save rather than from the world on purpose: a mount's stored position is
     * its position <em>inside its body</em>, which is not a block position that can simply be looked up in a
     * level, and asking the wrong question would drop entries that are perfectly good. The save is the authority
     * on which cannons exist, and it is written to disk, so this follows it exactly.
     *
     * @param knownCannons every cannon identity the profile save still holds, across all levels
     * @return how many entries were dropped
     */
    public static synchronized int sweep(MinecraftServer server, Set<String> knownCannons) {
        long now = System.currentTimeMillis();
        var doomed = new java.util.ArrayList<String>();
        for (var entry : BY_MOUNT.entrySet()) {
            boolean stillSaved = knownCannons != null && knownCannons.contains(entry.getValue());
            boolean pastBackstop = now - SEEN.getOrDefault(entry.getKey(), now) > BACKSTOP_MILLIS;
            if (!stillSaved || pastBackstop) doomed.add(entry.getKey());
        }
        for (String mountId : doomed) forget(mountId);
        return doomed.size();
    }
}
