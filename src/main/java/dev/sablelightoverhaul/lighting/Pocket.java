package dev.sablelightoverhaul.lighting;

/**
 * Which block columns of a plot belong to the hull's pocket, asked one column at a time.
 *
 * <p>A plot is a pocket cut out of the same level as the world: inside it there is the hull and air, and
 * outside it there is whatever the level generated at those coordinates. A light field flooded in plot space
 * therefore stopped dead at the pocket's edge, which reads in game as "the deck is lit and the ground around
 * the ship is not, and you have to put the lamp right against something".
 *
 * <p>This is the escape hatch a flood uses: a column this answers false for is treated as air. The hull still
 * occludes, the falloff is still one level a cell, but the light is no longer walled in by ground that belongs
 * to a different place entirely.
 *
 * <p>It is an interface rather than two corner coordinates because the earlier version tried to compute a
 * box from the plot's first and last chunk and silently produced nothing usable -- the flood then ran with the
 * old rule and looked exactly as it had before, with no line anywhere saying so. Asking the plot itself has no
 * frame to get wrong.
 */
@FunctionalInterface
public interface Pocket {
    /** Whether this block column is inside the plot, i.e. part of this ship's own world. */
    boolean contains(int x, int z);
}
