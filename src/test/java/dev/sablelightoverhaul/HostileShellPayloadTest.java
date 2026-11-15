package dev.sablelightoverhaul;

import dev.sablelightoverhaul.api.EffectApi;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostileShellPayloadTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");

    @Test void handoffRoundTripsAsAReleaseWithoutAHitSegment() {
        var uuid = UUID.randomUUID();
        var model = HostileShellPayload.Model.HE_203;
        var point = new Vec3(600, 70, 204);
        var release = HostileMathFlightPayload.handoff(uuid, model, point);
        var packet = new HostileMathFlightPayload(new HostileShellPayload(OVERWORLD, 12, List.of(release)));
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            HostileMathFlightPayload.CODEC.encode(buf, packet);
            var decoded = HostileMathFlightPayload.CODEC.decode(buf).snapshot().shells().getFirst();
            assertTrue(HostileMathFlightPayload.isHandoff(decoded));
            assertFalse(decoded.tracer());
            assertEquals(decoded.previous(), decoded.current());
            assertEquals(release, decoded);
            assertFalse(HostileMathFlightPayload.isHandoff(new HostileShellPayload.Shell(
                0, uuid, model, Vec3.ZERO, point, true, true)));
        } finally { buf.release(); }
    }

    @Test void roundTripsAllModelsAndTerminalFlag() {
        var entries = new ArrayList<HostileShellPayload.Shell>();
        for (var model : HostileShellPayload.Model.values()) {
            entries.add(new HostileShellPayload.Shell(model.ordinal() + 1, UUID.randomUUID(), model,
                new Vec3(1700.5, 72, -2400), new Vec3(1701.25, 72.25, -2398), model.ordinal() % 2 == 0, model.ordinal() % 3 == 0));
        }
        var packet = new HostileShellPayload(OVERWORLD, 500, entries);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            HostileShellPayload.CODEC.encode(buf, packet);
            assertEquals(packet, HostileShellPayload.CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }

    @Test void rejectsInvalidCoordinatesAndUnboundedBatches() {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            var bad = new HostileShellPayload.Shell(1, UUID.randomUUID(), HostileShellPayload.Model.AP_76,
                Vec3.ZERO, new Vec3(Double.NaN, 0, 0), false, false);
            HostileShellPayload.CODEC.encode(buf, new HostileShellPayload(OVERWORLD, 500, List.of(bad)));
            assertThrows(IllegalArgumentException.class, () -> HostileShellPayload.CODEC.decode(buf));
            assertThrows(IllegalArgumentException.class, () -> new HostileShellPayload(OVERWORLD, 500,
                java.util.Collections.nCopies(HostileShellPayload.LIMIT + 1, bad)));
            assertFalse(HostileShellPayload.validStep(Vec3.ZERO, new Vec3(129, 0, 0)));
        } finally { buf.release(); }
    }

    @Test void smokeTakeoverIsOptInAndAcknowledgesOnlyTheRecipient() {
        var cancellation = EffectApi.Cancellation.none();
        UUID handled = UUID.randomUUID(), unhandled = UUID.randomUUID();
        cancellation.acknowledgeMuzzleSmoke(handled);
        assertFalse(cancellation.muzzleSmokeHandled(handled));
        cancellation.requestMuzzleSmokeTakeover();
        assertTrue(cancellation.muzzleSmokeTakeoverRequested());
        cancellation.acknowledgeMuzzleSmoke(handled);
        assertTrue(cancellation.muzzleSmokeHandled(handled));
        assertFalse(cancellation.muzzleSmokeHandled(unhandled));
        assertFalse(cancellation.blocks(EffectApi.Section.SPAWN_PARTICLES));
    }
}
