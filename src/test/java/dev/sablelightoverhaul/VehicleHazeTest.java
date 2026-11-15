package dev.sablelightoverhaul;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import dev.sablelightoverhaul.lighting.GroundDustParticleData;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleHazeTest {
    @Test void smokeArrivesAtTheShotAndLeavesWithoutOpacityPop() {
        assertEquals(0, HazeEnvelope.opacity(0, 100));
        assertEquals(0, HazeEnvelope.opacity(1, 100));
        assertEquals(0, HazeEnvelope.opacity(Float.NaN, 100));
        // The rise is a fixed short time rather than a share of the life: one tick into a 100 tick puff
        // is most of the way in, and it is fully opaque by the second tick. The old envelope needed 12%
        // of the life, i.e. 0.6 s here, which read in game as the smoke arriving after the animation.
        assertTrue(HazeEnvelope.opacity(1F / 100F, 100) > .3F);
        assertEquals(1, HazeEnvelope.opacity(2F / 100F, 100), 1e-6);
        assertEquals(1, HazeEnvelope.opacity(.3F, 100), 1e-6);
        // A short-lived puff arrives just as fast rather than tying the rise to its lifetime.
        assertTrue(HazeEnvelope.opacity(1F / 10F, 10) > .3F);
        // And it still fades out over its last stretch, monotonically, never above 1.
        for (int i = 0; i <= 1000; i++) {
            float alpha = HazeEnvelope.opacity(i / 1000F, 100);
            assertTrue(alpha >= 0 && alpha <= 1);
        }
    }
    @Test void overlappingLobesDoNotTripleTheRequestedOpacity() {
        for (float alpha : new float[]{0, .02F, .09F, .3F, 1}) {
            float layer = HazeEnvelope.layerAlpha(alpha, 3);
            assertEquals(alpha, 1 - Math.pow(1 - layer, 3), 1e-6);
        }
    }
    @Test void oldParticleCommandsRemainOrdinaryDust() {
        var old = GroundDustParticleData.CODEC.codec().parse(JsonOps.INSTANCE,
            JsonParser.parseString("{\"size\":2.4}")).getOrThrow();
        assertFalse(old.haze());
        assertFalse(new GroundDustParticleData(2, 0xffffff, 40, 1, false, .75F, .08F, .2F).haze());
    }
    @Test void networkRoundTripKeepsModeAndFollowingPayloadAligned() {
        for (boolean haze : new boolean[]{false, true}) {
            var data = new GroundDustParticleData(3.5F, 0xd0c9bb, 100, 1.5F, false, .97F, .0005F, .09F, haze);
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                GroundDustParticleData.STREAM_CODEC.encode(buf, data);
                buf.writeInt(0x12345678);
                assertEquals(data, GroundDustParticleData.STREAM_CODEC.decode(buf));
                assertEquals(0x12345678, buf.readInt());
                assertEquals(0, buf.readableBytes());
            } finally { buf.release(); }
            assertEquals(haze, data.withColor(0xffffff).haze());
            assertEquals(haze, data.with(4, 80, 2, .95F, 0, .1F).haze());
        }
    }
}
