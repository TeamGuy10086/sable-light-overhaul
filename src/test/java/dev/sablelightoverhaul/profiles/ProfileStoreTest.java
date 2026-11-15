package dev.sablelightoverhaul.profiles;

import dev.sablelightoverhaul.api.EffectApi;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three promises the per-cannon store makes, checked without a game: a form only applies to the type it
 * was written for, changing the type keeps every other type's values, and a cannon with nothing filed is an
 * ordinary answer rather than a failure.
 */
class ProfileStoreTest {
    private static final ResourceLocation CANNON = ResourceLocation.parse("createbigcannons:pitch_contraption");
    private static final ResourceLocation MISSILE = ResourceLocation.parse("createbigcannons:munitions_contraption");

    private static EffectApi.SourceKey key(BlockPos pos) {
        return new EffectApi.SourceKey(net.minecraft.world.level.Level.OVERWORLD,
                java.util.UUID.fromString("00000000-0000-0000-0000-0000000000aa"), pos, CANNON, null);
    }

    @Test void aFormOnlyAppliesToTheTypeItWasWrittenFor() {
        var store = new ProfileStore();
        store.put(key(BlockPos.ZERO), CANNON, "shape.forwardRange", 32.0);

        assertEquals(32.0, store.resolve(key(BlockPos.ZERO), CANNON).orElseThrow().get("shape.forwardRange"));
        assertTrue(store.resolve(key(BlockPos.ZERO), MISSILE).isEmpty(),
                "a missile is not handed the cannon's form, even at the same identity");
    }

    @Test void changingTheTypeKeepsTheOtherTypesValues() {
        var store = new ProfileStore();
        store.put(key(BlockPos.ZERO), CANNON, "shape.forwardRange", 32.0);
        store.put(key(BlockPos.ZERO), MISSILE, "shape.forwardRange", 64.0);

        var everything = store.allFor(key(BlockPos.ZERO));
        assertEquals(2, everything.size(), "an export carries every type, not just the active one");
        assertEquals(32.0, everything.get(CANNON).get("shape.forwardRange"));
        assertEquals(64.0, everything.get(MISSILE).get("shape.forwardRange"));
    }

    @Test void aCannonWithNothingFiledResolvesToNothing() {
        var store = new ProfileStore();
        store.put(key(BlockPos.ZERO), CANNON, "shape.forwardRange", 32.0);

        assertTrue(store.resolve(key(new BlockPos(40, 70, 40)), CANNON).isEmpty(),
                "a different position is a different cannon");
        assertTrue(store.resolve(new EffectApi.SourceKey(net.minecraft.world.level.Level.OVERWORLD, null,
                        BlockPos.ZERO, CANNON, null), CANNON).isEmpty(),
                "no body is a different identity from a body, and neither is an error");
    }

    @Test void aBodyThatMovesWithItsCannonKeepsItsValues() {
        var store = new ProfileStore();
        var bodyId = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        var onTurret = new EffectApi.SourceKey(net.minecraft.world.level.Level.OVERWORLD, bodyId,
                new BlockPos(3, 1, 0), CANNON, null);
        store.put(onTurret, CANNON, "time.durationTicks", 5.0);

        // The same turret, the same local position, and a different place in the world: still the same cannon.
        var moved = new EffectApi.SourceKey(net.minecraft.world.level.Level.OVERWORLD, bodyId,
                new BlockPos(3, 1, 0), CANNON, null);
        assertEquals(5.0, store.resolve(moved, CANNON).orElseThrow().get("time.durationTicks"),
                "identity is the body and the local position, not where the ship happens to be");
    }

    @Test void forgettingIsHowThePeriodicCheckRemovesAReplacedCannon() {
        var store = new ProfileStore();
        store.put(key(BlockPos.ZERO), CANNON, "shape.forwardRange", 32.0);
        assertTrue(store.forget(key(BlockPos.ZERO)));
        assertFalse(store.forget(key(BlockPos.ZERO)), "forgetting twice is not an error");
        assertEquals(0, store.size());
    }
}
