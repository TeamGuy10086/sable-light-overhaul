package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/** Checks the connections most likely to break during a full namespace migration. */
class NamespaceContractTest {
    private static final String ID = "sablelightoverhaul";
    private static final Path RES = Path.of("src/main/resources");

    @Test void entryPointConstantAndPackageMatchMetadata() throws Exception {
        var node = new ClassNode();
        try (var in = getClass().getResourceAsStream("/dev/sablelightoverhaul/SableLightOverhaul.class")) {
            assertNotNull(in);
            new ClassReader(in).accept(node, 0);
        }
        assertEquals("dev/sablelightoverhaul/SableLightOverhaul", node.name);
        assertTrue(node.fields.stream().anyMatch(f -> f.name.equals("ID") && ID.equals(f.value)));
        assertTrue(Files.readString(RES.resolve("META-INF/neoforge.mods.toml")).contains("modId=\"" + ID + "\""));
    }

    @Test void everyMixinConfigPointsToNewPackage() throws Exception {
        for (String suffix : List.of("", ".cbcat", ".cbcmodernwarfare", ".iris", ".sable", ".server")) {
            String name = ID + suffix + ".mixins.json";
            String text = Files.readString(RES.resolve(name));
            assertTrue(text.contains("dev.sablelightoverhaul.mixin"), name);
            assertTrue(Files.readString(RES.resolve("META-INF/neoforge.mods.toml")).contains(name), name);
        }
    }

    @Test void languageAndGpuResourcePathsExist() {
        for (String path : List.of("lang/en_us.json", "lang/zh_cn.json", "shaders/core/slo_gpu_light.json",
                "particles/auto_muzzle.json", "particles/ground_dust.json", "particles/trail.json")) {
            assertTrue(Files.isRegularFile(RES.resolve("assets/" + ID + "/" + path)), path);
        }
    }

    @Test void recipeAndLootReferencesUseNewNamespace() throws Exception {
        for (String path : List.of("recipe/spotlight.json", "recipe/light_registrar.json", "loot_table/blocks/spotlight.json")) {
            assertTrue(Files.readString(RES.resolve("data/" + ID + "/" + path)).contains(ID + ":"), path);
        }
    }

    @Test void shaderBridgeAndFragmentsShareNames() throws Exception {
        String bridge = Files.readString(Path.of("src/main/java/dev/sablelightoverhaul/client/IrisMuzzleBridge.java"));
        for (String name : List.of("sloLightCount", "sloShadowMask", "sloQualityThickness", "sloLight")) {
            assertTrue(bridge.contains(name), name);
            assertTrue(Files.readString(Path.of("patch/source/slo_light.glsl")).contains(name), name);
        }
    }
}
