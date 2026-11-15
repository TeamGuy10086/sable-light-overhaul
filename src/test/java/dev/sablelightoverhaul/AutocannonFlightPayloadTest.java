package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AutocannonFlightPayloadTest {
    @Test void contactEndpointAndSessionRoundTripExactly() {
        var segment = new AutocannonFlightPayload.Segment(57, new Vec3(-1042.5, 124, 616),
            new Vec3(-1036.21, 122.8125, 616.45), true);
        var payload = new AutocannonFlightPayload(ResourceLocation.withDefaultNamespace("overworld"),
            new UUID(12, 34), List.of(segment));
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            AutocannonFlightPayload.CODEC.encode(buf, payload);
            assertEquals(payload, AutocannonFlightPayload.CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
            assertTrue(segment.visualId() > Integer.MAX_VALUE);
        } finally { buf.release(); }
    }
}
