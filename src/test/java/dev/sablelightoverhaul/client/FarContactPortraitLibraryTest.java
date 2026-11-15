package dev.sablelightoverhaul.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class FarContactPortraitLibraryTest {
    @Test void picksDefaultSpriteBeforeNamedVariants() {
        assertEquals(List.of(sprite("example/head.png")), candidates("""
            {"variants":{"blue":"normal_sprite:example/blue.png","default":"outlined_sprite:example/head.png"}}
            """));
    }

    @Test void offersVariantSpritesInPackOrderAndIgnoresMetadata() {
        assertEquals(List.of(sprite("fox/red.png"), sprite("fox/white.png")), candidates("""
            {"variants":{"red":"outlined_sprite:fox/red.png","layersAllowed":true,
            "white":"sprite:fox/white.png","unsupported":"model:2"}}
            """));
    }

    @Test void explicitModelDefaultLeavesTheBuiltInPortraitInControl() {
        assertTrue(candidates("""
            {"variants":{"rare":"normal_sprite:example/rare.png","default":"model:0"}}
            """).isEmpty());
        assertTrue(candidates("""
            {"variants":{"default":"normal_sprite:invalid path.png"}}
            """).isEmpty());
    }

    @Test void croppedPortraitPreservesTheSpriteAspectRatio() {
        var portrait = FarContactPortraitLibrary.Portrait.crop(sprite("example/head.png"), 64, 64, 16, 8, 32, 16);
        assertEquals(.25f, portrait.u0());
        assertEquals(.75f, portrait.u1());
        assertEquals(.125f, portrait.v0());
        assertEquals(.375f, portrait.v1());
        assertEquals(1, portrait.width());
        assertEquals(.5f, portrait.height());
    }

    private static List<ResourceLocation> candidates(String json) {
        return FarContactPortraitLibrary.spriteCandidates(JsonParser.parseString(json)).toList();
    }

    private static ResourceLocation sprite(String path) {
        return ResourceLocation.fromNamespaceAndPath("xaerominimap", "entity/icon/sprite/" + path);
    }
}
