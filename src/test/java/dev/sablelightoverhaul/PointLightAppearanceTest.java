package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.PointLightAppearance;
import dev.sablelightoverhaul.lighting.SourceAnchor;
import dev.sablelightoverhaul.lighting.SourcePayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.sablelightoverhaul.lighting.PointLightAppearance.*;

/** A lamp's own point-light look: what its page defaults to, what a bad field falls back to, and how it rides the wire. */
class PointLightAppearanceTest {
    private static final int SENTINEL = 0x5EED1234;
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static void assertNumbers(String what, PointLightAppearance a, double[] expected) {
        for (int i = 0; i < FIELDS.size(); i++)
            assertEquals(expected[i], a.number(FIELDS.get(i)), 0, what + " " + FIELDS.get(i));
    }

    private static final double[] DEFAULTS = {0, 0, 0, 0, 1, 1, 1, 1, 1};

    @Test void legacyPageWithoutAppearanceDefaultsToOffsetsZeroSizesOne() {
        PointLightAppearance a = PointLightAppearance.load(new CompoundTag());
        assertNumbers("empty", a, DEFAULTS);
        assertFalse(a.glow());
        assertTrue(a.ring());
        assertTrue(a.axis());
        assertEquals(DEFAULT, a);
        assertEquals(DEFAULT, PointLightAppearance.load(null), "a page that never saved one is the default");
    }

    @Test void oneBadFieldFallsBackWithoutLosingAValidNeighbour() {
        var t = new CompoundTag();
        t.putString("offsetX", "nope");
        t.putInt("offsetY", 5);
        t.putDouble("offsetZ", Double.NaN);
        t.putInt("glow", 3);
        t.putBoolean("glowRing", false);
        t.putDouble("glowRingSize", Double.POSITIVE_INFINITY);
        t.putDouble("glowAxisLength", 12.5);
        t.putDouble("glowAxisWidth", -3);
        PointLightAppearance a = PointLightAppearance.load(t);
        assertNumbers("bad fields", a, new double[]{0, 5, 0, 0, 0, 1, 1, 12.5, 1});
        assertFalse(a.glow(), "a flag other than 0/1 is not a wish to glow");
    }

    @Test void withRejectsUnknownNonFiniteOutOfBoundsAndOddFlags() {
        PointLightAppearance a = DEFAULT.with("offsetX", -12.25);
        assertThrows(IllegalArgumentException.class, () -> a.with("nope", 1));
        assertThrows(IllegalArgumentException.class, () -> a.with("offsetY", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> a.with("glowRingSize", Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> a.with("offsetZ", 64.5));
        assertThrows(IllegalArgumentException.class, () -> a.with("glowAxisWidth", 0.01));
        assertThrows(IllegalArgumentException.class, () -> a.with("glow", 0.5));
        assertThrows(IllegalArgumentException.class, () -> a.with("glowRing", 2));
        assertEquals(-12.25, a.x(), 0, "a rejected edit leaves the look as it was");
        assertTrue(a.shifted(), "a shifted lamp knows it moved");
    }

    @Test void glowSwitchesAreIndependent() {
        PointLightAppearance a = DEFAULT.with("glow", 1).with("glowRing", 0);
        assertTrue(a.glow());
        assertFalse(a.ring());
        assertTrue(a.axis(), "switching glow and ring leaves the axis alone");
        PointLightAppearance b = a.with("glowAxis", 0);
        assertTrue(b.glow(), "glow survives the ring and axis going quiet");
        assertFalse(b.ring());
        assertFalse(b.axis());
    }

    @Test void sizeAndOffsetLimitsAreInclusive() {
        for (double edge : new double[]{OFFSET_MIN, OFFSET_MAX}) assertEquals(edge, DEFAULT.with("offsetX", edge).x());
        for (double edge : new double[]{SIZE_MIN, SIZE_MAX})
            assertEquals(edge, DEFAULT.with("glowRingSize", edge).ringSize(), "size edge " + edge);
        assertEquals(OFFSET_MAX, new PointLightAppearance(1e9, -1e9, 0, false, false, false, 0, 0, 0).x());
        assertEquals(OFFSET_MIN, new PointLightAppearance(1e9, -1e9, 0, false, false, false, 0, 0, 0).y());
        assertEquals(1, new PointLightAppearance(0, 0, 0, false, false, false, Double.NaN, 0, 0).ringSize(),
            "a non-finite size is not a wish for the smallest one");
        assertEquals(0, new PointLightAppearance(Double.NaN, 0, 0, false, false, false, 1, 1, 1).x());
    }

    @Test void exactlyRepresentableSizesAndFlagsSurviveTheWire() {
        PointLightAppearance a = new PointLightAppearance(0.25, -0.5, 32, true, false, true, 0.5, 0.25, 2);
        var b = buffer();
        try {
            a.write(b);
            b.writeInt(SENTINEL);
            assertEquals(a, PointLightAppearance.read(b), "a wire round trip keeps every field");
            assertEquals(SENTINEL, b.readInt(), "the look read exactly its own bytes");
            assertEquals(0, b.readableBytes());
        } finally { b.release(); }
    }

    private static SourcePayload.State lamp(UUID ship, PointLightAppearance look) {
        return new SourcePayload.State(ship==null?5:4, new SourceAnchor(ship, new BlockPos(1, 64, -3)), 2, false, 12, 0, 0, 3, 0, 0,
            35.5F, -120.25F, 0, 2, 12, 96, true, 61, 0, 0, 0, 0, 0.0F, 0.0F, 0.0F, "", 0, look);
    }

    @Test void twoLampsKeepTheirOwnLooksAcrossTheSourceSnapshot() {
        UUID ship = UUID.fromString("00000000-0000-0000-0000-00000000abcd");
        PointLightAppearance shipLook = DEFAULT.with("offsetX", 12.25).with("offsetY", -0.5).with("offsetZ", 64)
            .with("glowRingSize", 3).with("glowAxisWidth", 0.5);
        assertEquals(shipLook,PointLightAppearance.load(shipLook.save()),"persist all custom fields");
        var payload = new SourcePayload(ResourceLocation.parse("minecraft:overworld"),
            List.of(lamp(ship, shipLook), lamp(null, DEFAULT)));
        var b = buffer();
        try {
            SourcePayload.CODEC.encode(b, payload);
            b.writeInt(SENTINEL);
            SourcePayload decoded = SourcePayload.CODEC.decode(b);
            assertEquals(SENTINEL, b.readInt(), "the snapshot read exactly its own bytes");
            assertEquals(0, b.readableBytes());
            assertEquals(payload, decoded, "nothing in the rows is skewed past the look");
            assertEquals(shipLook, decoded.states().get(0).appearance(), "the ship lamp keeps its offset and glow");
            assertEquals(DEFAULT, decoded.states().get(1).appearance(), "the ground lamp stays plain");
            assertEquals(ship, decoded.states().get(0).anchor().ship());
            assertNull(decoded.states().get(1).anchor().ship());
        } finally { b.release(); }
    }
}
