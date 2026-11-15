package dev.sablelightoverhaul.profiles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the periodic sweep removes, and more importantly what it refuses to remove.
 *
 * <p>The dangerous mistake here is deleting settings for a reason that is not evidence -- a chunk that is not
 * loaded, a view that cannot answer this tick, a body the container has temporarily mislaid. Those cases are
 * the ones worth a test. Where a player is standing is deliberately not one of them: this is a server
 * behaviour, and a cannon in a ship nobody is watching still exists.
 */
class ProfileAuditTest {
    private static final ResourceLocation CANNON = ResourceLocation.parse("createbigcannons:pitch_contraption");
    private static final ResourceLocation WALL = ResourceLocation.parse("minecraft:stone_bricks");
    private static final UUID BODY = UUID.fromString("00000000-0000-0000-0000-0000000000dd");

    private static final java.util.function.Predicate<ResourceLocation> WHITELIST =
            id -> id.getPath().contains("cannon") || id.getPath().contains("contraption");

    /** A world the test drives directly. */
    private static final class Fake implements ProfileAudit.View {
        final Map<ProfileStore.CannonId, BlockPos> resolved = new HashMap<>();
        final Map<BlockPos, ResourceLocation> types = new HashMap<>();
        final java.util.Set<BlockPos> loaded = new java.util.HashSet<>();
        boolean failResolve;

        @Override public BlockPos resolve(ProfileStore.CannonId id) {
            if (failResolve) throw new IllegalStateException("the view could not answer");
            return resolved.get(id);
        }

        @Override public boolean isLoaded(BlockPos pos) {
            return loaded.contains(pos);
        }

        @Override public ResourceLocation typeAt(BlockPos pos) {
            return types.get(pos);
        }
    }

    private static ProfileStore.CannonId id(BlockPos localPos, UUID body) {
        return new ProfileStore.CannonId(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse("minecraft:overworld")), body, localPos);
    }

    private static ProfileStore storeWith(ProfileStore.CannonId... ids) {
        var store = new ProfileStore();
        for (ProfileStore.CannonId one : ids) {
            store.put(new dev.sablelightoverhaul.api.EffectApi.SourceKey(one.dimension(), one.ship(), one.localPos(),
                    CANNON, null), CANNON, "shape.forwardRange", 32.0);
        }
        return store;
    }

    @Test void aCannonReplacedByAWallLosesItsSettings() {
        var pos = new BlockPos(10, 70, 10);
        var cannon = id(BlockPos.ZERO, null);
        var store = storeWith(cannon);
        var view = new Fake();
        view.resolved.put(cannon, pos);
        view.loaded.add(pos);
        view.types.put(pos, WALL);

        assertEquals(1, ProfileAudit.sweep(store, view, WHITELIST).size());
        assertEquals(0, store.size(), "the wall does not inherit the cannon's values");
    }

    @Test void aCannonStillStandingKeepsThem() {
        var pos = new BlockPos(10, 70, 10);
        var cannon = id(BlockPos.ZERO, null);
        var store = storeWith(cannon);
        var view = new Fake();
        view.resolved.put(cannon, pos);
        view.loaded.add(pos);
        view.types.put(pos, CANNON);

        assertTrue(ProfileAudit.sweep(store, view, WHITELIST).isEmpty());
        assertEquals(1, store.size());
    }

    @Test void whereAPlayerStandsChangesNothing() {
        // Far from anybody, and replaced by a wall: someone could argue for skipping it, and the answer is
        // that a cannon's existence does not depend on who is watching. Loaded means checked.
        var pos = new BlockPos(6000, 70, -6000);
        var cannon = id(BlockPos.ZERO, BODY);
        var store = storeWith(cannon);
        var view = new Fake();
        view.resolved.put(cannon, pos);
        view.loaded.add(pos);
        view.types.put(pos, WALL);

        assertEquals(1, ProfileAudit.sweep(store, view, WHITELIST).size(),
                "a force loaded body is checked wherever it is, and so is anything else that is loaded");
    }

    @Test void anUnloadedChunkIsNotEvidence() {
        var cannon = id(BlockPos.ZERO, BODY);
        var store = storeWith(cannon);
        var view = new Fake();
        view.resolved.put(cannon, new BlockPos(900, 70, 900));
        // not in the loaded set, and the type is unknown because it was never asked for

        assertTrue(ProfileAudit.sweep(store, view, WHITELIST).isEmpty(),
                "a ship that is out of loaded chunks must not lose its settings");
        assertEquals(1, store.size());
    }

    @Test void aBodyMissingFromTheContainerIsLeftAlone() {
        // Measured, not assumed: unloading a ship chunks also removes it from the container, so "not in the
        // container" and "gone" are not the same answer. The explicit command is what removes these.
        var cannon = id(BlockPos.ZERO, BODY);
        var store = storeWith(cannon);
        var view = new Fake();
        view.resolved.put(cannon, null);

        assertTrue(ProfileAudit.sweep(store, view, WHITELIST).isEmpty(),
                "a profile that is merely out of sight must not be deleted");
        assertEquals(1, store.size());
    }

    @Test void aBodyThatExistsButIsNotLoadedKeepsItsSettings() {
        // The distinction this whole class rests on: a body that exists is not a body that is gone, and its
        // chunks being unloaded says nothing about the cannon. This is the case that must never delete.
        var cannon = id(BlockPos.ZERO, BODY);
        var store = storeWith(cannon);
        var view = new Fake();
        view.resolved.put(cannon, new BlockPos(2000, 70, 2000));   // present, but nothing is loaded there

        assertTrue(ProfileAudit.sweep(store, view, WHITELIST).isEmpty());
        assertEquals(1, store.size());
    }

    @Test void aViewThatCannotAnswerThisTickDeletesNothing() {
        var store = storeWith(id(BlockPos.ZERO, BODY));
        var view = new Fake();
        view.failResolve = true;

        assertTrue(ProfileAudit.sweep(store, view, WHITELIST).isEmpty());
        assertEquals(1, store.size(), "not knowing is not the same as knowing it is gone");
    }

    @Test void anEmptyOrAbsentStoreIsFine() {
        var view = new Fake();
        assertTrue(ProfileAudit.sweep(new ProfileStore(), view, WHITELIST).isEmpty());
        assertTrue(ProfileAudit.sweep(null, view, WHITELIST).isEmpty());
        assertTrue(ProfileAudit.sweep(new ProfileStore(), view, null).isEmpty());
        assertFalse(ProfileAudit.sweep(new ProfileStore(), null, WHITELIST).iterator().hasNext());
    }
}
