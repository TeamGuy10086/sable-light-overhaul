package dev.sablelightoverhaul.profiles;

import dev.sablelightoverhaul.api.EffectApi;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The explicit cleanup: only bodied entries, and only when their body is not in Sable's list. */
class ProfileOrphansTest {
    private static final ResourceLocation CANNON = ResourceLocation.parse("createbigcannons:pitch_contraption");
    private static final UUID HERE = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID GONE = UUID.fromString("00000000-0000-0000-0000-0000000000e2");

    private static EffectApi.SourceKey key(UUID ship) {
        return new EffectApi.SourceKey(Level.OVERWORLD, ship, new BlockPos(1, 2, 3), CANNON, null);
    }

    @Test void aBodyThatIsNotInTheListIsAnOrphan() {
        var store = new ProfileStore();
        store.put(key(GONE), CANNON, "shape.forwardRange", 32.0);
        store.put(key(HERE), CANNON, "shape.forwardRange", 40.0);

        var removed = ProfileOrphans.purge(store, Set.of(HERE));
        assertEquals(1, removed.size());
        assertEquals(GONE, removed.get(0).ship());
        assertEquals(1, store.size(), "the ship that is still there keeps its values");
    }

    @Test void aWorldCannonIsNotAnOrphan() {
        var store = new ProfileStore();
        store.put(key(null), CANNON, "shape.forwardRange", 32.0);

        assertTrue(ProfileOrphans.purge(store, Set.of()).isEmpty(),
                "no body means nothing to compare against Sable, so this is the sweep's question instead");
        assertEquals(1, store.size());
    }

    @Test void nothingToCleanIsNotAnError() {
        assertTrue(ProfileOrphans.purge(new ProfileStore(), Set.of()).isEmpty());
        assertTrue(ProfileOrphans.purge(null, Set.of()).isEmpty());
        assertTrue(ProfileOrphans.purge(new ProfileStore(), null).isEmpty());
        assertEquals(List.of(), ProfileOrphans.purge(new ProfileStore(), Set.of()));
    }
}