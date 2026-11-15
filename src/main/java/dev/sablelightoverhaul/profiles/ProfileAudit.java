package dev.sablelightoverhaul.profiles;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Decides which registered cannons are no longer there.
 *
 * <p>A cannon that has been removed leaves its values behind, and a wall built where it stood must not
 * inherit them. So every few seconds the registered entries are looked at, and an entry whose block is no
 * longer a cannon goes.
 *
 * <p><b>This is a server behaviour and it does not care where players are.</b> Where a player happens to be
 * standing says nothing about whether a cannon exists: one behind the player, one in a ship nobody is
 * watching, and one in a part of the world nobody has visited this session all still exist. There is no
 * distance to test and no visibility to compute -- an entry is checked when it is loaded, and left alone when
 * it is not. The list holds one entry per configured cannon, so checking all of it is a handful of block
 * reads.
 *
 * <p>Pure on purpose. It is handed a view of the world rather than the world, so every branch that matters --
 * the block was replaced, the body is gone, the chunk is not loaded -- is testable without a running game, and
 * the part that touches Sable is the view's implementation and nothing else.
 *
 * <p><b>Not loaded is not evidence.</b> An unloaded chunk is left exactly as it is: it may hold the cannon
 * still, and a ship that is out of loaded chunks must not cost its owner their settings.
 */
public final class ProfileAudit {
    /** What the sweep needs to know about the world, and nothing else. */
    public interface View {
        /**
         * Where this cannon's block is, or null when the body it was in is gone.
         *
         * <p>A cannon with no body is its own world position; one on a ship has to be asked where the plot has
         * put it. Null means gone and nothing else -- an unloaded position is returned like any other, and
         * {@link #isLoaded} is what says so.
         */
        BlockPos resolve(ProfileStore.CannonId id);

        /** Whether the chunks holding this position are loaded. */
        boolean isLoaded(BlockPos pos);

        /** The cannon type at this position now, or null when there is nothing of the sort there. */
        ResourceLocation typeAt(BlockPos pos);
    }

    private ProfileAudit() {}

    /**
     * Removes every entry that is provably gone, and returns what was removed so the caller can log it.
     *
     * @param whitelist which types count as a cannon; anything else at a registered position means the cannon
     *                  was replaced
     */
    public static List<ProfileStore.CannonId> sweep(ProfileStore store, View view,
                                                    Predicate<ResourceLocation> whitelist) {
        var removed = new ArrayList<ProfileStore.CannonId>();
        if (store == null || view == null || whitelist == null) return removed;
        for (ProfileStore.CannonId id : store.snapshot().keySet()) {
            BlockPos now;
            try {
                now = view.resolve(id);
            } catch (RuntimeException failure) {
                // A view that cannot answer is not a reason to delete somebody's settings.
                continue;
            }
            if (now == null) {
                // The body is not in the container. That is NOT proof it is gone, and the owner proved it: a
                // ship whose chunks unload also leaves the container, so acting on this deleted a profile that
                // was merely out of sight. Nothing automatic removes on it; the explicit command does, where a
                // player has asked and can see the count.
                continue;
            }
            try {
                if (!view.isLoaded(now)) continue;   // unloaded is not evidence
                ResourceLocation type = view.typeAt(now);
                if (type == null || !whitelist.test(type)) removed.add(id);
            } catch (RuntimeException failure) {
                // Not knowing this tick is not knowing it is gone.
                continue;
            }
        }
        removed.forEach(store::forget);
        return removed;
    }
}
