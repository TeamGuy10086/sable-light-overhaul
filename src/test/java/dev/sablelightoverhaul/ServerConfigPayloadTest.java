package dev.sablelightoverhaul;

import dev.sablelightoverhaul.client.ui.FieldSpec;
import dev.sablelightoverhaul.client.ui.FieldValue;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** The server-config packets have to survive their own codec before they can survive a network. */
class ServerConfigPayloadTest {
    @Test
    void catalogueRoundTrips() {
        var spec = new FieldSpec("spotlights.beam.volume", "射灯", "体积光亮度", "说明文字",
                FieldSpec.Kind.SLIDER, 0.0, 2.0, 0.01, List.of(), false,
                "sablelightoverhaul.config.key.spotlights.beam.volume");
        var payload = new ServerConfigCatalogPayload(List.of(
                new ServerConfigCatalogPayload.Entry("光源", "射灯", "体积光", spec, new FieldValue.Num(0.25))), true);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        ServerConfigCatalogPayload.CODEC.encode(buf, payload);
        var decoded = ServerConfigCatalogPayload.CODEC.decode(buf);
        org.junit.jupiter.api.Assertions.assertEquals(1, decoded.entries().size());
        var entry = decoded.entries().getFirst();
        org.junit.jupiter.api.Assertions.assertEquals("spotlights.beam.volume", entry.spec().key());
        org.junit.jupiter.api.Assertions.assertEquals("体积光亮度", entry.spec().label());
        org.junit.jupiter.api.Assertions.assertEquals(0.25, ((FieldValue.Num) entry.value()).value(), 0.0001);
        org.junit.jupiter.api.Assertions.assertTrue(decoded.writable());
    }

    @Test
    void serverCatalogueIsComplete() {
        var entries = ServerConfigService.snapshot();
        org.junit.jupiter.api.Assertions.assertEquals(178, entries.size());
        for (var entry : entries) {
            org.junit.jupiter.api.Assertions.assertNotNull(entry.value(), entry.spec().key() + " has no value");
            org.junit.jupiter.api.Assertions.assertFalse(entry.spec().label().isBlank());
        }
    }

    @Test
    void editRoundTripsEveryValueKind() {
        for (FieldValue value : List.of(new FieldValue.Num(1.5), new FieldValue.Bool(true),
                new FieldValue.Text("a, b"), new FieldValue.Choice(2, "BOTH"))) {
            var payload = new ServerConfigEditPayload("shockwave.mode", value);
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            ServerConfigEditPayload.CODEC.encode(buf, payload);
            var decoded = ServerConfigEditPayload.CODEC.decode(buf);
            org.junit.jupiter.api.Assertions.assertEquals(value, decoded.value());
        }
    }
}
