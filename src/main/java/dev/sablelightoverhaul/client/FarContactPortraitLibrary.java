package dev.sablelightoverhaul.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Resolves type-only portraits once per resource reload, including Xaero-compatible packs. */
final class FarContactPortraitLibrary extends SimplePreparableReloadListener<Map<ResourceLocation, FarContactPortraitLibrary.Portrait>> {
    private static final String XAERO = "xaerominimap";
    private static final String DEFINITIONS = "entity/icon/definition";
    private static final String SPRITES = "entity/icon/sprite/";
    private static final Map<ResourceLocation, Portrait> VANILLA = vanilla();
    private static Map<ResourceLocation, Portrait> portraits = VANILLA;

    record Portrait(ResourceLocation texture, float u0, float v0, float u1, float v1, float width, float height) {
        static Portrait crop(ResourceLocation texture, int imageWidth, int imageHeight, int x, int y, int w, int h) {
            float longest = Math.max(w, h);
            return new Portrait(texture, (float) x / imageWidth, (float) y / imageHeight,
                (float) (x + w) / imageWidth, (float) (y + h) / imageHeight, w / longest, h / longest);
        }
    }

    static Portrait get(ResourceLocation type) {
        return portraits.get(type);
    }

    @Override
    protected Map<ResourceLocation, Portrait> prepare(ResourceManager resources, ProfilerFiller profiler) {
        var resolved = new HashMap<>(VANILLA);
        var sprites = new HashMap<ResourceLocation, Optional<Portrait>>();
        for (var entry : resources.listResources(DEFINITIONS, id -> id.getNamespace().equals(XAERO)
                && id.getPath().endsWith(".json")).entrySet()) {
            var id = entry.getKey();
            String path = id.getPath().substring(DEFINITIONS.length() + 1, id.getPath().length() - 5);
            int slash = path.indexOf('/');
            if (slash <= 0) continue;
            var type = ResourceLocation.tryBuild(path.substring(0, slash), path.substring(slash + 1));
            if (type == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(type)) continue;
            try (var reader = entry.getValue().openAsReader()) {
                spriteCandidates(JsonParser.parseReader(reader))
                    .map(texture -> sprites.computeIfAbsent(texture, key -> loadSprite(resources, key)))
                    .flatMap(Optional::stream).findFirst().ifPresent(portrait -> resolved.put(type, portrait));
            } catch (IOException | com.google.gson.JsonParseException ignored) {
                // A malformed optional icon definition should not break resource reload or hide the type name.
            }
        }
        return Map.copyOf(resolved);
    }

    @Override
    protected void apply(Map<ResourceLocation, Portrait> prepared, ResourceManager resources, ProfilerFiller profiler) {
        portraits = prepared;
    }

    static Stream<ResourceLocation> spriteCandidates(JsonElement definition) {
        if (!definition.isJsonObject()) return Stream.empty();
        var variants = definition.getAsJsonObject().get("variants");
        if (variants == null || !variants.isJsonObject()) return Stream.empty();
        var choices = variants.getAsJsonObject();
        // A contact has only a type ID. Use the explicit default, or the first available authored variant.
        var values = choices.has("default") ? Stream.of(choices.get("default")) : choices.asMap().values().stream();
        return values.filter(value -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())
            .map(JsonElement::getAsString).flatMap(form -> {
                int separator = form.indexOf(':');
                if (separator < 0) return Stream.empty();
                return switch (form.substring(0, separator)) {
                    case "normal_sprite", "outlined_sprite", "sprite" -> Stream.ofNullable(
                        ResourceLocation.tryBuild(XAERO, SPRITES + form.substring(separator + 1)));
                    default -> Stream.empty();
                };
            });
    }

    private static Optional<Portrait> loadSprite(ResourceManager resources, ResourceLocation texture) {
        var resource = resources.getResource(texture);
        if (resource.isEmpty()) return Optional.empty();
        try (var stream = resource.get().open(); var image = NativeImage.read(stream)) {
            int width = image.getWidth(), height = image.getHeight();
            int left = width, top = height, right = -1, bottom = -1;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if ((image.getPixelRGBA(x, y) >>> 24) <= 25) continue;
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
            if (right >= left) return Optional.of(Portrait.crop(texture, width, height, left, top, right - left + 1, bottom - top + 1));
        } catch (IOException ignored) {
            // Fall back to a vanilla face or a name if the optional sprite cannot be decoded.
        }
        return Optional.empty();
    }

    private static Map<ResourceLocation, Portrait> vanilla() {
        var map = new HashMap<ResourceLocation, Portrait>();
        face(map, "zombie", "zombie/zombie", 64, 64, 8, 8, 8, 8);
        face(map, "husk", "zombie/husk", 64, 64, 8, 8, 8, 8);
        face(map, "drowned", "zombie/drowned", 64, 64, 8, 8, 8, 8);
        face(map, "zombie_villager", "zombie_villager/zombie_villager", 64, 64, 8, 8, 8, 10);
        for (String type : new String[]{"skeleton", "stray", "bogged", "wither_skeleton"}) {
            face(map, type, "skeleton/" + type, 64, 32, 8, 8, 8, 8);
        }
        face(map, "creeper", "creeper/creeper", 64, 32, 8, 8, 8, 8);
        face(map, "enderman", "enderman/enderman", 64, 32, 8, 8, 8, 8);
        face(map, "blaze", "blaze", 64, 32, 8, 8, 8, 8);
        face(map, "ghast", "ghast/ghast", 64, 32, 16, 16, 16, 16);
        face(map, "spider", "spider/spider", 64, 32, 40, 12, 8, 8);
        face(map, "cave_spider", "spider/cave_spider", 64, 32, 40, 12, 8, 8);
        face(map, "warden", "warden/warden", 128, 128, 10, 42, 16, 16);
        face(map, "villager", "villager/villager", 64, 64, 8, 8, 8, 10);
        face(map, "wandering_trader", "wandering_trader", 64, 64, 8, 8, 8, 10);
        face(map, "witch", "witch", 64, 128, 8, 8, 8, 10);
        for (String type : new String[]{"pillager", "vindicator", "evoker", "illusioner"}) {
            face(map, type, "illager/" + type, 64, 64, 8, 8, 8, 10);
        }
        for (String type : new String[]{"piglin", "piglin_brute", "zombified_piglin"}) {
            face(map, type, "piglin/" + type, 64, 64, 8, 8, 10, 8);
        }
        face(map, "vex", "illager/vex", 32, 32, 5, 5, 5, 5);
        face(map, "allay", "allay/allay", 32, 32, 5, 5, 5, 5);
        face(map, "iron_golem", "iron_golem/iron_golem", 128, 128, 8, 8, 8, 10);
        face(map, "snow_golem", "snow_golem", 64, 64, 8, 8, 8, 8);
        face(map, "pig", "pig/pig", 64, 32, 8, 8, 8, 8);
        face(map, "cow", "cow/cow", 64, 32, 6, 6, 8, 8);
        face(map, "mooshroom", "cow/red_mooshroom", 64, 32, 6, 6, 8, 8);
        face(map, "sheep", "sheep/sheep", 64, 32, 8, 8, 6, 6);
        face(map, "chicken", "chicken", 64, 32, 3, 3, 4, 6);
        face(map, "wolf", "wolf/wolf", 64, 32, 4, 4, 6, 6);
        face(map, "cat", "cat/tabby", 64, 32, 5, 5, 5, 4);
        face(map, "ocelot", "cat/ocelot", 64, 32, 5, 5, 5, 4);
        face(map, "fox", "fox/fox", 48, 32, 7, 11, 8, 6);
        face(map, "panda", "panda/panda", 64, 64, 9, 15, 13, 10);
        face(map, "polar_bear", "bear/polarbear", 128, 64, 7, 7, 7, 7);
        face(map, "rabbit", "rabbit/brown", 64, 32, 37, 5, 5, 4);
        face(map, "bee", "bee/bee", 64, 64, 10, 10, 7, 7);
        return Map.copyOf(map);
    }

    // Crop the front face of the primary head cuboid in the vanilla texture layout.
    private static void face(Map<ResourceLocation, Portrait> map, String type, String path,
                             int width, int height, int x, int y, int w, int h) {
        var texture = ResourceLocation.withDefaultNamespace("textures/entity/" + path + ".png");
        map.put(ResourceLocation.withDefaultNamespace(type), Portrait.crop(texture, width, height, x, y, w, h));
    }
}
