package dev.sablelightoverhaul.profiles;

import dev.sablelightoverhaul.api.EffectApi;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Which values apply to which cannon.
 *
 * <p>Pure logic on purpose: no world, no file, no SavedData. Everything that decides whether a value is used
 * -- the identity it is filed under, the type gate, and the fact that changing the type does not discard
 * anything -- is testable without a game, and the storage layer only has to move these maps in and out of
 * NBT.
 *
 * <p><b>Identity is A and B and not C.</b> A cannon is a position (in its body's own frame) plus the body it
 * is in, which is {@link EffectApi.SourceKey}'s first three fields. The cannon's type is deliberately not
 * part of the identity: it is the gate below.
 *
 * <p><b>Switching the type never discards values.</b> A cannon keeps one set of values per type, and asking
 * for values only ever reads the set belonging to the type asked about. A player who opens the page, changes
 * the type to look at it, and changes it back finds everything where it was, because nothing was ever
 * written over.
 */
public final class ProfileStore {
    /**
     * One cannon's identity: where it is and which body it is in. Null body means there is no body and the
     * position is world space, which is an ordinary cannon and not an error.
     */
    public record CannonId(ResourceKey<Level> dimension, UUID ship, BlockPos localPos) {
        public static CannonId of(EffectApi.SourceKey key) {
            return new CannonId(key.dimension(), key.ship(), key.localPos());
        }

        /**
         * The same name {@link EffectApi.SourceKey#stableId()} gives this cannon.
         *
         * <p>Delegated rather than written out again: a save and a client have to agree on this string exactly,
         * and two copies of the rule are two chances for them to stop agreeing.
         */
        public String stableId() {
            return new EffectApi.SourceKey(dimension, ship, localPos, null, null).stableId();
        }
    }

    /** The values filed under one type for one cannon, in the order they were written. */
    private final Map<CannonId, Map<ResourceLocation, Map<String, Double>>> byCannon = new LinkedHashMap<>();

    /**
     * The values that apply to this cannon, if any: those filed under exactly this type.
     *
     * <p>The type gate is the whole reason the type is stored. A form written for one kind of cannon is never
     * handed to another kind, even when the identity matches -- an empty answer is the correct answer, and the
     * caller falls back to its own defaults. Nothing here throws or warns: a cannon nobody has configured is
     * the normal case.
     */
    public Optional<Map<String, Double>> resolve(EffectApi.SourceKey key, ResourceLocation cannonType) {
        var byType = byCannon.get(CannonId.of(key));
        if (byType == null || cannonType == null) return Optional.empty();
        var values = byType.get(cannonType);
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(Map.copyOf(values));
    }

    /** Writes one value, keeping every other type's values for this cannon exactly as they were. */
    public void put(EffectApi.SourceKey key, ResourceLocation typeId, String name, double value) {
        if (typeId == null || name == null) return;
        byCannon.computeIfAbsent(CannonId.of(key), id -> new LinkedHashMap<>())
                .computeIfAbsent(typeId, type -> new LinkedHashMap<>())
                .put(name, value);
    }

    /** Every value a cannon has, for every type, as one snapshot: what an export has to carry. */
    public Map<ResourceLocation, Map<String, Double>> allFor(EffectApi.SourceKey key) {
        var byType = byCannon.get(CannonId.of(key));
        if (byType == null) return Map.of();
        var copy = new LinkedHashMap<ResourceLocation, Map<String, Double>>();
        byType.forEach((type, values) -> copy.put(type, Map.copyOf(values)));
        return Map.copyOf(copy);
    }

    /**
     * Forgets one cannon entirely. This is what the periodic check calls when the block at a registered
     * position is no longer a cannon -- the cannon was removed, or a wall stands where it was.
     */
    /**
     * Forgets values a cannon holds for one type: the named ones, or every one of them when the list is empty.
     *
     * <p>This is what "restore the default" means here. A cannon has no defaults of its own -- a value it never
     * held is a value it inherits from the kind's preset -- so restoring is removing the record, and the page
     * then shows the preset because there is nothing else to show.
     *
     * @return how many values were actually removed
     */
    public int remove(EffectApi.SourceKey key, ResourceLocation typeId, java.util.Collection<String> names) {
        var id = CannonId.of(key);
        var byType = byCannon.get(id);
        if (byType == null || typeId == null) return 0;
        var values = byType.get(typeId);
        if (values == null) return 0;
        int removed;
        if (names == null || names.isEmpty()) {
            removed = values.size();
            byType.remove(typeId);
        } else {
            removed = 0;
            for (var name : names) if (values.remove(name) != null) removed++;
        }
        // A cannon with nothing left is not a cannon with an empty list: empty maps left behind would keep it
        // in the table sent to clients, and a page would offer it values it no longer holds.
        if (byType.isEmpty()) byCannon.remove(id);
        return removed;
    }
    public boolean forget(EffectApi.SourceKey key) {
        return key != null && forget(CannonId.of(key));
    }

    /** The same, for a caller that already holds the identity -- the periodic check works from those. */
    public boolean forget(CannonId id) {
        return id != null && byCannon.remove(id) != null;
    }

    /** How many cannons have anything filed. For diagnostics only. */
    public int size() {
        return byCannon.size();
    }

    /**
     * Everything, for the storage layer to write out.
     *
     * <p>A deep copy, so the caller cannot change the store by holding on to it, and ordered, so a save is
     * stable rather than reshuffled on every write.
     */
    public Map<CannonId, Map<ResourceLocation, Map<String, Double>>> snapshot() {
        var copy = new LinkedHashMap<CannonId, Map<ResourceLocation, Map<String, Double>>>();
        byCannon.forEach((id, byType) -> {
            var types = new LinkedHashMap<ResourceLocation, Map<String, Double>>();
            byType.forEach((type, values) -> types.put(type, Map.copyOf(values)));
            copy.put(id, Map.copyOf(types));
        });
        return Map.copyOf(copy);
    }

    /** Replaces everything with what was read back. Used only by the storage layer, on load. */
    public void restore(Map<CannonId, Map<ResourceLocation, Map<String, Double>>> saved) {
        byCannon.clear();
        saved.forEach((id, byType) -> byCannon.put(id, new LinkedHashMap<>(byType)));
    }
}
