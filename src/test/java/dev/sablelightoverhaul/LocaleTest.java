package dev.sablelightoverhaul;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sablelightoverhaul.client.MuzzleClientConfig;
import dev.sablelightoverhaul.client.ui.FieldSpec;
import dev.sablelightoverhaul.client.ui.TomlSource;
import dev.sablelightoverhaul.lighting.LightingCommon;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every catalogue row must have a Chinese label in the language file.
 *
 * <p>The editor falls back to the spec label when a key is missing, which is exactly why this has to be a
 * test: a missing entry is invisible in code review and only shows up as an English word in a Chinese UI.
 */
class LocaleTest {
    @Test
    void everyCatalogueKeyHasAChineseLabel() throws Exception {
        JsonObject zh;
        var stream = LocaleTest.class.getResourceAsStream("/assets/sablelightoverhaul/lang/zh_cn.json");
        org.junit.jupiter.api.Assertions.assertNotNull(stream, "zh_cn.json missing from resources");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            zh = JsonParser.parseReader(reader).getAsJsonObject();
        }

        Set<String> missing = new LinkedHashSet<>();
        Set<String> keys = new LinkedHashSet<>();
        for (var entry : java.util.List.of(
                java.util.Map.entry("client", MuzzleClientConfig.SPEC),
                java.util.Map.entry("server", LightingCommon.SPEC))) {
            TomlSource source = new TomlSource(
                    (net.neoforged.neoforge.common.ModConfigSpec) entry.getValue(), Set.of());
            for (var group : source.groups()) {
                for (FieldSpec field : group.fields()) {
                    keys.add(field.key());
                    String labelKey = field.labelKey();
                    if (labelKey == null || !zh.has(labelKey) || zh.get(labelKey).getAsString().isBlank()) {
                        missing.add(field.key() + " -> " + labelKey);
                    }
                }
            }
        }
        // Eight paths exist on both sides (disco.* and debug.effects); they share one path-keyed label.
        org.junit.jupiter.api.Assertions.assertEquals(505, keys.size(), "unique catalogue paths");
        org.junit.jupiter.api.Assertions.assertTrue(missing.isEmpty(),
                "missing Chinese labels (" + missing.size() + "):\n" + String.join("\n", missing));
    }
}
