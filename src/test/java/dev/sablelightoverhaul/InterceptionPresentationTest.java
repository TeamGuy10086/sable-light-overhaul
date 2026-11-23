package dev.sablelightoverhaul;

import dev.sablelightoverhaul.api.EffectApi;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterceptionPresentationTest {
    @Test void compatibilityAndTypedOverloadsHaveTheExpectedSignatures() throws Exception {
        assertEquals(void.class, EffectApi.class.getMethod("renderInterception",
            ServerLevel.class, Vec3.class, Vec3.class, float.class).getReturnType());
        assertEquals(void.class, EffectApi.class.getMethod("renderInterception",
            ServerLevel.class, Vec3.class, Vec3.class, ResourceLocation.class, ResourceLocation.class,
            float.class).getReturnType());
    }

    @Test void theWireKeepsMissileAndWarheadIdentityIncludingZeroPower() {
        var payload = new InterceptionPayload(ResourceLocation.parse("minecraft:overworld"), new Vec3(2, 4, 8),
            new Vec3(14, -3, 6), ResourceLocation.parse("wolfsmobs:missile/sm6"),
            ResourceLocation.parse("sablehealthbar:rods_ap_mk2"), 0, 1024);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            InterceptionPayload.CODEC.encode(buffer, payload);
            assertEquals(payload, InterceptionPayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test void sizedVisualKeepsTheLegacyBodyAndDiameter() throws Exception {
        assertEquals(void.class, EffectApi.class.getMethod("renderInterception",
            ServerLevel.class, Vec3.class, Vec3.class, ResourceLocation.class, ResourceLocation.class,
            float.class, float.class).getReturnType());
        var base = new InterceptionPayload(ResourceLocation.parse("minecraft:overworld"), Vec3.ZERO,
            new Vec3(14, 0, 0), InterceptionPayload.UNKNOWN, InterceptionPayload.UNKNOWN, 0, 1024);
        var sized = new InterceptionPayload.Sized(base, .42f);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            InterceptionPayload.Sized.CODEC.encode(buffer, sized);
            assertEquals(sized, InterceptionPayload.Sized.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test void zeroPowerOutcomesUseTheMissileHullWithoutAFireball() {
        var heavy = ResourceLocation.parse("wolfsmobs:missile/vx51");
        var light = ResourceLocation.parse("wolfsmobs:missile/agm179");
        var none = ResourceLocation.parse("sablehealthbar:none");
        var rods = ResourceLocation.parse("sablehealthbar:rods_ap_mk2");
        assertTrue(InterceptionStyle.of(heavy, none, 0).hullScale() > InterceptionStyle.of(light, rods, 0).hullScale());
        assertEquals(0f, InterceptionStyle.of(heavy, none, 0).fireScale());
        assertFalse(InterceptionStyle.of(heavy, rods, 0).explosive());
        for (var kind : new String[] {"he_mk1", "he_mk5", "vx51"}) {
            var warhead = ResourceLocation.fromNamespaceAndPath("sablehealthbar", kind);
            assertTrue(InterceptionStyle.of(heavy, warhead, 12).explosive());
            assertTrue(InterceptionStyle.of(heavy, warhead, 24).fireScale()
                > InterceptionStyle.of(heavy, warhead, 12).fireScale());
            assertFalse(InterceptionStyle.of(heavy, warhead, 0).explosive());
        }
    }

    @Test void audioAndVisualCutoffsAreIndependentOfOutputGain() {
        assertEquals(48f, InterceptionPayload.cbcVolume(24));
        assertEquals(768, InterceptionPayload.audibleRange(24, 1024));
        assertEquals(512, InterceptionPayload.audibleRange(24, 512));
        assertEquals(256, InterceptionPayload.audibleRange(0, 1024));
        assertEquals(InterceptionStyle.Detail.FAR, InterceptionStyle.Detail.at(1024));
        assertEquals(InterceptionStyle.Detail.FAR, InterceptionStyle.Detail.at(1536));
        assertNull(InterceptionStyle.Detail.at(1537));
        assertTrue(InterceptionPayload.receives(1500 * 1500, 512));
        assertTrue(InterceptionPayload.receives(1800 * 1800, 2048));
        assertFalse(InterceptionPayload.receives(1800 * 1800, 1024));
    }
}
