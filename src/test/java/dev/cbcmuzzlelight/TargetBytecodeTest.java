package dev.cbcmuzzlelight;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies exact uploaded binaries, without loading their dependency graphs or pretending to launch Minecraft. */
class TargetBytecodeTest {
    /**
     * The Iris bridge is deliberate and requested, but it must stay a contained, optional,
     * compile-only integration: Iris linkage may appear only in these two classes, and the retired
     * emission uniform must never come back.
     */
    private static final java.util.Set<String> IRIS_BRIDGE_CLASSES = java.util.Set.of(
            "dev/cbcmuzzlelight/client/IrisMuzzleBridge.class",
            "dev/cbcmuzzlelight/mixin/IrisUniformMixin.class");

    @Test void irisLinkageIsConfinedToTheOptionalBridge() throws IOException {
        var root = Path.of("build/classes/java/main");
        try(var files=Files.walk(root)) {
            for(var file:files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String binary=new String(Files.readAllBytes(file),java.nio.charset.StandardCharsets.ISO_8859_1);
                assertFalse(binary.contains("cbcEmissionSettings"),"No retired emission uniform in "+file);
                if(!binary.contains("net/irisshaders/")) continue;
                String relative=root.relativize(file).toString().replace('\\','/');
                assertTrue(IRIS_BRIDGE_CLASSES.contains(relative),"Unexpected Iris linkage in "+relative);
            }
        }
    }
    /** The bridge must remain gated by bytecode presence, so Iris stays optional and servers stay clean. */
    @Test void irisMixinConfigIsRegisteredAndGated() throws IOException {
        String metadata=Files.readString(Path.of("src/main/resources/META-INF/neoforge.mods.toml"));
        assertTrue(metadata.contains("cbcmuzzlelight.iris.mixins.json"),"Iris bridge config should be registered");
        String config=Files.readString(Path.of("src/main/resources/cbcmuzzlelight.iris.mixins.json"));
        assertTrue(config.contains("IrisMixinPlugin"),"Iris mixins must stay gated by the bytecode-presence plugin");
    }
    @Test void nativeSmokeVisualsAreUntouched() throws IOException {
        var smoke=new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main/dev/cbcmuzzlelight/mixin/CannonSmokeMixin.class"))).accept(smoke,0);
        for(var method:smoke.methods) {
            for(var insn:method.instructions) {
                if(insn instanceof FieldInsnNode field)
                    assertFalse(java.util.Set.of("rCol","gCol","bCol","alpha").contains(field.name),"Native smoke visual field must not be read or written");
            }
            if(method.visibleAnnotations!=null) for(var annotation:method.visibleAnnotations)
                assertFalse(annotation.values!=null && annotation.values.toString().contains("getLightColor"));
        }
    }
    @Test void smokeFallbackUsesCapturedBaseConstructorAndTick() throws IOException {
        String prefix="rbasamoyai/createbigcannons/effects/particles/smoke/";
        var smoke=read("cbc.jar",prefix+"CannonSmokeParticle");
        var fallback=read("cbc.jar",prefix+"FallbackCannonSmokeParticle");
        assertEquals(prefix+"CannonSmokeParticle",fallback.superName);
        assertTrue(fallback.methods.stream().noneMatch(m -> m.name.equals("tick")));
        assertTrue(smoke.methods.stream().anyMatch(m -> m.name.equals("tick") && m.desc.equals("()V")));
        assertTrue(smoke.methods.stream().anyMatch(m -> m.name.equals("getLightColor") && m.desc.equals("(F)I")));
        assertEquals(1,smoke.methods.stream().filter(m -> m.name.equals("<init>")).count());
    }
    private ClassNode read(String jar, String name) throws IOException {
        try (var zip = new JarFile(Path.of("libs", jar).toFile()); var input = zip.getInputStream(zip.getJarEntry(name + ".class"))) {
            var node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
    private void assertHook(String clazz, String method, String descriptor) throws IOException {
        var node = read("cbc.jar", "rbasamoyai/createbigcannons/cannon_control/contraption/" + clazz);
        var match = node.methods.stream().filter(m -> m.name.equals(method) && m.desc.equals(descriptor)).findFirst().orElseThrow();
        String direction = clazz.contains("Autocannon") ? "vec1" : "vec";
        assertEquals(1, match.localVariables.stream().filter(v -> v.name.equals(direction)
                && v.desc.equals("Lnet/minecraft/world/phys/Vec3;")).count(), "Captured direction local");
        int calls = 0;
        for (var instruction : match.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("rbasamoyai/createbigcannons/CBCCompatTransformers")
                    && call.name.equals("transformVec3")
                    && call.desc.equals("(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;")) ++calls;
        }
        assertEquals(1, calls, clazz + "." + method);
    }
    @Test void bothSupportedDischargePathsHaveOneExactHook() throws IOException {
        String firing = "(Lnet/minecraft/server/level/ServerLevel;Lrbasamoyai/createbigcannons/cannon_control/contraption/PitchOrientedContraptionEntity;)V";
        assertHook("MountedBigCannonContraption", "fireShot", firing);
        assertHook("MountedAutocannonContraption", "fireShot", firing);
    }
    @Test void nativePlumeHasTheExpectedBaseAndDirectionField() throws IOException {
        var node = read("cbc.jar", "rbasamoyai/createbigcannons/effects/particles/plumes/BigCannonPlumeParticle");
        assertEquals("net/minecraft/client/particle/NoRenderParticle", node.superName);
        assertTrue(node.fields.stream().anyMatch(f -> f.name.equals("direction") && f.desc.equals("Lnet/minecraft/world/phys/Vec3;")));
        assertEquals(1,node.methods.stream().filter(m -> m.name.equals("tick") && m.desc.equals("()V")).count());
    }
    @Test void noBallParticleAndNoMortarHookAreShipped() throws IOException {
        try (var paths = Files.walk(Path.of("build/classes/java/main"))) {
            for (var path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                var node = new ClassNode(); new ClassReader(Files.readAllBytes(path)).accept(node,0);
                for (var method : node.methods) {
                    for (var insn : method.instructions) {
                        if (insn instanceof FieldInsnNode f) assertFalse(f.owner.equals("net/minecraft/core/particles/ParticleTypes") && f.name.equals("FLASH"));
                    }
                    if (method.invisibleAnnotations != null) for (var annotation : method.invisibleAnnotations)
                        assertFalse(annotation.values != null && annotation.values.toString().contains("actuallyFireDropMortar"));
                    if (method.visibleAnnotations != null) for (var annotation : method.visibleAnnotations)
                        assertFalse(annotation.values != null && annotation.values.toString().contains("actuallyFireDropMortar"));
                }
            }
        }
    }
}
