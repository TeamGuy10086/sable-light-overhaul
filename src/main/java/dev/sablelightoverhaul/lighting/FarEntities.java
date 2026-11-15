package dev.sablelightoverhaul.lighting;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Which entities the server keeps delivering past the vanilla client view distance.
 *
 * <p>An assembled cannon is an entity (CBC registers it as {@code createbigcannons:pitch_contraption}),
 * so unlike the ship it rides on it obeys vanilla entity tracking: {@code ChunkMap.TrackedEntity
 * .updatePlayer} caps the delivery range at {@code min(typeRange, playerViewDistance * 16)}, which is
 * why the cannon disappears at 48 blocks with a view distance of 3 and at 64 with 4. No client-side
 * drawing can show an entity the client was never sent, so this is the server-side half of the far
 * pass (see {@code HANDOFF.zh-CN.md} 8.27 for the measurements).
 *
 * <p>This class deliberately lives outside the client package: the hook runs on the integrated server
 * and on a dedicated server, where client-only classes are absent.
 */
public final class FarEntities {
    private FarEntities() {}

    /**
     * The switch, safe to ask before any server config exists (a client that never hosted a world, or
     * a config that failed to load). A config that is not loaded throws on access, so it is checked.
     */
    public static boolean enabled() {
        return LightingCommon.SPEC.isLoaded() && LightingCommon.FAR_ENTITIES.get();
    }

    /** How far matching entities are delivered, in blocks; 0 when the feature is off. */
    public static int range() {
        if (!enabled()) return 0;
        return LightingCommon.FAR_ENTITY_RANGE.get();
    }

    public static boolean matches(Entity entity) {
        return entity != null && matches(entity.getType());
    }

    public static boolean matches(EntityType<?> type) {
        if (type == null || !enabled()) return false;
        return pathMatches(type);
    }

    private static boolean pathMatches(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id != null && matchesName(id.getPath(), LightingCommon.FAR_ENTITY_NAMES.get());
    }

    /**
     * The filter itself, on the registry path only, exactly as the block entity pass does it: the
     * namespace is ignored on purpose, so a mod whose id merely contains a fragment cannot drag every
     * one of its entities in. Blank entries are skipped rather than matching everything.
     */
    public static boolean matchesName(String path, List<? extends String> names) {
        if (path == null || names == null) return false;
        for (String name : names) {
            if (name != null && !name.isEmpty() && path.contains(name)) return true;
        }
        return false;
    }
}
