package dev.sablelightoverhaul.client.ui;

import dev.sablelightoverhaul.LocalizedText;
import dev.sablelightoverhaul.client.MuzzleClientConfig;
import dev.sablelightoverhaul.lighting.LightingCommon;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalizationTest {
    private static Map<String, String> locale(String name) throws Exception {
        var result = new HashMap<String, String>();
        try (var input = Files.newInputStream(Path.of("src/main/resources/assets/sablelightoverhaul/lang/" + name + ".json"))) {
            Language.loadFromJson(input, result::put);
        }
        return result;
    }

    @Test void englishCoversChineseAndEveryConfigDescription() throws Exception {
        var en = locale("en_us");
        var zh = locale("zh_cn");
        assertEquals(zh.keySet(), en.keySet());
        for (var entry : en.entrySet()) {
            assertFalse(entry.getValue().isBlank(), entry.getKey());
            assertFalse(entry.getValue().matches("(?s).*[\\p{IsHan}].*"), entry.getKey());
        }
        for (var spec : java.util.List.of(MuzzleClientConfig.SPEC, LightingCommon.SPEC)) {
            for (var group : new TomlSource(spec, Set.of(), true).groups()) {
                for (var text : java.util.List.of(group.category(), group.section(), group.topic())) {
                    if (text.matches("(?s).*[\\p{IsHan}].*")) assertTrue(en.containsKey(LocalizedText.key(text)), text);
                }
                for (var field : group.fields()) {
                    assertTrue(en.containsKey(field.labelKey()), field.key());
                    if (field.tooltip().matches("(?s).*[\\p{IsHan}].*")) {
                        assertTrue(en.containsKey(LocalizedText.key(field.tooltip())), field.key() + " tooltip " + LocalizedText.key(field.tooltip()) + " " + field.tooltip());
                    }
                }
            }
        }
    }

    @Test void allInventoryItemsHaveBothNames() throws Exception {
        for (var language : java.util.List.of("en_us", "zh_cn")) {
            var entries = locale(language);
            try (var files = Files.list(Path.of("src/main/resources/assets/sablelightoverhaul/models/item"))) {
                for (var file : files.toList()) {
                    String id = file.getFileName().toString().replace(".json", "");
                    assertTrue(entries.containsKey("item.sablelightoverhaul." + id)
                            || entries.containsKey("block.sablelightoverhaul." + id), language + " " + id);
                }
            }
        }
    }

    @Test void descriptionsRenderInEnglishWithoutLosingLiteralPercentSigns() throws Exception {
        var en = locale("en_us");
        Language original = Language.getInstance();
        try {
            install(en);
            for (var config : java.util.List.of(MuzzleClientConfig.SPEC, LightingCommon.SPEC)) {
                for (var group : new TomlSource(config, Set.of(), true).groups()) {
                    for (var field : group.fields()) {
                        String translated = en.get(LocalizedText.key(field.tooltip()));
                        if (translated != null) {
                            assertEquals(translated.replace("%%", "%"), LocalizedText.text(field.tooltip()), field.key());
                        }
                    }
                }
            }
        } finally { Language.inject(original); }
    }

    @Test void serverComponentsUseRecipientLocaleAndPercentSignsSurvive() throws Exception {
        Language original = Language.getInstance();
        // Construct before choosing the recipient's language, as a server does.
        Component message = LocalizedText.component("已移除半径 %s 内 %s 个静态光源登记", 12, 3);
        Component nested = LocalizedText.component("射灯模型：%s", LocalizedText.component("显示"));
        try {
            install(locale("en_us"));
            assertEquals("Removed 3 static light source registrations within radius 12", message.getString());
            assertFalse(nested.getString().contains("显示"));
            assertFalse(nested.getString().contains("%s"));
            String source = "手电筒：颜色 %s / 距离 %s 步（%s 格） / 宽度 %s° / 衰减模式 %s / 距离衰减 %s%% / 强度 %s / 点光半径 %s / 点光亮度 %s";
            String text = LocalizedText.component(source, 0, 1, 16, 45, "Uniform", 50, 15, 3, 5).getString();
            assertTrue(text.contains("50%"), text);
            assertFalse(text.contains("50%%"), text);
            install(locale("zh_cn"));
            assertEquals("已移除半径 12 内 3 个静态光源登记", message.getString());
            assertTrue(nested.getString().contains("显示"));
        } finally {
            Language.inject(original);
        }
    }

    private static void install(Map<String, String> entries) {
        Language.inject(new Language() {
            @Override public String getOrDefault(String key, String fallback) { return entries.getOrDefault(key, fallback); }
            @Override public boolean has(String key) { return entries.containsKey(key); }
            @Override public boolean isDefaultRightToLeft() { return false; }
            @Override public FormattedCharSequence getVisualOrder(FormattedText text) { return FormattedCharSequence.EMPTY; }
        });
    }
}
