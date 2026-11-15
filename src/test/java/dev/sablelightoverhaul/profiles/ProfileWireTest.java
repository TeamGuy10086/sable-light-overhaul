package dev.sablelightoverhaul.profiles;

import dev.sablelightoverhaul.api.EffectApi;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The identity has to survive the trip, including the part that is absent.
 *
 * <p>A cannon with no body and a cannon with one are different cannons, so the null case is the interesting
 * one: an encoding that quietly turned "no body" into a body would file two different worlds' settings
 * together, and a page would then be editing the wrong cannon.
 */
class ProfileWireTest {
    private static EffectApi.SourceKey roundTrip(EffectApi.SourceKey key) {
        var buffer = Unpooled.buffer();
        try {
            ProfileWire.SOURCE_KEY.encode(buffer, key);
            return ProfileWire.SOURCE_KEY.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test void everythingSurvivesTheTrip() {
        var key = new EffectApi.SourceKey(Level.OVERWORLD,
                UUID.fromString("00000000-0000-0000-0000-0000000000ff"),
                new BlockPos(-1234, 70, 5678),
                ResourceLocation.parse("createbigcannons:pitch_contraption"),
                "左炮塔");
        var back = roundTrip(key);
        assertEquals(key.dimension(), back.dimension());
        assertEquals(key.ship(), back.ship());
        assertEquals(key.localPos(), back.localPos());
        assertEquals(key.typeId(), back.typeId());
        assertEquals(key.customName(), back.customName());
    }

    @Test void aCannonWithNoBodyStaysBodiless() {
        var key = new EffectApi.SourceKey(Level.OVERWORLD, null, new BlockPos(3, 1, 0),
                ResourceLocation.parse("createbigcannons:cannon_mount"), null);
        var back = roundTrip(key);
        assertNull(back.ship(), "no body must not come back as a body");
        assertNull(back.customName(), "and neither must the absence of a custom name");
        assertEquals(key.localPos(), back.localPos());
    }

    @Test void aNegativePositionSurvivesAndSoDoesAnotherDimension() {
        // The position is written as one long, which is where a sign mistake would show up.
        var key = new EffectApi.SourceKey(Level.NETHER, null, new BlockPos(-30000000, -64, 29999999),
                ResourceLocation.parse("minecraft:stone"), null);
        var back = roundTrip(key);
        assertEquals(key.dimension(), back.dimension());
        assertEquals(key.localPos(), back.localPos());
    }
}
