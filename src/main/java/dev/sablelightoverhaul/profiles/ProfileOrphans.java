package dev.sablelightoverhaul.profiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Removing entries whose ship is no longer in Sable's list of bodies.
 *
 * <p>Deliberately not part of the periodic sweep. A container can be missing bodies for reasons that are not
 * "the ship was taken apart" -- a world still loading, a level being read in -- and a sweep that deleted on
 * that basis would throw away a player's settings for being briefly unaccounted for. An orphan, meanwhile,
 * costs a few numbers sitting in a file.
 *
 * <p>So this runs when a player asks it to, with the live list handed in. Nothing else in this mod removes a
 * bodied entry, which means a cannon's values can only disappear when its block was replaced, or when someone
 * deliberately cleaned up.
 */
public final class ProfileOrphans {
    private ProfileOrphans() {}

    /**
     * Removes every entry filed under a body that is not in {@code liveBodies}, and returns what went.
     *
     * <p>An entry with no body is not an orphan: it is a world-space cannon, and there is no Sable list to
     * compare it against. Whether one of those still exists is the sweep's question, not this one's.
     */
    public static List<ProfileStore.CannonId> purge(ProfileStore store, Set<UUID> liveBodies) {
        var removed = new ArrayList<ProfileStore.CannonId>();
        if (store == null || liveBodies == null) return removed;
        for (ProfileStore.CannonId id : store.snapshot().keySet()) {
            if (id.ship() == null) continue;
            if (!liveBodies.contains(id.ship())) removed.add(id);
        }
        removed.forEach(store::forget);
        return removed;
    }
}