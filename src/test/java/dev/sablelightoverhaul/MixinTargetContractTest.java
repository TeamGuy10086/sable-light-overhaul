package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Every {@code @Inject}/{@code @WrapMethod} in this mod has to name a method that is actually there.
 *
 * <p>The failure this exists for is expensive and completely silent: a mixin that names a method which does
 * not exist does not compile-fail, does not warn, and does not degrade -- with {@code required=true} and
 * {@code defaultRequire: 1} it throws while the game is starting, so the cost is a launch (minutes, on this
 * machine) plus a log read, and the fix is a one-character typo. This came up twice in one day: once for a
 * descriptor that no longer matched after a signature change, and once while wiring the light field's
 * invalidation, where the whole point was to stop burning launches on verifiable mistakes.
 *
 * <p>It is a <em>static</em> check: the source is scanned for the {@code method = "..."} strings and each one
 * is resolved against the real class files. A method reference is either a bare name or a
 * {@code name(descriptor)} pair, and both are checked against the target class and its superclasses, because
 * a mixin may legitimately inject into an inherited method ({@code @Mixin(ClientLevel.class)} into
 * {@code Level.setBlock} is the obvious one).
 *
 * <p><b>What it cannot check.</b> A target class that is not on disk -- an optional dependency that is not in
 * {@code libs/} -- is skipped rather than failed, because "this jar is absent" is not evidence that the name
 * is wrong. That is also why the test asserts that it checked something: a run where every target was skipped
 * proves nothing, and calling that a pass is how a guardrail quietly stops guarding.
 */
class MixinTargetContractTest {
    private static final Path MIXINS = Path.of("src/main/java/dev/sablelightoverhaul/mixin");
    private static final Path LIBS = Path.of("libs");

    /** Every jar that could define a mixin target, in the order they are searched. */
    private static final List<ZipFile> JARS = new ArrayList<>();

    /** {@code method = "..."} or {@code method="..."}, with or without a descriptor. */
    private static final Pattern METHOD = Pattern.compile("method\\s*=\\s*\"([^\"]+)\"");
    /** {@code @Mixin(targets="a.b.C")}: the class is named outright, so it needs no import. */
    private static final Pattern TARGETS = Pattern.compile("targets\\s*=\\s*\"([^\"]+)\"");
    /** {@code @Mixin(C.class)}: the class is named locally and resolved through the file's imports. */
    private static final Pattern MIXIN_CLASS = Pattern.compile("@Mixin\\(\\s*(?:value\\s*=\\s*)?([A-Za-z0-9_$.]+)\\.class");
    private static final Pattern IMPORT = Pattern.compile("import\\s+([\\w.]+)\\.([A-Za-z0-9_$]+)\\s*;");

    @Test void everyInjectionNamesAMethodThatExists() throws Exception {
        Path minecraft = minecraftJar();
        assumeTrue(minecraft != null, "no decompiled Minecraft in the neoform cache; run a build first");
        open(minecraft);
        Path libs = LIBS;
        if (Files.isDirectory(libs)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(libs, "*.jar")) {
                for (Path jar : stream) open(jar);
            }
            Path lamb = libs.resolve("lamb-deps");
            if (Files.isDirectory(lamb)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(lamb, "*.jar")) {
                    for (Path jar : stream) open(jar);
                }
            }
        }

        List<String> failures = new ArrayList<>();
        int checked = 0;
        int skipped = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(MIXINS, "*.java")) {
            for (Path file : stream) {
                String source = Files.readString(file);
                String target = targetClass(source);
                if (target == null) continue;
                if (bytesOf(target.replace('.', '/')) == null) {
                    skipped++;
                    continue;
                }
                Matcher methods = METHOD.matcher(source);
                while (methods.find()) {
                    String reference = methods.group(1);
                    int open = reference.indexOf('(');
                    String name = open < 0 ? reference : reference.substring(0, open);
                    String descriptor = open < 0 ? null : reference.substring(open);
                    checked++;
                    if (!hasMethod(target.replace('.', '/'), name, descriptor)) {
                        failures.add(file.getFileName() + " -> " + target + "." + reference);
                    }
                }
            }
        }
        assertTrue(checked > 0, "the scan resolved no method references at all, so it verified nothing");
        assertTrue(failures.isEmpty(),
                "mixin target method(s) not found; the game would fail while starting. Checked " + checked
                        + " reference(s), skipped " + skipped + " target class(es) whose jar is absent:\n  "
                        + String.join("\n  ", failures));
    }

    /** The class this mixin injects into: named outright by {@code targets}, or resolved through an import. */
    private static String targetClass(String source) {
        Matcher direct = TARGETS.matcher(source);
        if (direct.find()) return direct.group(1);
        Matcher local = MIXIN_CLASS.matcher(source);
        if (!local.find()) return null;
        String simple = local.group(1);
        Matcher imports = IMPORT.matcher(source);
        while (imports.find()) {
            if (imports.group(2).equals(simple)) return imports.group(1) + "." + simple;
        }
        // A same-package or nested target: not resolvable from the text, and not worth guessing at.
        return null;
    }

    private static boolean hasMethod(String internalName, String name, String descriptor) throws IOException {
        Set<String> seen = new HashSet<>();
        List<String> pending = new ArrayList<>(List.of(internalName));
        while (!pending.isEmpty()) {
            String current = pending.remove(pending.size() - 1);
            if (!seen.add(current)) continue;
            ClassNode node = nodeOf(current);
            if (node == null) continue;
            for (MethodNode method : node.methods) {
                if (method.name.equals(name) && (descriptor == null || method.desc.equals(descriptor))) return true;
            }
            if (node.superName != null) pending.add(node.superName);
            if (node.interfaces != null) {
                for (String implemented : node.interfaces) pending.add(implemented);
            }
        }
        return false;
    }

    private static ClassNode nodeOf(String internalName) throws IOException {
        byte[] bytes = bytesOf(internalName);
        if (bytes == null) return null;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] bytesOf(String internalName) throws IOException {
        for (ZipFile jar : JARS) {
            ZipEntry entry = jar.getEntry(internalName + ".class");
            if (entry == null) continue;
            try (var in = jar.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
        return null;
    }

    private static void open(Path jar) {
        try {
            JARS.add(new ZipFile(jar.toFile()));
        } catch (IOException | RuntimeException ignored) {
            // A jar that cannot be read cannot answer a question about its contents; the target class is
            // then simply absent, which the caller already treats as "skipped, not failed".
        }
    }

    /** The newest decompiled Minecraft the neoform runtime has produced, or null before the first build. */
    private static Path minecraftJar() throws IOException {
        Path cache = Path.of(System.getProperty("user.home"), ".gradle", "caches", "neoformruntime",
                "intermediate_results");
        if (!Files.isDirectory(cache)) return null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cache, "compiledWithNeoForge*_output.jar")) {
            List<Path> jars = new ArrayList<>();
            for (Path jar : stream) jars.add(jar);
            jars.sort(Comparator.comparingLong(MixinTargetContractTest::modified).reversed());
            return jars.isEmpty() ? null : jars.get(0);
        }
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return 0L;
        }
    }

    static {
        // Zip handles are process-lifetime by design: the test JVM exits after the suite, and reopening a
        // 23 MB jar once per lookup would be the slow part of the run.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (ZipFile jar : JARS) {
                try {
                    jar.close();
                } catch (IOException ignored) {
                    // Shutting down; a leaked handle is not worth reporting.
                }
            }
        }));
    }
}
