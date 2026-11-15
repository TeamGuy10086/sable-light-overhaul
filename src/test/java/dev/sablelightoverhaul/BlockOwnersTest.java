package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.BlockOwners;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class BlockOwnersTest {
    @Test void ownerRoundTripsThroughTheBlockEntityTag() {
        UUID id = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        BlockOwners.save(tag, id);
        org.junit.jupiter.api.Assertions.assertEquals(id, BlockOwners.load(tag));
    }

    @Test void legacyBlocksHaveNoOwner() {
        org.junit.jupiter.api.Assertions.assertNull(BlockOwners.load(new CompoundTag()));
    }
}
