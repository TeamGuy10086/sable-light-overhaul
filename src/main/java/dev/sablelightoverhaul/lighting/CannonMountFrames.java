package dev.sablelightoverhaul.lighting;

import dev.sablelightoverhaul.profiles.ProfileTargeting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The two frames the cannon search can run in, and which one a given aim point belongs to.
 *
 * <p>The owner's rule, expressed directly: if the line of sight lands in the world, search the world; if it
 * lands on a body, search that body. It is decided by asking the same primitive the rest of the mod asks --
 * whether this position belongs to a sub-level -- so the answer cannot disagree with what the shot's own
 * identity says later.
 *
 * <p>That is also what makes the search never cross bodies, without a rule for it. A body's cannons live at
 * that body's plot coordinates and a world position is not one of them, so a world frame simply never finds
 * one, and a body frame never finds anything else. There is no distance between the two spaces to be nearest
 * in, which is why the rule is structural rather than a check.
 */
public final class CannonMountFrames {
    private CannonMountFrames() {}

    /**
     * Where the aim landed, and the frame that goes with it.
     *
     * <p>Returns null when the aim is not a position at all, which the caller should treat as "nothing to
     * configure" rather than as an error.
     */
    public static ProfileTargeting.Frame frameAt(ServerLevel level, BlockPos aim) {
        if (level == null || aim == null) return null;
        var anchor = SourceAnchor.capture(level, aim);
        return anchor.ship() == null ? world(level) : body(level, anchor.ship());
    }

    /** The world: only real cannon blocks, and only positions that are not inside a body's plot. */
    public static ProfileTargeting.Frame world(ServerLevel level) {
        return new Frame(level, null);
    }

    /** One body: only that body's plot coordinates, so a cannon here cannot be found from a world aim. */
    public static ProfileTargeting.Frame body(ServerLevel level, java.util.UUID ship) {
        return new Frame(level, ship);
    }

    private record Frame(ServerLevel level, java.util.UUID ship) implements ProfileTargeting.Frame {
        @Override
        public boolean isCannonMount(BlockPos pos) {
            if (!level.isLoaded(pos)) return false;
            var state = level.getBlockState(pos);
            if (state.isAir()) return false;
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (id == null) return false;
            // The same name list the far-entity pass uses, so both agree on what a cannon is. Today that is
            // Cannon; a missile or anything else later is one entry in that list and nothing else.
            return FarEntities.matchesName(id.getPath(), LightingCommon.FAR_ENTITY_NAMES.get());
        }

        /**
         * Whether the search may step here.
         *
         * <p>The frame's own space is the whole filter. A world search refuses everything inside a plot, a
         * body search refuses everything outside its own, and both refuse unloaded chunks -- which ends that
         * branch of the fill rather than the search.
         */
        @Override
        public boolean canEnter(BlockPos pos) {
            if (!level.isLoaded(pos)) return false;
            var anchor = SourceAnchor.capture(level, pos);
            return ship == null ? anchor.ship() == null : ship.equals(anchor.ship());
        }
    }
}
