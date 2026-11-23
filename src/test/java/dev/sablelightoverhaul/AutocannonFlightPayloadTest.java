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
    @Test void phasedFirstAndTerminalEndpointsSurviveTheConfirmedFeed() {
        for (int phase : new int[]{0, 1, 2, 3, 4}) {
            var launch = new AutocannonFlightPayload.Segment(57, Vec3.ZERO, new Vec3(2, 0, 0), false, phase, true);
            var nextTick = new AutocannonFlightPayload.Segment(58, Vec3.ZERO, new Vec3(4, 0, 0), false, 0, false);
            var payload = new AutocannonFlightPayload.Phased(new AutocannonFlightPayload(
                ResourceLocation.withDefaultNamespace("overworld"), new UUID(12, 34), List.of(launch, nextTick)));
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                AutocannonFlightPayload.Phased.CODEC.encode(buf, payload);
                assertEquals(payload, AutocannonFlightPayload.Phased.CODEC.decode(buf));
                assertEquals(0, buf.readableBytes());
            } finally { buf.release(); }
        }
    }

    @Test void nativeFeedPreservesPhasesWithoutChangingLegacyEncoding() {
        var shell = new ShellTrackPayload.Shell(17, 5, 0, 0, 3, 0, 0, 2, 0, 0, new UUID(12, 34));
        var nativeTrack = new AutocannonTrackPayload(new ShellTrackPayload(
            ResourceLocation.withDefaultNamespace("overworld"), List.of(shell)));
        var phased = new AutocannonTrackPayload.Phased(nativeTrack, java.util.Map.of(17, 4));
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            AutocannonTrackPayload.Phased.CODEC.encode(buf, phased);
            assertEquals(phased, AutocannonTrackPayload.Phased.CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
            AutocannonTrackPayload.CODEC.encode(buf, nativeTrack);
            assertEquals(nativeTrack, AutocannonTrackPayload.CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }

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
