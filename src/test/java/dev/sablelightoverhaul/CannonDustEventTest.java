package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.GroundDustParticleData;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CannonDustEventTest {
    private GroundDustParticleData dust(boolean haze) { return new GroundDustParticleData(3, 0xffffff, 100, 1.5F, false, .97F, 0, .09F, haze); }
    @Test void muzzleSmokeNeedsNoVehicleSurface() {
        var event = new CannonDustPayload(ResourceLocation.parse("minecraft:overworld"), new Vec3(20, 70, 30), new Vec3(0, 0, 1), 55,
            List.of(), 0, 4, .8F, dust(false), dust(true), 0.03f, 0.05f, 0.0f, 1.0f, 0.02f, 3.0f, 1.6f, 31415);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            CannonDustPayload.CODEC.encode(buffer, event); buffer.writeInt(12345);
            var decoded = CannonDustPayload.CODEC.decode(buffer);
            assertEquals(event, decoded); assertEquals(4, decoded.hazeCount());
            assertEquals(31415, decoded.emitterId());
            assertEquals(12345, buffer.readInt()); assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void eventRetainsHullAndTurretPoints() {
        var points = List.of(new CannonDustPayload.Surface(new Vec3(0, 1, 0), new Vec3(0, 1, 0)),
            new CannonDustPayload.Surface(new Vec3(0, 3, 0), new Vec3(0, .8, .6)));
        var event = new CannonDustPayload(ResourceLocation.parse("minecraft:overworld"), Vec3.ZERO, new Vec3(1, 0, 0), 1, points, 14, 4, .8F, dust(false), dust(true), 0.03f, 0.05f, 0.0f, 1.0f, 0.02f, 3.0f, 1.6f);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try { CannonDustPayload.CODEC.encode(buffer, event); assertEquals(event, CannonDustPayload.CODEC.decode(buffer)); }
        finally { buffer.release(); }
    }
    @Test void optionalAutocannonChannelCarriesTheExistingEventUnchanged() {
        var event = new CannonDustPayload(ResourceLocation.parse("minecraft:overworld"), Vec3.ZERO, new Vec3(0, 0, 1), 9,
            List.of(), 0, 1, .8F, dust(false), dust(true), .02f, .05f, 0, 1, .02f, 3, 1.6f, 17);
        var soft = new AutocannonHazePayload(event);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            assertNotEquals(event.type().id(), soft.type().id());
            AutocannonHazePayload.CODEC.encode(buffer, soft);
            assertEquals(event, CannonDustPayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
            CannonDustPayload.CODEC.encode(buffer, event);
            assertEquals(soft, AutocannonHazePayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void surfaceMotionStaysTangentEvenOnRotatedHull() {
        Vec3 n = new Vec3(0, .8, .6);
        Vec3 tangent = CannonDustMotion.tangent(new Vec3(2, 3, 4), n, new Vec3(0, 0, 1));
        assertEquals(0, tangent.dot(n), 1e-8); assertEquals(1, tangent.length(), 1e-8);
        assertEquals(1, CannonDustMotion.tangent(n, n, n).length(), 1e-8);
    }
    @Test void shotPushesHazeHorizontallyAwayFromTheMuzzleAndSpeedIsBounded() {
        // Horizontal radial: a puff outside the radius keeps its velocity, and the push itself never has
        // a vertical component -- a shot clears a hole in front of the gun instead of lifting the smoke.
        assertEquals(Vec3.ZERO, CannonDustMotion.gustHorizontal(new Vec3(7, 0, 0), Vec3.ZERO, 6, .06));
        assertEquals(Vec3.ZERO, CannonDustMotion.gustHorizontal(new Vec3(0, 0, -7), Vec3.ZERO, 6, .06));
        assertEquals(Vec3.ZERO, CannonDustMotion.gustHorizontal(new Vec3(1, 0, 0), Vec3.ZERO, 6, 0));
        assertEquals(Vec3.ZERO, CannonDustMotion.gustHorizontal(new Vec3(1, 0, 0), Vec3.ZERO, 0, .06));
        Vec3 sideways = CannonDustMotion.gustHorizontal(new Vec3(0, 8, 2), Vec3.ZERO, 6, .06);
        assertEquals(0, sideways.y, 1e-9);
        assertTrue(sideways.z > 0);
        // Straight over the barrel there is no horizontal direction to leave along, but a puff that sits
        // there still has to be pushed off it, so any horizontal direction is taken, none of it vertical.
        Vec3 overhead = CannonDustMotion.gustHorizontal(new Vec3(0, 5, 0), Vec3.ZERO, 6, .06);
        assertEquals(0, overhead.y, 1e-9);
        assertEquals(.06, overhead.length(), 1e-9);
        // Strongest at the muzzle, fading to nothing at the radius.
        assertTrue(CannonDustMotion.gustHorizontal(new Vec3(1, 0, 0), Vec3.ZERO, 6, .06).length()
            > CannonDustMotion.gustHorizontal(new Vec3(4, 0, 0), Vec3.ZERO, 6, .06).length());
        // Repeated shots through the cap converge on the cap instead of accelerating without limit.
        Vec3 velocity = Vec3.ZERO;
        for (int i = 0; i < 100; i++) velocity = CannonDustMotion.cap(velocity.add(new Vec3(1, 0, 0).scale(.06)), .22);
        assertEquals(.22, velocity.length(), 1e-8);
    }
    @Test void acceptedAutocannonHazeDefaultKeepsTheTankPathIndependent() throws Exception {
        assertTrue(dev.sablelightoverhaul.lighting.LightingCommon.AUTO_HAZE_ENABLED.getDefault(),
            "owner accepted snapshot enables autocannon haze; its gate remains independent");
        String flashes = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/dev/sablelightoverhaul/ServerFlashes.java"));
        assertTrue(flashes.contains("AUTO_HAZE_ENABLED"),
            "the autocannon haze gate must be in the flash sender");
        String tank = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/dev/sablelightoverhaul/MediumCannonEffects.java"));
        assertFalse(tank.contains("AUTO_HAZE_ENABLED"),
            "the tank cannon's haze must not be disabled by the autocannon switch");
    }
    @Test void rejectsUnboundedParticleEvents() {
        assertThrows(IllegalArgumentException.class, () -> new CannonDustPayload(ResourceLocation.parse("minecraft:overworld"), Vec3.ZERO,
            new Vec3(0, 0, 1), 0, List.of(), 201, 4, .8F, dust(false), dust(true), 0.03f, 0.05f, 0.0f, 1.0f, 0.02f, 3.0f, 1.6f));
    }
}
