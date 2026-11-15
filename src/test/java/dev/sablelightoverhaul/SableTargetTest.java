package dev.sablelightoverhaul;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class SableTargetTest {
    @Test void twinMuzzleUsesWorldTransformBeforeSending() throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main/dev/sablelightoverhaul/ServerFlashes.class"))).accept(node, 0);
        var method = node.methods.stream().filter(m -> m.name.equals("emitWorldAutocannon")).findFirst().orElseThrow();
        boolean transformed = false;
        for (var insn : method.instructions) if (insn instanceof MethodInsnNode call) {
            assertNotEquals("emit", call.name, "Twin muzzle must not bypass the CBC world transform");
            transformed |= call.name.equals("emitTransformed");
        }
        assertTrue(transformed);
    }
    private ClassNode read(String name) throws Exception {
        try (var jar = new JarFile("libs/sable.jar")) {
            var node = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getJarEntry(name + ".class"))).accept(node, 0);
            return node;
        }
    }
    @Test void exactInspectedBinary() throws Exception {
        assertEquals("da6c3b66238586603d1dcaa2afb012d36815fbce0a2d5938fbb2936701d42279",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("libs/sable.jar")))));
    }
    @Test void clientPlotUsesParentLightEngine() throws Exception {
        var method = read("dev/ryanhcode/sable/sublevel/plot/ClientLevelPlot").methods.stream()
            .filter(m -> m.name.equals("getLightEngine")).findFirst().orElseThrow();
        boolean found = false;
        for (var insn : method.instructions) if (insn instanceof MethodInsnNode call)
            found |= call.owner.equals("net/minecraft/client/multiplayer/ClientLevel") && call.name.equals("getLightEngine");
        assertTrue(found, "Bridge requires the client plot to share the parent light engine");
    }
    @Test void sodiumReceivesPlotSectionRebuilds() throws Exception {
        var node = read("dev/ryanhcode/sable/mixin/sublevel_render/impl/sodium/SodiumWorldRendererMixin");
        boolean found = false;
        for (var method : node.methods) for (var insn : method.instructions)
            if (insn instanceof MethodInsnNode call) found |= call.name.equals("setDirty")
                && call.owner.contains("SubLevelRenderData");
        assertTrue(found, "Sable must forward section rebuilds to the ship renderer");
    }
}
