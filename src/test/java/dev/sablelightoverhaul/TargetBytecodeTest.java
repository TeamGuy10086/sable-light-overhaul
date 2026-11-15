package dev.sablelightoverhaul;

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
            "dev/sablelightoverhaul/client/IrisMuzzleBridge.class",
            "dev/sablelightoverhaul/mixin/IrisUniformMixin.class",
            // The sampler half of the same bridge: the vanilla light field has to be a dynamic 3D sampler, and
            // a SamplerHolder is only available where Iris builds its samplers, not where it builds its
            // uniforms. Same containment, same optionality, same gate.
            "dev/sablelightoverhaul/mixin/IrisFieldSamplerMixin.class");

    @Test void irisLinkageIsConfinedToTheOptionalBridge() throws IOException {
        var root = Path.of("build/classes/java/main");
        try(var files=Files.walk(root)) {
            for(var file:files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String binary=new String(Files.readAllBytes(file),java.nio.charset.StandardCharsets.ISO_8859_1);
                assertFalse(binary.contains("sloEmissionSettings"),"No retired emission uniform in "+file);
                if(!binary.contains("net/irisshaders/")) continue;
                String relative=root.relativize(file).toString().replace('\\','/');
                assertTrue(IRIS_BRIDGE_CLASSES.contains(relative),"Unexpected Iris linkage in "+relative);
            }
        }
    }
    /** The bridge must remain gated by bytecode presence, so Iris stays optional and servers stay clean. */
    @Test void irisMixinConfigIsRegisteredAndGated() throws IOException {
        String metadata=Files.readString(Path.of("src/main/resources/META-INF/neoforge.mods.toml"));
        assertTrue(metadata.contains("sablelightoverhaul.iris.mixins.json"),"Iris bridge config should be registered");
        String config=Files.readString(Path.of("src/main/resources/sablelightoverhaul.iris.mixins.json"));
        assertTrue(config.contains("IrisMixinPlugin"),"Iris mixins must stay gated by the bytecode-presence plugin");
    }
    /**
     * The per-light style word is a contract between this mod and the patched shader: bits 0..5 are the
     * faces a lamp emits through, bit 6 is the flood mode, and the two sides name them separately. A
     * mismatch is a compile error on neither side -- it is a lamp that lights the wrong way, or a face
     * that never lights -- so the numbers are compared here, against the shader source that ships.
     */
    @Test void theLightStyleBitsMatchTheShader() throws IOException {
        assertEquals(dev.sablelightoverhaul.lighting.RegisteredSources.FACES_ALL,
                dev.sablelightoverhaul.client.IrisMuzzleBridge.STYLE_FACES,
                "the page, the payload and the bridge must agree on how many faces there are");
        String shader=Files.readString(Path.of("patch/source/slo_light.glsl"));
        assertEquals(dev.sablelightoverhaul.client.IrisMuzzleBridge.STYLE_FACES, define(shader,"SLO_FACES_ALL"));
        assertEquals(dev.sablelightoverhaul.client.IrisMuzzleBridge.STYLE_FLOOD, define(shader,"SLO_STYLE_FLOOD"));
        // Six consecutive single bits, in the order the page labels them (down, up, north, south, west, east).
        String[] axes={"PX","NX","PY","NY","PZ","NZ"};
        for(int i=0;i<axes.length;i++) assertEquals(1<<i,define(shader,"SLO_FACE_"+axes[i]),axes[i]);
        // The two remaining fields of the style word, and the shift each side uses to find them.
        assertEquals(dev.sablelightoverhaul.client.IrisMuzzleBridge.STYLE_CONE_SHIFT,
                define(shader,"SLO_CONE_COUNT_SHIFT"));
        assertEquals(dev.sablelightoverhaul.client.IrisMuzzleBridge.STYLE_BODY_SHIFT,
                define(shader,"SLO_BODY_SHIFT"));
        assertEquals(7,define(shader,"SLO_CONE_COUNT_MASK"),"three bits hold 0..4 cones");
        assertEquals(7,define(shader,"SLO_BODY_MASK"),"three bits hold 0..7 bodies");
    }

    /**
     * A negative cloud reach is a contract too, and an even quieter one than a bit: the mod writes
     * {@code -glow} for a tracer or a blast to say "this light named its glow in blocks and wants the
     * patch to fill the gate", and the shader has to read the sign before it uses the magnitude. Drop
     * either half and nothing fails to compile -- the shell's cloud light simply goes back to a small
     * ball, or a light that asked for a glow gets no cloud light at all.
     */
    @Test void theNegativeCloudReachIsReadAsAFlag() throws IOException {
        String writer=Files.readString(Path.of(
                "src/main/java/dev/sablelightoverhaul/client/AdvancedLights.java"));
        assertTrue(writer.contains("return -glow;"),
                "the mod has to send the glow negated for the shader to see the flag");
        String shader=Files.readString(Path.of("patch/source/slo_clouds.glsl"));
        assertTrue(shader.contains("float reach = abs(axis.z);"),
                "the shader has to take the magnitude of the cloud reach");
        assertTrue(shader.contains("axis.z < 0.0"),
                "and it has to test the sign to know the patch fills the gate");
    }

    /**
     * A log line must not name a project this one no longer is.
     *
     * <p>{@code CBC Muzzle Light} was this mod's name before it was merged into the
     * {@code sablelightoverhaul} namespace, and every log line still began with it. That matters more than
     * cosmetics: the prefix is how a reader finds this mod's output in a log the size of a modpack's, and a
     * prefix that names something else sends them looking at the wrong mod's config, the wrong jar and the
     * wrong documentation -- which is exactly what happened.
     */
    @Test void noLogLineNamesTheRetiredProject() throws IOException {
        try (var files = Files.walk(Path.of("src/main"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = file.toString().replace('\\', '/');
                if (!name.endsWith(".java") && !name.endsWith(".json")) continue;
                String text = Files.readString(file);
                assertFalse(text.contains("CBC Muzzle Light"),
                        file + " still carries the retired log prefix 'CBC Muzzle Light'; logs from this mod "
                                + "must be findable by its own name");
            }
        }
    }

    /**
     * A uniform supplier must be a pure function of the frame: running it twice must produce the same value.
     *
     * <p>Iris calls a PER_FRAME supplier once for every program it builds, and a frame builds many -- deferred,
     * each translucent pass, water, the hand. A supplier that rewrites its own source array in place therefore
     * applies its transform once per program instead of once per frame: the field origins came out as the world
     * origin minus the camera seven times over, so every slot past the first two was tens of thousands of blocks
     * from the surface being shaded and the lamps that owned them lit nothing. The two slots that survived were
     * the two the light list rewrites every tick, which is why the symptom looked like some lamps working and
     * others not rather than like arithmetic.
     *
     * <p>A source-level check, because the fault is one `=` in the wrong array and the build is green either way.
     */
    @Test void theFieldTravelsInAChannelThatArrives() throws IOException {
        String bridge = stripLineComments(Files.readString(Path.of(
                "src/main/java/dev/sablelightoverhaul/client/IrisMuzzleBridge.java")));
        assertFalse(bridge.contains("uniform4f") && bridge.contains("sloFieldOrigin"),
                "the field must not be carried by a uniform of its own. sloFieldOrigin was tried as a packed "
                        + "array (the shader read w==0 in every program for six rounds, so the branch was never "
                        + "entered), as \"sloFieldOrigin[i]\" and as \"sloFieldOrigin0..31\" (Iris resolved no "
                        + "location for any of them and both lamps went dark). Every uniform that has ever worked "
                        + "here is a name the shader declares as one vec4, so the field travels in one of those");
        assertTrue(bridge.contains("LightFields.layerFor("),
                "the lamp's field layer has to be packed into a channel of the per-light uniforms");
        String shader = stripLineComments(Files.readString(Path.of("patch/source/slo_light.glsl")));
        assertTrue(shader.contains("if (flood && decay > 0.0)"),
                "the shader's field gate is the layer arriving in the beam knee: zero means the pack draws the "
                        + "ray model, non-zero means the lamp is on the vanilla light model");
        assertFalse(shader.contains("uniform vec4 sloFieldOrigin"),
                "and the shader must not still declare the uniform that never arrived");
    }

    /** The value of one integer {@code #define} in the shader source. */
    private static int define(String source,String name) {
        var matcher=java.util.regex.Pattern.compile("(?m)^#define\\s+"+name+"\\s+(\\d+)\\s*$").matcher(source);
        assertTrue(matcher.find(),"the shader no longer defines "+name);
        return Integer.parseInt(matcher.group(1));
    }

    /**
     * The vanilla light field is a contract whose two halves are written in two languages, and the half that
     * publishes a slot is the one nobody was watching.
     *
     * <p>0.1.331 added the "an empty field is not published" guard to {@code LightFields.slot} and deleted the
     * assignment that writes the layer, in the same commit. The shader kept testing for a field, the texture kept
     * being built, the patch self-check kept passing -- it only ever reads the GLSL -- and the only visible
     * symptom was the flood lamp falling back to its flat look, which two releases were then spent trying to
     * tune. Build, test and deploy were all green the whole time.
     *
     * <p>So the two halves are compared here. The write is looked for in the method body rather than anywhere in
     * the file, because a line that exists somewhere is not a line that runs.
     */
    @Test void theFieldSlotIsPublishedAndTheShaderReadsIt() throws IOException {
        String lightFields=stripLineComments(Files.readString(Path.of(
                "src/main/java/dev/sablelightoverhaul/client/LightFields.java")));
        var slot=java.util.regex.Pattern.compile(
                "(?s)static void slot\\s*\\([^)]*\\)\\s*\\{(.*?)\\n    \\}").matcher(lightFields);
        assertTrue(slot.find(),"LightFields.slot is gone, so no lamp can be given a field");
        String body=slot.group(1);
        assertTrue(java.util.regex.Pattern.compile("out\\[base \\+ 3\\]\\s*=").matcher(body).find(),
                "slot() must write the slot's fourth component: the shader reads it as the layer, and a slot "
                        + "left at zero is a lamp on the vanilla model that silently draws the ray fallback");
        assertTrue(java.util.regex.Pattern.compile("field\\.layer\\s*>=\\s*0").matcher(body).find(),
                "the layer is assigned one tick after the slot is first filled, so an unassigned layer has to "
                        + "keep w at zero rather than publish a layer the texture has not been written at");
        assertTrue(body.contains("field.useful"),
                "a field that never spread must not be published: a non-zero w for an all-zero field makes the "
                        + "lamp vanish instead of falling back to the ray model");
        assertTrue(java.util.regex.Pattern.compile("out\\[base\\]\\s*=").matcher(body).find(),
                "the origin has to be written in slot() too, not left over from whichever lamp held this slot "
                        + "last tick: a stale origin with a fresh layer samples the wrong part of the texture");

        // The other half: the handshake. The layer is what tells the shader which model to draw, and it reaches
        // the pack inside a per-light uniform rather than in a uniform of its own -- see theFieldTravelsInAChannel
        // ThatArrives for why.
        String shader=stripLineComments(Files.readString(Path.of("patch/source/slo_light.glsl")));
        assertTrue(shader.contains("if (flood && decay > 0.0)"),
                "the shader must treat a zero layer as 'no field' and a non-zero one as a layer number");
        String bridge=Files.readString(Path.of(
                "src/main/java/dev/sablelightoverhaul/client/IrisMuzzleBridge.java"));
        assertTrue(bridge.contains("LightFields.layerFor("),
                "the layer the shader reads has to be the one the bridge packs, from the field the lamp owns");
    }

    /** The source without its {@code //} comments, so a claim in prose cannot satisfy a contract check. */
    private static String stripLineComments(String source) {
        return java.util.regex.Pattern.compile("//[^\\n]*").matcher(source).replaceAll("");
    }

    /**
     * No text file this mod compiles or ships may start with a byte-order mark.
     *
     * <p>A BOM is invisible in every editor and in the diff, and it is not harmless: javac counts it as a
     * character, so it shifts the reported line of a genuine error and, in a file whose first token is a
     * modifier, reports a syntax error that does not exist. A whole debugging session can go into a line that
     * is perfectly valid, which is exactly what happened here. The patch self-check already refuses a BOM in
     * the GLSL it writes; this is the same rule for everything else.
     *
     * <p>Tools that write files as UTF-8 with a BOM are the usual cause -- PowerShell's
     * {@code [System.IO.File]::WriteAllText} and {@code Set-Content} both do unless told otherwise.
     */
    @Test void noSourceOrResourceFileStartsWithABom() throws IOException {
        // src is what the compiler and the jar read; patch is what the shader pack is built from. The
        // research notes are deliberately outside this: they are evidence written by tools, not build inputs.
        var roots = java.util.List.of(Path.of("src"), Path.of("patch"));
        var checked = new java.util.ArrayList<String>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String name = file.toString().replace('\\', '/');
                    if (!name.matches(".*\\.(java|glsl|fsh|vsh|json|toml|ps1|properties)$")) continue;
                    if (Files.size(file) < 3) continue;
                    checked.add(name);
                    byte[] head = new byte[3];
                    try (var in = Files.newInputStream(file)) {
                        if (in.read(head) < 3) continue;
                    }
                    assertFalse(head[0] == (byte) 0xEF && head[1] == (byte) 0xBB && head[2] == (byte) 0xBF,
                            file + " starts with a UTF-8 BOM: it shifts every reported line number and can make "
                                    + "javac reject a perfectly legal statement");
                }
            }
        }
        assertTrue(checked.size() > 100, "the BOM scan found almost nothing to check, so it is not looking "
                + "where the sources are: " + checked.size());
    }

        @Test void nativeSmokeColoursAreUntouched() throws IOException {
        // What this mod is allowed to add to CBC's own smoke is an opacity and nothing else. The colours have
        // to stay CBC's: its ramp is what makes the smoke read as smoke, and a mod that tints it is drawing
        // its own smoke with CBC's masks rather than CBC's smoke. The alpha is allowed, but only where a child
        // is born, because that is the one moment the plume's opacity is meant to apply -- and since the key
        // behind it defaults to 1.0, a cannon nobody has tuned draws exactly what CBC drew.
        var smoke=new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main/dev/sablelightoverhaul/mixin/CannonSmokeMixin.class"))).accept(smoke,0);
        for(var method:smoke.methods) {
            for(var insn:method.instructions) {
                if(insn instanceof FieldInsnNode field) {
                    assertFalse(java.util.Set.of("rCol","gCol","bCol").contains(field.name),
                            "CBC's smoke colours must not be read or written");
                    // The write lives in a method that injects into the constructor, so it is not named
                    // "<init>" itself; what matters is that no tick ever touches it. A puff's opacity is
                    // decided once, at birth, and never animated.
                    // The opacity is spent at birth (remembering it) and at the end of each tick (applying it,
                    // after CBC's own tick has written the field and would otherwise overwrite ours).
                    if("alpha".equals(field.name) && field.getOpcode()==org.objectweb.asm.Opcodes.PUTFIELD)
                        assertTrue(java.util.Set.of("<init>","slo$alpha").contains(method.name),
                                "the opacity belongs to the birth and the end of the tick, nowhere else");
                }
            }
            if(method.visibleAnnotations!=null) for(var annotation:method.visibleAnnotations) {
                if(annotation.values!=null && annotation.values.toString().contains("getLightColor")) {
                    assertEquals("slo$smokeMaterial",method.name,"Only the muzzle material metadata wrapper may intercept cooling light");
                    assertTrue(java.util.Arrays.stream(method.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode call
                        && call.owner.equals("dev/sablelightoverhaul/client/MuzzleParticleLayer") && call.name.equals("markLight")));
                    assertTrue(java.util.Arrays.stream(method.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode call
                        && call.name.equals("getReturnValue")),"Keep CBC's computed cooling light instead of substituting fullbright");
                }
            }
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

    /** A class of the game itself, read as the resource the test runtime already has on its classpath. */
    private ClassNode fromClasspath(String binaryName) throws IOException {
        try (var input = TargetBytecodeTest.class.getClassLoader()
                .getResourceAsStream(binaryName.replace('.', '/') + ".class")) {
            assertNotNull(input, "not on the test classpath: " + binaryName);
            var node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }

    /**
     * One string out of an annotation element.
     *
     * <p>Mixin declares {@code method} and {@code at}/{@code value} as arrays, so a single value written
     * in the source still arrives from the class file as a one-entry list.
     */
    private static String firstOf(Object value) {
        if (value instanceof java.util.List<?> list) return list.isEmpty() ? null : String.valueOf(list.get(0));
        return value == null ? null : String.valueOf(value);
    }

    /** {@code at} is an array of annotations, so the one written in the source arrives as a one-entry list. */
    private static Object firstAnnotation(Object value) {
        if (value instanceof java.util.List<?> list) return list.isEmpty() ? null : list.get(0);
        return value;
    }

    /**
     * The particle depth sort hooks into vanilla code by name and descriptor, and a mixin whose target
     * does not exist is not a compile error -- it is a crash while the game is starting up, or worse, a
     * silently unapplied injection. So every name it relies on is checked here against the real class,
     * read out of the game's own bytecode: the injected method, the annotation that says where in it,
     * the shadowed field, the accessor's two fields and the interface method the hook calls.
     */
    @Test void particleDepthSortTargetsExistInTheRealParticleClasses() throws IOException {
        var engine = fromClasspath("net.minecraft.client.particle.ParticleEngine");
        var particle = fromClasspath("net.minecraft.client.particle.Particle");
        var renderType = fromClasspath("net.minecraft.client.particle.ParticleRenderType");

        var mixin = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(
                "build/classes/java/main/dev/sablelightoverhaul/mixin/ParticleDepthSortMixin.class"))).accept(mixin, 0);

        String target = null;
        String at = null;
        Integer require = null;
        for (var method : mixin.methods) {
            if (method.visibleAnnotations == null) continue;
            for (var annotation : method.visibleAnnotations) {
                if (!"Lorg/spongepowered/asm/mixin/injection/Inject;".equals(annotation.desc)) continue;
                for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                    String key = (String) annotation.values.get(i);
                    Object value = annotation.values.get(i + 1);
                    if (key.equals("method")) target = firstOf(value);
                    if (key.equals("require")) require = (Integer) value;
                    if (key.equals("at") && firstAnnotation(value) instanceof org.objectweb.asm.tree.AnnotationNode nested) {
                        int valueIndex = nested.values.indexOf("value");
                        if (valueIndex >= 0) at = firstOf(nested.values.get(valueIndex + 1));
                    }
                }
            }
        }
        assertNotNull(target, "the mixin must name the method it injects into");
        assertEquals("HEAD", at, "the order has to be fixed before the layer is walked");
        assertEquals(1, require, "a missing target must fail loudly, not silently");
        String wanted = target;
        // Mixin takes "name(descriptor)return" or a bare name; the class file has them apart.
        assertTrue(wanted.contains("("), "name the target by descriptor, so a rename cannot pass unnoticed: " + wanted);
        String targetName = wanted.substring(0, wanted.indexOf('('));
        String targetDescriptor = wanted.substring(wanted.indexOf('('));
        assertTrue(engine.methods.stream()
                        .anyMatch(m -> m.name.equals(targetName) && m.desc.equals(targetDescriptor)),
                "ParticleEngine has no such method: " + wanted + " -- it has "
                        + engine.methods.stream().filter(m -> m.name.equals(targetName)).map(m -> m.desc).toList());
        assertTrue(engine.fields.stream().anyMatch(f ->
                        f.name.equals("particles") && f.desc.equals("Ljava/util/Map;")),
                "ParticleEngine no longer holds its layers in a Map");
        for (String axis : new String[]{"x", "y", "z"}) {
            assertTrue(particle.fields.stream().anyMatch(f -> f.name.equals(axis) && f.desc.equals("D")),
                    "Particle has no double field " + axis);
        }
        assertTrue(renderType.methods.stream().anyMatch(m -> m.name.equals("isTranslucent") && m.desc.equals("()Z")),
                "ParticleRenderType no longer answers whether a layer is translucent");
        var accessor = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(
                "build/classes/java/main/dev/sablelightoverhaul/mixin/ParticlePositionAccessor.class"))).accept(accessor, 0);
        for (var method : accessor.methods) {
            if (method.visibleAnnotations == null) continue;
            for (var annotation : method.visibleAnnotations) {
                if (!"Lorg/spongepowered/asm/mixin/gen/Accessor;".equals(annotation.desc)) continue;
                String field = (String) annotation.values.get(1);
                assertTrue(particle.fields.stream().anyMatch(f -> f.name.equals(field) && f.desc.equals("D")),
                        "the accessor reads a field Particle does not have: " + field);
            }
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
